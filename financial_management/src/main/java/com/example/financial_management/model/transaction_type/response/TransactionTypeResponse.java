package com.example.financial_management.model.transaction_type.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionTypeResponse {
    private Integer id;
    private String code;
    private String name;
}
