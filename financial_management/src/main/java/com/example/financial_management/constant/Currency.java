package com.example.financial_management.constant;

public class Currency {
    public static final int USD = 0;
    public static final int VND = 1;

    private Currency() {
    }

    public static String getCode(int currency) {
        switch (currency) {
            case USD:
                return "USD";
            case VND:
                return "VND";
            default:
                return "UNKNOWN";
        }
    }
}
