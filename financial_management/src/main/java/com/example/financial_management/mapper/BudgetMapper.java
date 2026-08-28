package com.example.financial_management.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.example.financial_management.entity.Budget;
import com.example.financial_management.model.budget.BudgetRequest;
import com.example.financial_management.model.budget.BudgetResponse;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {TagMapper.class})
public interface BudgetMapper {
    @Mapping(target = "tags", ignore = true)
    Budget toEntity(BudgetRequest request);

    @Mapping(target = "monthYear", expression = "java(mapMonthYear(entity.getMonth(), entity.getYear()))")
    @Mapping(target = "tags", source = "tags")
    @Mapping(target = "tagName", ignore = true)
    @Mapping(target = "tagColor", ignore = true)
    BudgetResponse toResponse(Budget entity);

    default String mapMonthYear(int month, int year) {
        return String.format("%02d/%d", month, year);
    }
}

