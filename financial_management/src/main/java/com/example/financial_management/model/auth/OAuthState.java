package com.example.financial_management.model.auth;

import lombok.Data;

@Data
public class OAuthState {
    private String base;
    private String returnUrl;
}
