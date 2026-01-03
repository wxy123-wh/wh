package com.wh.reputation.compare;

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
@RequestMapping("/api/compare")
public class CompareController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    @GetMapping("/aspects")
    public ApiResponse<CompareAspectsResponseDto> aspects(
            @RequestParam("productId") Long productId,
            @RequestParam("competitorId") Long competitorId,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end
    ) {
        return ApiResponse.ok(compareService.compareAspects(productId, competitorId, parseDateOrNull(start), parseDateOrNull(end)));
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

