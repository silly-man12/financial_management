package com.example.financial_management.model.report.response;

import com.example.financial_management.constant.Category;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDistribution {
    private int category;
    private BigDecimal total;
    private String categoryName;

    public CategoryDistribution(int category, BigDecimal total) {
        this.category = category;
        this.total = total;
        this.categoryName = Category.getName(category);
    }
}
