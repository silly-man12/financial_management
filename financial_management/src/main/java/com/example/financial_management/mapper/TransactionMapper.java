package com.example.financial_management.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.example.financial_management.entity.Transaction;
import com.example.financial_management.model.transaction.TransactionRequest;
import com.example.financial_management.model.transaction.TransactionResponse;
import com.example.financial_management.model.transaction.TransactionUpdateResponse;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {TagMapper.class})
public interface TransactionMapper {
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "tags", ignore = true)
    Transaction toEntity(TransactionRequest request, UUID userId);

    @Mapping(target = "tags", source = "tags")
    TransactionResponse toResponse(Transaction entity);

    @Mapping(target = "tags", source = "tags")
    TransactionUpdateResponse toUpdateResponse(Transaction entity);
}

