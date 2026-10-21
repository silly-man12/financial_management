package com.example.financial_management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.financial_management.entity.CategoryEntity;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Integer> {
    Optional<CategoryEntity> findByCode(String code);

    List<CategoryEntity> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    List<CategoryEntity> findAllByTransactionTypeIdAndIsActiveTrueOrderByDisplayOrderAsc(Integer transactionTypeId);
}
