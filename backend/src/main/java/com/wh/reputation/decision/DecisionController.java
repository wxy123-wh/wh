package com.wh.reputation.decision;

import com.wh.reputation.common.ApiResponse;
import com.wh.reputation.common.BadRequestException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/decision")
public class DecisionController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DecisionPriorityService decisionPriorityService;
    private final SuggestionService suggestionService;

    public DecisionController(DecisionPriorityService decisionPriorityService, SuggestionService suggestionService) {
        this.decisionPriorityService = decisionPriorityService;
        this.suggestionService = suggestionService;
    }

    @GetMapping("/priority")
    public ApiResponse<PriorityResponseDto> priority(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "topN", required = false, defaultValue = "10") Integer topN
    ) {
        return ApiResponse.ok(decisionPriorityService.priorities(productId, parseDateOrNull(start), parseDateOrNull(end), topN));
    }

    @GetMapping("/suggestions")
    public ApiResponse<SuggestionsResponseDto> suggestions(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end
    ) {
        return ApiResponse.ok(suggestionService.suggestions(productId, parseDateOrNull(start), parseDateOrNull(end)));
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
