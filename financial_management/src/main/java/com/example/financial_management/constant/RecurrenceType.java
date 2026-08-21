package com.example.financial_management.constant;

public class RecurrenceType {
    public static final int DAILY = 1;
    public static final int WEEKLY = 2;
    public static final int MONTHLY = 3;
    public static final int YEARLY = 4;

    private RecurrenceType() {
        // Utility class
    }

    public static String getName(int type) {
        switch (type) {
            case DAILY:
                return "Daily";
            case WEEKLY:
                return "Weekly";
            case MONTHLY:
                return "Monthly";
            case YEARLY:
                return "Yearly";
            default:
                return "Unknown";
        }
    }
}
