package com.example.financial_management.model.webhook;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BankWebhookSimulateRequest {

    @NotBlank(message = "Provider is required (SEPAY)")
    private String provider = "SEPAY";

    @NotBlank(message = "Raw JSON payload is required")
    private String rawPayload;
}
