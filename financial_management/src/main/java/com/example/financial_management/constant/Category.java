package com.example.financial_management.constant;

public class Category {
    // Expense Categories (1xx)
    public static final int FOOD = 101;
    public static final int TRANSPORT = 102;
    public static final int ENTERTAINMENT = 103;
    public static final int UTILITIES = 104;
    public static final int HEALTHCARE = 105;
    public static final int EDUCATION = 106;
    public static final int SHOPPING = 107;
    public static final int HOUSING = 108;
    public static final int DEBT = 109;
    public static final int TRAVELING = 110;
    public static final int PRESENT = 111;
    public static final int OTHER_EXPENSE = 112;

    // Income Categories (2xx)
    public static final int SALARY = 201;
    public static final int BUSINESS = 202;
    public static final int INVESTMENTS = 203;
    public static final int GIFTS = 204;
    public static final int OTHER_INCOME = 205;

    // Transfer & Savings Categories (3xx)
    public static final int TRANSFER = 301;
    public static final int SAVINGS = 302;

    private Category() {
        // Utility class
    }

    public static boolean isExpense(int categoryId) {
        return categoryId >= 100 && categoryId < 200;
    }

    public static boolean isIncome(int categoryId) {
        return categoryId >= 200 && categoryId < 300;
    }

    public static String getName(int categoryId) {
        switch (categoryId) {
            case FOOD:
                return "Food";
            case TRANSPORT:
                return "Transport";
            case ENTERTAINMENT:
                return "Entertainment";
            case UTILITIES:
                return "Utilities";
            case HEALTHCARE:
                return "Healthcare";
            case EDUCATION:
                return "Education";
            case SHOPPING:
                return "Shopping";
            case HOUSING:
                return "Housing";
            case DEBT:
                return "Debt";
            case TRAVELING:
                return "Traveling";
            case PRESENT:
                return "Present";
            case OTHER_EXPENSE:
                return "Other Expense";
            case SALARY:
                return "Salary";
            case BUSINESS:
                return "Business";
            case INVESTMENTS:
                return "Investments";
            case GIFTS:
                return "Gifts";
            case OTHER_INCOME:
                return "Other Income";
            case TRANSFER:
                return "Transfer";
            case SAVINGS:
                return "Savings";
            default:
                return "Unknown";
        }
    }
}
