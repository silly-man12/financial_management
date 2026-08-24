package com.example.financial_management.mapper;

import java.math.BigDecimal;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.example.financial_management.constant.DebtStatus;
import com.example.financial_management.constant.DebtType;
import com.example.financial_management.entity.Debt;
import com.example.financial_management.model.debt.DebtRequest;
import com.example.financial_management.model.debt.DebtResponse;
import com.example.financial_management.model.debt.DebtUpdateRequest;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DebtMapper {

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "remainingAmount", source = "request.initialAmount")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Debt toEntity(DebtRequest request, UUID userId);

    @Mapping(target = "typeName", expression = "java(mapTypeName(entity.getType()))")
    @Mapping(target = "statusName", expression = "java(mapStatusName(entity.getStatus()))")
    @Mapping(target = "paidAmount", expression = "java(calculatePaidAmount(entity.getInitialAmount(), entity.getRemainingAmount()))")
    @Mapping(target = "payments", ignore = true)
    DebtResponse toResponse(Debt entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "initialAmount", ignore = true)
    @Mapping(target = "remainingAmount", ignore = true)
    @Mapping(target = "startDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(DebtUpdateRequest request, @MappingTarget Debt entity);

    default String mapTypeName(int type) {
        return DebtType.getName(type);
    }

    default String mapStatusName(int status) {
        return DebtStatus.getName(status);
    }

    default BigDecimal calculatePaidAmount(BigDecimal initial, BigDecimal remaining) {
        if (initial == null) return BigDecimal.ZERO;
        if (remaining == null) return initial;
        return initial.subtract(remaining);
    }
}
