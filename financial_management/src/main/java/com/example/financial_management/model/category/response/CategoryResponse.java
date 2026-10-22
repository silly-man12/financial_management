package com.example.financial_management.model.category.response;

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
public class CategoryResponse {
    private Integer id;
    private Integer transactionTypeId;
    private String code;
    private String name;
    private String nameVi;
    private String icon;
    private String color;
    private boolean isSystem;
    private boolean isActive;
    private Integer displayOrder;
}
