package com.wh.reputation.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.common.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ReviewQueryService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReviewQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ReviewsPageDto list(
            Long productId,
            Long platformId,
            Long aspectId,
            String sentiment,
            String keyword,
            String start,
            String end,
            Integer page,
            Integer pageSize
    ) {
        if (productId == null) {
            throw new BadRequestException("productId is required");
        }
        int safePage = page == null ? 1 : page;
        int safePageSize = pageSize == null ? 20 : pageSize;
        if (safePage < 1) {
            throw new BadRequestException("page must be >= 1");
        }
        if (safePageSize < 1 || safePageSize > 200) {
            throw new BadRequestException("pageSize must be between 1 and 200");
        }

        LocalDate startDate = parseDateOrNull(start);
        LocalDate endDate = parseDateOrNull(end);
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay();

        List<Object> params = new ArrayList<>();
        String where = buildReviewWhereClause(productId, platformId, aspectId, sentiment, keyword, startTime, endExclusive, params);

        long total = jdbcTemplate.queryForObject("select count(*) from review r where " + where, Long.class, params.toArray());
        int offset = (safePage - 1) * safePageSize;

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safePageSize);
        listParams.add(offset);

        String sql = """
                select r.id as id,
                       pf.name as platformName,
                       pr.name as productName,
                       r.rating as rating,
                       r.review_time as reviewTime,
                       r.content_clean as contentClean,
                       r.overall_sentiment_label as overallSentiment,
                       r.overall_sentiment_score as overallScore
                from review r
                join platform pf on pf.id = r.platform_id
                join product pr on pr.id = r.product_id
                where %s
                order by r.id desc
                limit ? offset ?
                """.formatted(where);

        List<ReviewRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new ReviewRow(
                rs.getLong("id"),
                rs.getString("platformName"),
                rs.getString("productName"),
                (Integer) rs.getObject("rating"),
                rs.getTimestamp("reviewTime"),
                rs.getString("contentClean"),
                rs.getString("overallSentiment"),
                rs.getDouble("overallScore")
        ), listParams.toArray());

        List<Long> reviewIds = rows.stream().map(ReviewRow::id).toList();
        Map<Long, List<ReviewListAspectDto>> aspectsByReviewId = loadAspectsForReviews(reviewIds);

        List<ReviewListItemDto> items = rows.stream().map(row -> new ReviewListItemDto(
                row.id(),
                row.platformName(),
                row.productName(),
                row.rating(),
                formatTimestamp(row.reviewTime()),
                row.contentClean(),
                row.overallSentiment(),
                row.overallScore(),
                aspectsByReviewId.getOrDefault(row.id(), List.of())
        )).toList();

        return new ReviewsPageDto(safePage, safePageSize, total, items);
    }

    public ReviewDetailDto detail(Long id) {
        if (id == null) {
            throw new BadRequestException("id is required");
        }

        String sql = """
                select r.id as id,
                       pf.name as platformName,
                       pr.name as productName,
                       r.rating as rating,
                       r.review_time as reviewTime,
                       r.content_raw as contentRaw,
                       r.content_clean as contentClean,
                       r.overall_sentiment_label as overallSentiment,
                       r.overall_sentiment_score as overallScore
                from review r
                join platform pf on pf.id = r.platform_id
                join product pr on pr.id = r.product_id
                where r.id = ?
                """;

        List<ReviewDetailRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new ReviewDetailRow(
                rs.getLong("id"),
                rs.getString("platformName"),
                rs.getString("productName"),
                (Integer) rs.getObject("rating"),
                rs.getTimestamp("reviewTime"),
                rs.getString("contentRaw"),
                rs.getString("contentClean"),
                rs.getString("overallSentiment"),
                rs.getDouble("overallScore")
        ), id);

        if (rows.isEmpty()) {
            throw new NotFoundException("review not found");
        }

        ReviewDetailRow row = rows.get(0);
        List<ReviewAspectResultDto> aspectResults = loadAspectResults(id);
        return new ReviewDetailDto(
                row.id(),
                row.platformName(),
                row.productName(),
                row.rating(),
                formatTimestamp(row.reviewTime()),
                row.contentRaw(),
                row.contentClean(),
                row.overallSentiment(),
                row.overallScore(),
                aspectResults
        );
    }

    private Map<Long, List<ReviewListAspectDto>> loadAspectsForReviews(List<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(reviewIds.size(), "?"));
        String sql = """
                select rar.review_id as reviewId,
                       rar.aspect_id as aspectId,
                       a.name as aspectName,
                       rar.sentiment_label as sentiment,
                       rar.sentiment_score as score
                from review_aspect_result rar
                join aspect a on a.id = rar.aspect_id
                where rar.review_id in (%s)
                order by rar.review_id asc, rar.aspect_id asc
                """.formatted(placeholders);

        Map<Long, List<ReviewListAspectDto>> result = new HashMap<>();
        jdbcTemplate.query(sql, (rs) -> {
            Long reviewId = rs.getLong("reviewId");
            ReviewListAspectDto dto = new ReviewListAspectDto(
                    rs.getLong("aspectId"),
                    rs.getString("aspectName"),
                    rs.getString("sentiment"),
                    rs.getDouble("score")
            );
            result.computeIfAbsent(reviewId, k -> new ArrayList<>()).add(dto);
        }, reviewIds.toArray());
        return result;
    }

    private List<ReviewAspectResultDto> loadAspectResults(Long reviewId) {
        String sql = """
                select rar.aspect_id as aspectId,
                       a.name as aspectName,
                       rar.hit_keywords_json as hitKeywordsJson,
                       rar.sentiment_label as sentiment,
                       rar.sentiment_score as score,
                       rar.confidence as confidence
                from review_aspect_result rar
                join aspect a on a.id = rar.aspect_id
                where rar.review_id = ?
                order by rar.aspect_id asc
                """;

        List<ReviewAspectResultDto> items = new ArrayList<>();
        jdbcTemplate.query(sql, (rs) -> {
            List<String> hitKeywords = parseJsonArray(rs.getString("hitKeywordsJson"));
            items.add(new ReviewAspectResultDto(
                    rs.getLong("aspectId"),
                    rs.getString("aspectName"),
                    hitKeywords,
                    rs.getString("sentiment"),
                    rs.getDouble("score"),
                    rs.getDouble("confidence")
            ));
        }, reviewId);
        return items;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String buildReviewWhereClause(
            Long productId,
            Long platformId,
            Long aspectId,
            String sentiment,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endExclusive,
            List<Object> params
    ) {
        StringBuilder sb = new StringBuilder("r.product_id = ?");
        params.add(productId);

        if (platformId != null) {
            sb.append(" and r.platform_id = ?");
            params.add(platformId);
        }
        if (aspectId != null) {
            sb.append(" and exists (select 1 from review_aspect_result rar where rar.review_id = r.id and rar.aspect_id = ?)");
            params.add(aspectId);
        }
        if (sentiment != null && !sentiment.isBlank()) {
            sb.append(" and r.overall_sentiment_label = ?");
            params.add(sentiment.trim().toUpperCase(Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            sb.append(" and r.content_clean like ?");
            params.add("%" + keyword.trim() + "%");
        }
        if (startTime != null) {
            sb.append(" and coalesce(r.review_time, r.created_at) >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endExclusive != null) {
            sb.append(" and coalesce(r.review_time, r.created_at) < ?");
            params.add(Timestamp.valueOf(endExclusive));
        }
        return sb.toString();
    }

    private static LocalDate parseDateOrNull(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(input.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("invalid date: " + input);
        }
    }

    private static String formatTimestamp(Timestamp ts) {
        if (ts == null) {
            return null;
        }
        return DATETIME_FORMAT.format(ts.toLocalDateTime());
    }

    private record ReviewRow(
            Long id,
            String platformName,
            String productName,
            Integer rating,
            Timestamp reviewTime,
            String contentClean,
            String overallSentiment,
            double overallScore
    ) {}

    private record ReviewDetailRow(
            Long id,
            String platformName,
            String productName,
            Integer rating,
            Timestamp reviewTime,
            String contentRaw,
            String contentClean,
            String overallSentiment,
            double overallScore
    ) {}
}

