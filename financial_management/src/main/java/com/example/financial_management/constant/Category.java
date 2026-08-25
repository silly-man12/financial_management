package com.example.financial_management.constant;

public class Category {
    // Expense Categories
    public static final int FOOD = 1;
    public static final int TRANSPORT = 2;
    public static final int ENTERTAINMENT = 3;
    public static final int UTILITIES = 4;
    public static final int HEALTHCARE = 5;
    public static final int EDUCATION = 6;
    public static final int SHOPPING = 7;
    public static final int HOUSING = 8;
    public static final int DEBT = 9;
    public static final int OTHER_EXPENSE = 10;

    // Income Categories
    public static final int SALARY = 11;
    public static final int BUSINESS = 12;
    public static final int INVESTMENTS = 13;
    public static final int GIFTS = 14;
    public static final int OTHER_INCOME = 15;

    // Transfer Category
    public static final int TRANSFER = 16;

    // Savings Category
    public static final int SAVINGS = 17;

    private Category() {
        // Utility class
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
