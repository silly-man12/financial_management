package com.example.financial_management.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.example.financial_management.entity.Budget;
import com.example.financial_management.model.budget.BudgetRequest;
import com.example.financial_management.model.budget.BudgetResponse;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BudgetMapper {
    Budget toEntity(BudgetRequest request);

    @Mapping(target = "monthYear", expression = "java(mapMonthYear(entity.getMonth(), entity.getYear()))")
    BudgetResponse toResponse(Budget entity);

    default String mapMonthYear(int month, int year) {
        return String.format("%02d/%d", month, year);
    }
}
