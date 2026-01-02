package com.wh.reputation.review;

import com.wh.reputation.analysis.ReviewAnalysisService;
import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.persistence.PlatformEntity;
import com.wh.reputation.persistence.PlatformRepository;
import com.wh.reputation.persistence.ProductEntity;
import com.wh.reputation.persistence.ProductRepository;
import com.wh.reputation.persistence.ReviewEntity;
import com.wh.reputation.persistence.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ReviewImportService {
    private static final List<String> EXPECTED_HEADER = List.of(
            "platform_name",
            "product_name",
            "brand",
            "model",
            "rating",
            "review_time",
            "content",
            "like_count",
            "review_id_raw"
    );
    private static final DateTimeFormatter CSV_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlatformRepository platformRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewAnalysisService reviewAnalysisService;

    public ReviewImportService(
            PlatformRepository platformRepository,
            ProductRepository productRepository,
            ReviewRepository reviewRepository,
            ReviewAnalysisService reviewAnalysisService
    ) {
        this.platformRepository = platformRepository;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.reviewAnalysisService = reviewAnalysisService;
    }

    @Transactional
    public ReviewImportResult importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required");
        }

        int errors = 0;
        List<ValidRow> validRows = new ArrayList<>();

        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BadRequestException("empty csv");
            }
            List<String> header = CsvUtils.parseLine(headerLine).stream()
                    .map(String::trim)
                    .toList();
            if (!stripBom(header).equals(EXPECTED_HEADER)) {
                throw new BadRequestException("invalid csv header");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                List<String> cols;
                try {
                    cols = CsvUtils.parseLine(line);
                } catch (IllegalArgumentException e) {
                    errors++;
                    continue;
                }
                if (cols.size() != EXPECTED_HEADER.size()) {
                    errors++;
                    continue;
                }

                Optional<ValidRow> parsed;
                try {
                    parsed = parseRow(cols);
                } catch (IllegalArgumentException e) {
                    errors++;
                    continue;
                }
                if (parsed.isEmpty()) {
                    errors++;
                    continue;
                }
                validRows.add(parsed.get());
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to read csv", e);
        }

        Set<String> existingHashes = loadExistingHashes(validRows);
        Map<String, PlatformEntity> platformCache = new HashMap<>();
        Map<ProductKey, ProductEntity> productCache = new HashMap<>();

        int inserted = 0;
        int skipped = 0;
        List<Long> insertedReviewIds = new ArrayList<>();
        for (ValidRow row : validRows) {
            if (existingHashes.contains(row.hash())) {
                skipped++;
                continue;
            }

            PlatformEntity platform = platformCache.computeIfAbsent(row.platformName(), name ->
                    platformRepository.findByName(name)
                            .orElseGet(() -> platformRepository.save(new PlatformEntity(name, now())))
            );

            ProductKey productKey = new ProductKey(row.productName(), row.brand(), row.model());
            ProductEntity product = productCache.computeIfAbsent(productKey, key ->
                    productRepository.findExisting(key.name(), key.brand(), key.model())
                            .orElseGet(() -> productRepository.save(new ProductEntity(
                                    key.name(),
                                    key.brand(),
                                    key.model(),
                                    now()
                            )))
            );

            ReviewEntity entity = new ReviewEntity(
                    platform,
                    product,
                    row.reviewIdRaw(),
                    row.rating(),
                    row.contentRaw(),
                    row.contentClean(),
                    row.reviewTime(),
                    row.likeCount(),
                    row.hash(),
                    "NEU",
                    0.0,
                    now()
            );
            ReviewEntity saved = reviewRepository.save(entity);

            existingHashes.add(row.hash());
            inserted++;
            insertedReviewIds.add(saved.getId());
        }

        reviewAnalysisService.analyzeReviewIds(insertedReviewIds);

        return new ReviewImportResult(inserted, skipped, errors);
    }

    private static List<String> stripBom(List<String> header) {
        if (header.isEmpty()) {
            return header;
        }
        String first = header.get(0);
        if (first.startsWith("\uFEFF")) {
            List<String> fixed = new ArrayList<>(header);
            fixed.set(0, first.substring(1));
            return fixed;
        }
        return header;
    }

    private Set<String> loadExistingHashes(List<ValidRow> rows) {
        if (rows.isEmpty()) {
            return new HashSet<>();
        }
        List<String> hashes = rows.stream().map(ValidRow::hash).toList();
        Set<String> existing = new HashSet<>();
        int chunkSize = 900;
        for (int i = 0; i < hashes.size(); i += chunkSize) {
            List<String> chunk = hashes.subList(i, Math.min(i + chunkSize, hashes.size()));
            existing.addAll(reviewRepository.findExistingHashes(chunk));
        }
        return existing;
    }

    private Optional<ValidRow> parseRow(List<String> cols) {
        String platformName = cols.get(0).trim();
        String productName = cols.get(1).trim();
        String brand = toNullIfBlank(cols.get(2));
        String model = toNullIfBlank(cols.get(3));
        Integer rating = toIntOrNull(cols.get(4));
        String reviewTimeRaw = cols.get(5).trim();
        String contentRaw = cols.get(6);
        Integer likeCount = toIntOrNull(cols.get(7));
        String reviewIdRaw = toNullIfBlank(cols.get(8));

        if (platformName.isBlank() || productName.isBlank() || contentRaw == null || contentRaw.isBlank()) {
            return Optional.empty();
        }

        String contentClean = TextCleaner.clean(contentRaw);
        if (contentClean.isBlank()) {
            return Optional.empty();
        }

        LocalDateTime reviewTime = null;
        if (!reviewTimeRaw.isBlank()) {
            try {
                reviewTime = LocalDateTime.parse(reviewTimeRaw, CSV_TIME_FORMATTER);
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
        String reviewTimeHashPart = reviewTime == null ? "" : reviewTime.format(CSV_TIME_FORMATTER);

        String hashInput = platformName + "|" + productName + "|" + contentClean + "|" + reviewTimeHashPart;
        String hash = HashUtils.sha256Hex(hashInput);

        return Optional.of(new ValidRow(
                platformName,
                productName,
                brand,
                model,
                rating,
                reviewTime,
                contentRaw,
                contentClean,
                likeCount,
                reviewIdRaw,
                hash
        ));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private static String toNullIfBlank(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static Integer toIntOrNull(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return Integer.parseInt(trimmed);
    }

    private record ProductKey(String name, String brand, String model) {}

    private record ValidRow(
            String platformName,
            String productName,
            String brand,
            String model,
            Integer rating,
            LocalDateTime reviewTime,
            String contentRaw,
            String contentClean,
            Integer likeCount,
            String reviewIdRaw,
            String hash
    ) {}
}
