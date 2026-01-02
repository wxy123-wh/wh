package com.wh.reputation.analysis;

import com.wh.reputation.common.ApiResponse;
import com.wh.reputation.common.BadRequestException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReviewAnalysisService reviewAnalysisService;
    private final AnalysisQueryService analysisQueryService;
    private final KeywordAnalysisService keywordAnalysisService;

    public AnalysisController(
            ReviewAnalysisService reviewAnalysisService,
            AnalysisQueryService analysisQueryService,
            KeywordAnalysisService keywordAnalysisService
    ) {
        this.reviewAnalysisService = reviewAnalysisService;
        this.analysisQueryService = analysisQueryService;
        this.keywordAnalysisService = keywordAnalysisService;
    }

    @PostMapping("/run")
    public ApiResponse<AnalysisRunResponseDto> run(@RequestBody AnalysisRunRequest body) {
        if (body == null || body.productId() == null) {
            throw new BadRequestException("productId is required");
        }
        LocalDate start = parseDateOrNull(body.start());
        LocalDate end = parseDateOrNull(body.end());
        reviewAnalysisService.analyzeByProduct(body.productId(), start, end);
        return ApiResponse.ok(new AnalysisRunResponseDto(true));
    }

    @GetMapping("/aspects")
    public ApiResponse<AspectAnalysisResponseDto> aspects(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end
    ) {
        return ApiResponse.ok(analysisQueryService.aspects(productId, parseDateOrNull(start), parseDateOrNull(end)));
    }

    @GetMapping("/trend")
    public ApiResponse<TrendResponseDto> trend(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "aspectId", required = false) Long aspectId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end
    ) {
        return ApiResponse.ok(analysisQueryService.trend(productId, aspectId, parseDateOrNull(start), parseDateOrNull(end)));
    }

    @GetMapping("/keywords")
    public ApiResponse<KeywordsResponseDto> keywords(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "aspectId", required = false) Long aspectId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "topN", required = false, defaultValue = "20") Integer topN
    ) {
        return ApiResponse.ok(keywordAnalysisService.keywords(productId, aspectId, parseDateOrNull(start), parseDateOrNull(end), topN));
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
}

