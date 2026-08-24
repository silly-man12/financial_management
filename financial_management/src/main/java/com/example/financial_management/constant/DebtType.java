package com.example.financial_management.constant;

public class DebtType {
    public static final int BORROW = 1; // Đi vay (Nợ phải trả)
    public static final int LEND = 2;   // Cho vay (Nợ phải thu)

    private DebtType() {
        // Utility class
    }

    public static String getName(int type) {
        switch (type) {
            case BORROW:
                return "Đi vay (Nợ phải trả)";
            case LEND:
                return "Cho vay (Nợ phải thu)";
            default:
                return "Unknown";
        }
    }
}
