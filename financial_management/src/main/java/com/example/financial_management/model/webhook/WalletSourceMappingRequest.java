package com.example.financial_management.model.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class WalletSourceMappingRequest {

    @NotBlank(message = "Provider is required (SEPAY)")
    private String provider = "SEPAY";

    @NotBlank(message = "Source key (bank account number) is required")
    private String sourceKey;

    @NotNull(message = "Target accountId is required")
    private UUID accountId;

    private String bankCode;

    private String label;

    private Boolean active = true;
}
