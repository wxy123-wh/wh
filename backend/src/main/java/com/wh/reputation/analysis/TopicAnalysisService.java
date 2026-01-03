package com.wh.reputation.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.common.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TopicAnalysisService {
    private static final int DEFAULT_TOPIC_COUNT = 3;
    private static final int TOP_WORDS_PER_TOPIC = 10;
    private static final int MAX_CANDIDATE_TERMS = 200;
    private static final int MAX_EVIDENCE_REVIEWS = 20;

    private final JdbcTemplate jdbcTemplate;
    private final TokenizationService tokenizationService;
    private final ObjectMapper objectMapper;

    public TopicAnalysisService(JdbcTemplate jdbcTemplate, TokenizationService tokenizationService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenizationService = tokenizationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TopicsResponseDto topics(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("productId is required");
        }

        TopicsResponseDto cached = loadLatest(productId, start, end);
        if (cached != null && cached.topicCount() > 0) {
            return cached;
        }

        return recompute(productId, start, end);
    }

    @Transactional
    public TopicsResponseDto recompute(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("productId is required");
        }

        List<ReviewRow> rows = loadReviewRows(productId, start, end);
        if (rows.isEmpty()) {
            return new TopicsResponseDto(0, List.of());
        }

        List<Doc> docs = new ArrayList<>(rows.size());
        Map<String, Integer> freq = new HashMap<>();
        for (ReviewRow row : rows) {
            List<String> tokens = parseTokensOrTokenize(row.tokensJson(), row.contentClean());
            if (tokens.isEmpty()) {
                continue;
            }
            for (String t : tokens) {
                freq.merge(t, 1, Integer::sum);
            }
            docs.add(new Doc(row.id(), new HashSet<>(tokens)));
        }

        if (docs.isEmpty() || freq.isEmpty()) {
            return new TopicsResponseDto(0, List.of());
        }

        List<Map.Entry<String, Integer>> candidates = freq.entrySet().stream()
                .sorted((a, b) -> {
                    int c1 = Integer.compare(b.getValue(), a.getValue());
                    if (c1 != 0) {
                        return c1;
                    }
                    int c2 = Integer.compare(b.getKey().length(), a.getKey().length());
                    if (c2 != 0) {
                        return c2;
                    }
                    return a.getKey().compareTo(b.getKey());
                })
                .limit(MAX_CANDIDATE_TERMS)
                .toList();

        int topicCount = Math.min(DEFAULT_TOPIC_COUNT, candidates.size());
        if (topicCount <= 0) {
            return new TopicsResponseDto(0, List.of());
        }

        List<List<String>> buckets = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            buckets.add(new ArrayList<>());
        }

        int totalCandidateFreq = 0;
        int[] topicFreq = new int[topicCount];
        int idx = 0;
        for (Map.Entry<String, Integer> entry : candidates) {
            int bucket = idx % topicCount;
            buckets.get(bucket).add(entry.getKey());
            topicFreq[bucket] += entry.getValue();
            totalCandidateFreq += entry.getValue();
            idx++;
        }

        List<TopicItemDto> items = new ArrayList<>(topicCount);
        for (int i = 0; i < topicCount; i++) {
            List<String> words = new ArrayList<>(buckets.get(i));
            words.sort((a, b) -> {
                int c1 = Integer.compare(freq.getOrDefault(b, 0), freq.getOrDefault(a, 0));
                if (c1 != 0) {
                    return c1;
                }
                int c2 = Integer.compare(b.length(), a.length());
                if (c2 != 0) {
                    return c2;
                }
                return a.compareTo(b);
            });

            List<String> topWords = words.subList(0, Math.min(TOP_WORDS_PER_TOPIC, words.size()));
            double weight = totalCandidateFreq <= 0 ? 0.0 : (double) topicFreq[i] / totalCandidateFreq;
            List<Long> evidenceReviewIds = pickEvidenceReviewIds(docs, topWords);
            items.add(new TopicItemDto(i, topWords, weight, evidenceReviewIds));
        }

        String topicsJson = toJson(items);
        jdbcTemplate.update("""
                        insert into topic_result (product_id, start_date, end_date, topic_count, topics_json, created_at)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                productId,
                toSqlDate(start),
                toSqlDate(end),
                topicCount,
                topicsJson,
                Timestamp.valueOf(LocalDateTime.now())
        );

        return new TopicsResponseDto(topicCount, items);
    }

    private TopicsResponseDto loadLatest(Long productId, LocalDate start, LocalDate end) {
        String sql = """
                select tr.topic_count as topicCount,
                       tr.topics_json as topicsJson
                from topic_result tr
                where tr.product_id = ?
                  and tr.start_date <=> ?
                  and tr.end_date <=> ?
                order by tr.created_at desc
                limit 1
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, productId, toSqlDate(start), toSqlDate(end));
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = rows.get(0);
        Integer topicCount = (Integer) row.get("topicCount");
        String topicsJson = (String) row.get("topicsJson");
        if (topicCount == null || topicCount <= 0 || topicsJson == null || topicsJson.isBlank()) {
            return null;
        }

        List<TopicItemDto> items;
        try {
            items = objectMapper.readValue(topicsJson, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
        return new TopicsResponseDto(topicCount, items == null ? List.of() : items);
    }

    private List<ReviewRow> loadReviewRows(Long productId, LocalDate start, LocalDate end) {
        LocalDateTime startTime = start == null ? null : start.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select r.id as id,
                       r.content_clean as contentClean,
                       r.tokens_json as tokensJson
                from review r
                where r.product_id = ?
                """);
        params.add(productId);

        if (startTime != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endExclusive != null) {
            sql.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }
        sql.append(" order by r.id asc");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new ReviewRow(
                rs.getLong("id"),
                rs.getString("contentClean"),
                rs.getString("tokensJson")
        ), params.toArray());
    }

    private List<String> parseTokensOrTokenize(String tokensJson, String contentClean) {
        List<String> tokens = parseJsonArray(tokensJson);
        if (!tokens.isEmpty()) {
            return tokens;
        }
        return tokenizationService.tokenize(contentClean);
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> items = objectMapper.readValue(json, new TypeReference<>() {});
            return items == null ? List.of() : items;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Long> pickEvidenceReviewIds(List<Doc> docs, List<String> topWords) {
        if (docs.isEmpty()) {
            return List.of();
        }
        if (topWords == null || topWords.isEmpty()) {
            return List.of(docs.get(0).reviewId());
        }

        Set<String> target = new HashSet<>(topWords);
        List<Long> evidence = new ArrayList<>();
        for (Doc doc : docs) {
            if (!Collections.disjoint(doc.tokens(), target)) {
                evidence.add(doc.reviewId());
                if (evidence.size() >= MAX_EVIDENCE_REVIEWS) {
                    break;
                }
            }
        }
        if (evidence.isEmpty()) {
            return List.of(docs.get(0).reviewId());
        }
        return evidence;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize json", e);
        }
    }

    private static Date toSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private record ReviewRow(Long id, String contentClean, String tokensJson) {}

    private record Doc(Long reviewId, Set<String> tokens) {}
}

