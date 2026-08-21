package com.example.financial_management.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.example.financial_management.entity.RecurringTransaction;
import com.example.financial_management.model.recurring.RecurringTransactionRequest;
import com.example.financial_management.model.recurring.RecurringTransactionResponse;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RecurringTransactionMapper {

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "nextExecutionDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    RecurringTransaction toEntity(RecurringTransactionRequest request, UUID userId);

    RecurringTransactionResponse toResponse(RecurringTransaction entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "nextExecutionDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(RecurringTransactionRequest request, @MappingTarget RecurringTransaction entity);
}
