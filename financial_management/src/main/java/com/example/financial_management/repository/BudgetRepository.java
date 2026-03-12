package com.example.financial_management.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.financial_management.entity.Budget;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findAllByUserId(UUID userId);
}