package com.wh.reputation.crawl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.common.BadRequestException;
import com.wh.reputation.review.ReviewImportItem;
import com.wh.reputation.review.ReviewImportResult;
import com.wh.reputation.review.ReviewImportService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class CrawlService {
    private static final DateTimeFormatter BATCH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ObjectMapper objectMapper;
    private final ReviewImportService reviewImportService;

    public CrawlService(ObjectMapper objectMapper, ReviewImportService reviewImportService) {
        this.objectMapper = objectMapper;
        this.reviewImportService = reviewImportService;
    }

    public CrawlRunResult run(CrawlRunRequest request) {
        String platformName = request.platformName();
        String productName = request.productName();
        Integer pagesRaw = request.pages();
        if (platformName == null || platformName.isBlank()) {
            throw new BadRequestException("platformName is required");
        }
        if (productName == null || productName.isBlank()) {
            throw new BadRequestException("productName is required");
        }
        String platform = platformName.trim();
        String product = productName.trim();
        if (!platform.matches("[A-Za-z0-9_-]+")) {
            throw new BadRequestException("invalid platformName");
        }

        int pages = pagesRaw == null ? 1 : pagesRaw;
        if (pages <= 0) {
            pages = 1;
        }

        Path platformDir = resolvePlatformDir(platform);
        List<Path> files = listSampleFiles(platformDir);
        if (files.isEmpty()) {
            throw new BadRequestException("no crawl samples found for platform: " + platform);
        }

        List<Path> selected = files.subList(0, Math.min(pages, files.size()));
        List<ReviewImportItem> items = new ArrayList<>();
        for (Path path : selected) {
            items.addAll(loadSampleFile(path, platform, product));
        }
        if (items.isEmpty()) {
            throw new BadRequestException("no reviews parsed from crawl samples for platform: " + platform);
        }

        String batchId = "crawl_" + LocalDateTime.now().format(BATCH_TIME_FORMAT);
        ReviewImportResult result = reviewImportService.importJson(items);
        return new CrawlRunResult(result.inserted(), result.skipped(), result.errors(), batchId);
    }

    private Path resolvePlatformDir(String platformName) {
        Path baseDir = resolveCrawlSamplesBaseDir();
        Path exact = baseDir.resolve(platformName);
        if (Files.isDirectory(exact)) {
            return exact;
        }

        try (var stream = Files.list(baseDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(platformName))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("crawl sample dir not found: " + platformName));
        } catch (IOException e) {
            throw new IllegalStateException("failed to list crawl samples", e);
        }
    }

    private Path resolveCrawlSamplesBaseDir() {
        List<Path> candidates = List.of(
                Paths.get("data").resolve("crawl_samples"),
                Paths.get("..").resolve("data").resolve("crawl_samples")
        );
        return candidates.stream()
                .filter(Files::isDirectory)
                .findFirst()
                .map(Path::toAbsolutePath)
                .orElseThrow(() -> new BadRequestException("crawl_samples dir not found, tried: " + candidates));
    }

    private List<Path> listSampleFiles(Path platformDir) {
        try (var stream = Files.list(platformDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".json") || name.endsWith(".html");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("failed to list crawl samples: " + platformDir, e);
        }
    }

    private List<ReviewImportItem> loadSampleFile(Path path, String platformName, String productName) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".json")) {
            return loadJsonSample(path, platformName, productName);
        }
        if (filename.endsWith(".html")) {
            return loadHtmlSample(path, platformName, productName);
        }
        return List.of();
    }

    private List<ReviewImportItem> loadJsonSample(Path path, String platformName, String productName) {
        try (var input = Files.newInputStream(path)) {
            List<ReviewImportItem> raw = objectMapper.readValue(input, new TypeReference<>() {});
            List<ReviewImportItem> normalized = new ArrayList<>(raw.size());
            for (ReviewImportItem item : raw) {
                if (item == null) {
                    continue;
                }
                normalized.add(new ReviewImportItem(
                        platformName,
                        productName,
                        item.brand(),
                        item.model(),
                        item.rating(),
                        item.reviewTime(),
                        item.content(),
                        item.likeCount(),
                        item.reviewIdRaw()
                ));
            }
            return normalized;
        } catch (IOException e) {
            throw new BadRequestException("invalid crawl sample json: " + path.getFileName());
        }
    }

    private List<ReviewImportItem> loadHtmlSample(Path path, String platformName, String productName) {
        String raw;
        try {
            raw = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read crawl sample html: " + path.getFileName(), e);
        }

        String noTags = raw.replaceAll("<[^>]*>", " ");
        String collapsed = noTags.replaceAll("\\s+", " ").trim();
        if (collapsed.isBlank()) {
            return List.of();
        }
        return List.of(new ReviewImportItem(
                platformName,
                productName,
                null,
                null,
                null,
                null,
                collapsed,
                null,
                null
        ));
    }
}

