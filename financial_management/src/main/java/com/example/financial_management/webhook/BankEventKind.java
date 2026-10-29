package com.example.financial_management.webhook;

public enum BankEventKind {
    PAYMENT,
    RECEIVE,
    TRANSFER_OUT,
    TOP_UP,
    WITHDRAW,
    REFUND,
    UNKNOWN
}
