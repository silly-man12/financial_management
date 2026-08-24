package com.example.financial_management.constant;

public class DebtStatus {
    public static final int IN_PROGRESS = 1; // Đang nợ
    public static final int PAID = 2;        // Đã trả xong (Đã tất toán)
    public static final int OVERDUE = 3;     // Quá hạn

    private DebtStatus() {
        // Utility class
    }

    public static String getName(int status) {
        switch (status) {
            case IN_PROGRESS:
                return "Đang nợ";
            case PAID:
                return "Đã trả xong";
            case OVERDUE:
                return "Quá hạn";
            default:
                return "Unknown";
        }
    }
}
