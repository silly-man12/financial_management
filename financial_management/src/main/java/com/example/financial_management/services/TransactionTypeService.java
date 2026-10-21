package com.example.financial_management.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.example.financial_management.entity.TransactionTypeEntity;
import com.example.financial_management.model.transaction_type.response.TransactionTypeResponse;
import com.example.financial_management.repository.TransactionTypeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionTypeService {

    private final TransactionTypeRepository transactionTypeRepository;

    @Cacheable(value = "transactionTypes", key = "'all'")
    public List<TransactionTypeResponse> getAllTransactionTypes() {
        return transactionTypeRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "transactionTypes", key = "'id:' + #id")
    public TransactionTypeResponse getTransactionTypeById(Integer id) {
        if (id == null) {
            return null;
        }
        return transactionTypeRepository.findById(id)
                .map(this::mapToResponse)
                .orElse(null);
    }

    public boolean isValidTransactionType(Integer id) {
        if (id == null) {
            return false;
        }
        return transactionTypeRepository.existsById(id);
    }

    public String getTransactionTypeName(Integer id) {
        TransactionTypeResponse type = getTransactionTypeById(id);
        return type != null ? type.getName() : "Unknown";
    }

    private TransactionTypeResponse mapToResponse(TransactionTypeEntity entity) {
        return TransactionTypeResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .build();
    }
}
