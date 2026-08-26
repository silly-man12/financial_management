package com.example.financial_management.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.example.financial_management.constant.SavingContributionType;
import com.example.financial_management.entity.SavingGoalContribution;
import com.example.financial_management.model.saving_goal.SavingGoalContributionRequest;
import com.example.financial_management.model.saving_goal.SavingGoalContributionResponse;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, imports = {SavingContributionType.class})
public interface SavingGoalContributionMapper {

    @Mapping(target = "savingGoalId", source = "savingGoalId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "transactionId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    SavingGoalContribution toEntity(SavingGoalContributionRequest request, UUID savingGoalId);

    @Mapping(target = "typeName", expression = "java(SavingContributionType.getName(entity.getType()))")
    SavingGoalContributionResponse toResponse(SavingGoalContribution entity);
}
