package com.example.financial_management.model.transaction;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class TransactionFilterRequest {
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<Integer> category;
    private Integer type;
    private int page = 1;
    private int size = 20;
}
