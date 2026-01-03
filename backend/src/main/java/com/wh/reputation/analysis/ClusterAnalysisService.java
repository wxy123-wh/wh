package com.wh.reputation.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.common.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ClusterAnalysisService {
    private static final int MIN_CLUSTER_SIZE = 5;
    private static final int MAX_K = 8;
    private static final int MAX_FEATURES = 200;
    private static final int TOP_TERMS = 10;
    private static final int REPRESENTATIVE_REVIEWS = 5;
    private static final int MAX_ITER = 20;
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final TokenizationService tokenizationService;
    private final ObjectMapper objectMapper;

    public ClusterAnalysisService(JdbcTemplate jdbcTemplate, TokenizationService tokenizationService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenizationService = tokenizationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ClustersResponseDto clusters(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("productId is required");
        }

        ClustersResponseDto cached = loadLatest(productId, start, end);
        if (cached != null && cached.items() != null && !cached.items().isEmpty()) {
            return cached;
        }

        return recompute(productId, start, end);
    }

    @Transactional
    public ClustersResponseDto recompute(Long productId, LocalDate start, LocalDate end) {
        if (productId == null) {
            throw new BadRequestException("productId is required");
        }

        List<ReviewRow> rows = loadReviewRows(productId, start, end);
        if (rows.isEmpty()) {
            return new ClustersResponseDto(List.of());
        }

        List<Doc> docs = new ArrayList<>(rows.size());
        for (ReviewRow row : rows) {
            List<String> tokens = parseTokensOrTokenize(row.tokensJson(), row.contentClean());
            docs.add(new Doc(row.id(), tokens, isNeg(row.sentimentLabel())));
        }

        int k = chooseK(docs.size());
        List<ClusterResult> results = cluster(docs, k);
        if (results.isEmpty()) {
            return new ClustersResponseDto(List.of());
        }

        LocalDateTime createdAt = LocalDateTime.now();
        Timestamp createdAtTs = Timestamp.valueOf(createdAt);
        int finalK = results.size();

        List<ClusterListItemDto> responseItems = new ArrayList<>(results.size());
        for (ClusterResult result : results) {
            String topTermsJson = toJson(result.topTerms());
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement("""
                                insert into `cluster` (product_id, start_date, end_date, k, top_terms_json, size, neg_rate, created_at)
                                values (?, ?, ?, ?, ?, ?, ?, ?)
                                """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, productId);
                ps.setObject(2, toSqlDate(start));
                ps.setObject(3, toSqlDate(end));
                ps.setInt(4, finalK);
                ps.setString(5, topTermsJson);
                ps.setInt(6, result.reviewIds().size());
                ps.setDouble(7, result.negRate());
                ps.setTimestamp(8, createdAtTs);
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("failed to insert cluster");
            }
            long clusterId = key.longValue();

            List<Object[]> mappingArgs = new ArrayList<>(result.reviewIds().size());
            for (Long reviewId : result.reviewIds()) {
                mappingArgs.add(new Object[]{reviewId, clusterId, createdAtTs});
            }
            jdbcTemplate.batchUpdate("""
                            insert into review_cluster (review_id, cluster_id, created_at)
                            values (?, ?, ?)
                            """,
                    mappingArgs
            );

            List<Long> repIds = result.reviewIds().subList(0, Math.min(REPRESENTATIVE_REVIEWS, result.reviewIds().size()));
            responseItems.add(new ClusterListItemDto(clusterId, result.topTerms(), result.reviewIds().size(), result.negRate(), repIds));
        }

        return new ClustersResponseDto(responseItems);
    }

    public ClusterDetailResponseDto clusterDetail(Long id) {
        if (id == null) {
            throw new BadRequestException("id is required");
        }

        String sql = """
                select c.id as id,
                       c.top_terms_json as topTermsJson,
                       c.size as size,
                       c.neg_rate as negRate
                from `cluster` c
                where c.id = ?
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
        if (rows.isEmpty()) {
            throw new NotFoundException("cluster not found");
        }
        Map<String, Object> row = rows.get(0);
        String topTermsJson = (String) row.get("topTermsJson");
        List<String> topTerms = parseJsonArray(topTermsJson);
        int size = ((Number) row.get("size")).intValue();
        double negRate = ((Number) row.get("negRate")).doubleValue();

        List<ClusterRepresentativeReviewDto> reps = loadRepresentativeReviews(id);
        return new ClusterDetailResponseDto(id, topTerms, size, negRate, reps);
    }

    private List<ClusterRepresentativeReviewDto> loadRepresentativeReviews(Long clusterId) {
        String sql = """
                select r.id as id,
                       coalesce(r.review_time, r.created_at) as reviewTime,
                       r.content_clean as contentClean,
                       r.overall_sentiment_label as overallSentiment
                from review_cluster rc
                join review r on r.id = rc.review_id
                where rc.cluster_id = ?
                order by rc.id asc
                limit 50
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("reviewTime");
            String time = ts == null ? null : DATETIME_FORMAT.format(ts.toLocalDateTime());
            return new ClusterRepresentativeReviewDto(
                    rs.getLong("id"),
                    time,
                    rs.getString("contentClean"),
                    rs.getString("overallSentiment")
            );
        }, clusterId);
    }

    private ClustersResponseDto loadLatest(Long productId, LocalDate start, LocalDate end) {
        String maxSql = """
                select max(c.created_at) as createdAt
                from `cluster` c
                where c.product_id = ?
                  and c.start_date <=> ?
                  and c.end_date <=> ?
                """;

        List<Map<String, Object>> maxRows = jdbcTemplate.queryForList(maxSql, productId, toSqlDate(start), toSqlDate(end));
        if (maxRows.isEmpty() || maxRows.get(0).get("createdAt") == null) {
            return null;
        }
        Timestamp createdAt = (Timestamp) maxRows.get(0).get("createdAt");

        String listSql = """
                select c.id as id,
                       c.top_terms_json as topTermsJson,
                       c.size as size,
                       c.neg_rate as negRate
                from `cluster` c
                where c.product_id = ?
                  and c.start_date <=> ?
                  and c.end_date <=> ?
                  and c.created_at = ?
                order by c.id asc
                """;

        List<ClusterRow> clusters = jdbcTemplate.query(listSql, (rs, rowNum) -> new ClusterRow(
                rs.getLong("id"),
                rs.getString("topTermsJson"),
                rs.getInt("size"),
                rs.getDouble("negRate")
        ), productId, toSqlDate(start), toSqlDate(end), createdAt);

        if (clusters.isEmpty()) {
            return null;
        }

        List<Long> clusterIds = clusters.stream().map(ClusterRow::id).toList();
        Map<Long, List<Long>> representativeIds = loadRepresentativeIds(clusterIds);

        List<ClusterListItemDto> items = clusters.stream()
                .map(c -> new ClusterListItemDto(
                        c.id(),
                        parseJsonArray(c.topTermsJson()),
                        c.size(),
                        c.negRate(),
                        representativeIds.getOrDefault(c.id(), List.of())
                ))
                .toList();

        return new ClustersResponseDto(items);
    }

    private Map<Long, List<Long>> loadRepresentativeIds(List<Long> clusterIds) {
        if (clusterIds == null || clusterIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(",", Collections.nCopies(clusterIds.size(), "?"));
        String sql = """
                select t.cluster_id as clusterId,
                       t.review_id as reviewId
                from (
                    select rc.cluster_id,
                           rc.review_id,
                           row_number() over (partition by rc.cluster_id order by rc.id asc) as rn
                    from review_cluster rc
                    where rc.cluster_id in (%s)
                ) t
                where t.rn <= %d
                order by t.cluster_id asc, t.rn asc
                """.formatted(placeholders, REPRESENTATIVE_REVIEWS);

        Map<Long, List<Long>> map = new HashMap<>();
        jdbcTemplate.query(sql, (rs) -> {
            long clusterId = rs.getLong("clusterId");
            long reviewId = rs.getLong("reviewId");
            map.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(reviewId);
        }, clusterIds.toArray());
        return map;
    }

    private List<ReviewRow> loadReviewRows(Long productId, LocalDate start, LocalDate end) {
        LocalDateTime startTime = start == null ? null : start.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select r.id as id,
                       r.content_clean as contentClean,
                       r.tokens_json as tokensJson,
                       r.overall_sentiment_label as sentimentLabel
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
                rs.getString("tokensJson"),
                rs.getString("sentimentLabel")
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize json", e);
        }
    }

    private static int chooseK(int docCount) {
        if (docCount <= 0) {
            return 0;
        }
        if (docCount < MIN_CLUSTER_SIZE) {
            return 1;
        }
        int k = Math.max(1, docCount / MIN_CLUSTER_SIZE);
        return Math.min(MAX_K, k);
    }

    private List<ClusterResult> cluster(List<Doc> docs, int k) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        if (k <= 1) {
            return List.of(buildClusterResult(docs, docs));
        }

        Vectorization vectorization = vectorize(docs);
        if (vectorization.vectors().isEmpty()) {
            return List.of(buildClusterResult(docs, docs));
        }

        if (vectorization.nonZeroCount() == 0) {
            return roundRobinClusters(docs, k);
        }

        int effectiveK = Math.min(k, vectorization.nonZeroCount());
        int[] assignments = kmeans(vectorization.vectors(), effectiveK);

        Map<Integer, List<Doc>> grouped = new HashMap<>();
        for (int i = 0; i < assignments.length; i++) {
            grouped.computeIfAbsent(assignments[i], key -> new ArrayList<>()).add(docs.get(i));
        }

        List<List<Doc>> clusters = grouped.values().stream()
                .sorted(Comparator.comparingInt((List<Doc> list) -> list.size()).reversed())
                .toList();

        List<List<Doc>> merged = mergeSmallClusters(clusters, vectorization.vectors(), effectiveK);
        List<ClusterResult> results = new ArrayList<>(merged.size());
        for (List<Doc> clusterDocs : merged) {
            results.add(buildClusterResult(docs, clusterDocs));
        }
        results.sort(Comparator.comparingInt((ClusterResult r) -> r.reviewIds().size()).reversed());
        return results;
    }

    private List<ClusterResult> roundRobinClusters(List<Doc> docs, int k) {
        List<List<Doc>> buckets = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            buckets.add(new ArrayList<>());
        }
        List<Doc> sorted = docs.stream()
                .sorted(Comparator.comparingLong(Doc::reviewId))
                .toList();
        int idx = 0;
        for (Doc doc : sorted) {
            buckets.get(idx % k).add(doc);
            idx++;
        }
        List<ClusterResult> results = new ArrayList<>();
        for (List<Doc> bucket : buckets) {
            if (!bucket.isEmpty()) {
                results.add(buildClusterResult(docs, bucket));
            }
        }
        return results;
    }

    private List<List<Doc>> mergeSmallClusters(List<List<Doc>> clusters, List<double[]> vectors, int k) {
        int docCount = vectors.size();
        int minSize = Math.min(MIN_CLUSTER_SIZE, docCount);
        if (clusters.size() <= 1) {
            return clusters;
        }

        List<double[]> centroids = computeCentroids(clusters, vectors);
        List<List<Doc>> mutable = new ArrayList<>(clusters);
        List<double[]> mutableCentroids = new ArrayList<>(centroids);

        while (mutable.size() > 1) {
            int smallestIdx = -1;
            int smallestSize = Integer.MAX_VALUE;
            for (int i = 0; i < mutable.size(); i++) {
                int size = mutable.get(i).size();
                if (size < minSize && size < smallestSize) {
                    smallestSize = size;
                    smallestIdx = i;
                }
            }
            if (smallestIdx < 0) {
                break;
            }

            int targetIdx = nearestCentroidIndex(mutableCentroids, smallestIdx);
            if (targetIdx < 0) {
                break;
            }
            mutable.get(targetIdx).addAll(mutable.get(smallestIdx));

            int removeIdx = smallestIdx;
            int keepIdx = targetIdx;
            if (removeIdx < keepIdx) {
                keepIdx--;
            }
            mutable.remove(removeIdx);
            mutableCentroids.remove(removeIdx);

            mutableCentroids.set(keepIdx, computeCentroid(mutable.get(keepIdx), vectors));
        }

        return mutable;
    }

    private int nearestCentroidIndex(List<double[]> centroids, int fromIdx) {
        double best = Double.POSITIVE_INFINITY;
        int bestIdx = -1;
        for (int i = 0; i < centroids.size(); i++) {
            if (i == fromIdx) {
                continue;
            }
            double d = distSq(centroids.get(fromIdx), centroids.get(i));
            if (d < best) {
                best = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private List<double[]> computeCentroids(List<List<Doc>> clusters, List<double[]> vectors) {
        List<double[]> centroids = new ArrayList<>(clusters.size());
        for (List<Doc> cluster : clusters) {
            centroids.add(computeCentroid(cluster, vectors));
        }
        return centroids;
    }

    private double[] computeCentroid(List<Doc> cluster, List<double[]> vectors) {
        if (cluster == null || cluster.isEmpty()) {
            return new double[0];
        }
        int dim = vectors.get(0).length;
        double[] sum = new double[dim];
        int count = 0;
        for (Doc doc : cluster) {
            int idx = (int) Math.min(Math.max(0, doc.vectorIndex()), vectors.size() - 1);
            double[] v = vectors.get(idx);
            for (int d = 0; d < dim; d++) {
                sum[d] += v[d];
            }
            count++;
        }
        if (count == 0) {
            return sum;
        }
        for (int d = 0; d < dim; d++) {
            sum[d] /= count;
        }
        normalize(sum);
        return sum;
    }

    private int[] kmeans(List<double[]> vectors, int k) {
        int n = vectors.size();
        int dim = vectors.get(0).length;
        double[][] centroids = initCentroids(vectors, k);
        int[] assign = new int[n];
        Arrays.fill(assign, -1);

        for (int iter = 0; iter < MAX_ITER; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int best = nearest(centroids, vectors.get(i));
                if (assign[i] != best) {
                    assign[i] = best;
                    changed = true;
                }
            }
            if (!changed && iter > 0) {
                break;
            }

            double[][] next = new double[k][dim];
            int[] counts = new int[k];
            for (int i = 0; i < n; i++) {
                int c = assign[i];
                counts[c]++;
                double[] v = vectors.get(i);
                for (int d = 0; d < dim; d++) {
                    next[c][d] += v[d];
                }
            }
            for (int c = 0; c < k; c++) {
                if (counts[c] == 0) {
                    next[c] = Arrays.copyOf(vectors.get(c % n), dim);
                    continue;
                }
                for (int d = 0; d < dim; d++) {
                    next[c][d] /= counts[c];
                }
                normalize(next[c]);
            }
            centroids = next;
        }

        return assign;
    }

    private double[][] initCentroids(List<double[]> vectors, int k) {
        int n = vectors.size();
        int dim = vectors.get(0).length;
        double[][] centroids = new double[k][dim];

        int first = 0;
        centroids[0] = Arrays.copyOf(vectors.get(first), dim);

        List<Integer> chosen = new ArrayList<>();
        chosen.add(first);
        for (int c = 1; c < k; c++) {
            double bestDist = -1;
            int bestIdx = 0;
            for (int i = 0; i < n; i++) {
                double d = Double.POSITIVE_INFINITY;
                for (int idx : chosen) {
                    d = Math.min(d, distSq(vectors.get(i), vectors.get(idx)));
                }
                if (d > bestDist) {
                    bestDist = d;
                    bestIdx = i;
                }
            }
            chosen.add(bestIdx);
            centroids[c] = Arrays.copyOf(vectors.get(bestIdx), dim);
        }
        return centroids;
    }

    private int nearest(double[][] centroids, double[] vector) {
        double best = Double.POSITIVE_INFINITY;
        int bestIdx = 0;
        for (int i = 0; i < centroids.length; i++) {
            double d = distSq(centroids[i], vector);
            if (d < best) {
                best = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static double distSq(double[] a, double[] b) {
        int dim = Math.min(a.length, b.length);
        double sum = 0.0;
        for (int i = 0; i < dim; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return sum;
    }

    private static void normalize(double[] v) {
        double norm = 0.0;
        for (double x : v) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        if (norm <= 0) {
            return;
        }
        for (int i = 0; i < v.length; i++) {
            v[i] /= norm;
        }
    }

    private Vectorization vectorize(List<Doc> docs) {
        int n = docs.size();
        Map<String, Integer> df = new HashMap<>();
        for (Doc doc : docs) {
            Set<String> uniq = new HashSet<>(doc.tokens());
            for (String token : uniq) {
                if (token == null || token.isBlank()) {
                    continue;
                }
                df.merge(token, 1, Integer::sum);
            }
        }

        int minDf = 2;
        List<String> vocab = buildVocabulary(df, minDf);
        if (vocab.isEmpty()) {
            vocab = buildVocabulary(df, 1);
        }
        if (vocab.isEmpty()) {
            return new Vectorization(List.of(), 0);
        }

        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < vocab.size(); i++) {
            index.put(vocab.get(i), i);
        }

        double[] idf = new double[vocab.size()];
        for (int i = 0; i < vocab.size(); i++) {
            int d = df.getOrDefault(vocab.get(i), 0);
            idf[i] = Math.log((n + 1.0) / (d + 1.0)) + 1.0;
        }

        List<double[]> vectors = new ArrayList<>(n);
        int nonZero = 0;
        for (int docIdx = 0; docIdx < docs.size(); docIdx++) {
            Doc doc = docs.get(docIdx);
            int dim = vocab.size();
            int[] counts = new int[dim];
            int total = 0;
            for (String token : doc.tokens()) {
                Integer idx = index.get(token);
                if (idx == null) {
                    continue;
                }
                counts[idx]++;
                total++;
            }
            double[] vec = new double[dim];
            if (total > 0) {
                for (int i = 0; i < dim; i++) {
                    if (counts[i] == 0) {
                        continue;
                    }
                    double tf = (double) counts[i] / total;
                    vec[i] = tf * idf[i];
                }
                normalize(vec);
            }
            if (vectorNorm(vec) > 0) {
                nonZero++;
            }
            vectors.add(vec);
            doc.setVectorIndex(docIdx);
        }

        return new Vectorization(vectors, nonZero);
    }

    private static double vectorNorm(double[] vec) {
        double sum = 0.0;
        for (double v : vec) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    private List<String> buildVocabulary(Map<String, Integer> df, int minDf) {
        return df.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .filter(e -> e.getValue() >= minDf)
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
                .limit(MAX_FEATURES)
                .map(Map.Entry::getKey)
                .toList();
    }

    private ClusterResult buildClusterResult(List<Doc> allDocs, List<Doc> clusterDocs) {
        if (clusterDocs == null || clusterDocs.isEmpty()) {
            return new ClusterResult(List.of(), List.of(), 0.0);
        }

        Map<String, Integer> tokenFreq = new HashMap<>();
        int negCount = 0;
        for (Doc doc : clusterDocs) {
            if (doc.isNeg()) {
                negCount++;
            }
            for (String token : doc.tokens()) {
                if (token == null || token.isBlank()) {
                    continue;
                }
                tokenFreq.merge(token, 1, Integer::sum);
            }
        }

        List<String> topTerms = tokenFreq.entrySet().stream()
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
                .limit(TOP_TERMS)
                .map(Map.Entry::getKey)
                .toList();

        List<Long> orderedReviewIds = clusterDocs.stream()
                .sorted(Comparator.comparingLong(Doc::reviewId))
                .map(Doc::reviewId)
                .toList();

        double negRate = clusterDocs.isEmpty() ? 0.0 : (double) negCount / clusterDocs.size();
        return new ClusterResult(orderedReviewIds, topTerms, negRate);
    }

    private static boolean isNeg(String sentimentLabel) {
        return "NEG".equalsIgnoreCase(sentimentLabel);
    }

    private static Date toSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private record ReviewRow(Long id, String contentClean, String tokensJson, String sentimentLabel) {}

    private static class Doc {
        private final Long reviewId;
        private final List<String> tokens;
        private final boolean neg;
        private int vectorIndex;

        private Doc(Long reviewId, List<String> tokens, boolean neg) {
            this.reviewId = reviewId;
            this.tokens = tokens == null ? List.of() : tokens;
            this.neg = neg;
        }

        public Long reviewId() {
            return reviewId;
        }

        public List<String> tokens() {
            return tokens;
        }

        public boolean isNeg() {
            return neg;
        }

        public int vectorIndex() {
            return vectorIndex;
        }

        public void setVectorIndex(int vectorIndex) {
            this.vectorIndex = vectorIndex;
        }
    }

    private record Vectorization(List<double[]> vectors, int nonZeroCount) {}

    private record ClusterRow(Long id, String topTermsJson, int size, double negRate) {}

    private record ClusterResult(List<Long> reviewIds, List<String> topTerms, double negRate) {}
}
