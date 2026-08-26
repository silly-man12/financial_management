package com.example.financial_management.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.example.financial_management.entity.SavingGoal;
import com.example.financial_management.model.saving_goal.SavingGoalRequest;
import com.example.financial_management.model.saving_goal.SavingGoalResponse;
import com.example.financial_management.model.saving_goal.SavingGoalUpdateRequest;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SavingGoalMapper {

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "currentAmount", source = "request.initialAmount")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    SavingGoal toEntity(SavingGoalRequest request, UUID userId);

    @Mapping(target = "contributions", ignore = true)
    @Mapping(target = "progressPercentage", expression = "java(calculateProgress(entity.getCurrentAmount(), entity.getTargetAmount()))")
    SavingGoalResponse toResponse(SavingGoal entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "currentAmount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(SavingGoalUpdateRequest request, @MappingTarget SavingGoal entity);

    default double calculateProgress(BigDecimal current, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0 || current == null) {
            return 0.0;
        }
        return current.divide(target, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
