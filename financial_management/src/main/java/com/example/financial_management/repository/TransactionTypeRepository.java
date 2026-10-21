package com.example.financial_management.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.financial_management.entity.TransactionTypeEntity;

@Repository
public interface TransactionTypeRepository extends JpaRepository<TransactionTypeEntity, Integer> {
    Optional<TransactionTypeEntity> findByCode(String code);
}
