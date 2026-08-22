package com.example.financial_management.constant;

public class SavingGoalStatus {
    public static final int IN_PROGRESS = 1; // Đang thực hiện / Chưa hoàn thành
    public static final int COMPLETED = 2;   // Đã hoàn thành (đạt >= 100%)
    public static final int CANCELLED = 3;   // Đã hủy

    private SavingGoalStatus() {
        // Utility class
    }

    public static String getName(int status) {
        switch (status) {
            case IN_PROGRESS:
                return "In Progress";
            case COMPLETED:
                return "Completed";
            case CANCELLED:
                return "Cancelled";
            default:
                return "Unknown";
        }
    }
}
