package com.example.financial_management.model.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BankCategoryRuleRequest {

    @NotBlank(message = "Keyword is required")
    private String keyword;

    @NotNull(message = "Category ID is required")
    private Integer category;

    private String direction; // IN, OUT, or null for any

    private Integer priority = 100;

    private String description;

    private Boolean active = true;
}
