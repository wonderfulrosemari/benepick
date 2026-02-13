package com.benepick.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SimulateRecommendationRequest(
    @NotNull @Min(19) @Max(100)
    Integer age,

    @NotNull @Min(0)
    Integer income,

    @NotNull @Min(0)
    Integer monthlySpend,

    @NotBlank
    String priority,

    String accountPriority,

    String cardPriority,

    @NotBlank
    String salaryTransfer,

    @NotBlank
    String travelLevel,

    @NotNull
    List<String> categories,

    List<String> accountCategories,

    List<String> cardCategories
) {
}
