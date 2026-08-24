package com.example.financial_management.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.example.financial_management.entity.DebtPayment;
import com.example.financial_management.model.debt.DebtPaymentRequest;
import com.example.financial_management.model.debt.DebtPaymentResponse;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DebtPaymentMapper {

    @Mapping(target = "debtId", source = "debtId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    DebtPayment toEntity(DebtPaymentRequest request, UUID debtId);

    DebtPaymentResponse toResponse(DebtPayment entity);
}
