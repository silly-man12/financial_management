package com.example.financial_management.model.transaction;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class TransactionFilterRequest {
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<Integer> category;
    private List<UUID> accountId;
    private Integer type;
    private int page = 1;
    private int size = 20;
}
