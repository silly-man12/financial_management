package com.example.financial_management.constant;

public class SavingContributionType {
    public static final int DEPOSIT = 1;  // Nạp tiền / Góp quỹ
    public static final int WITHDRAW = 2; // Rút tiền từ quỹ

    private SavingContributionType() {
        // Utility class
    }

    public static String getName(int type) {
        switch (type) {
            case DEPOSIT:
                return "Nạp tiền / Góp quỹ";
            case WITHDRAW:
                return "Rút tiền";
            default:
                return "Không xác định";
        }
    }
}
