package com.example.financial_management.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.financial_management.entity.SavingGoalContribution;

public interface SavingGoalContributionRepository extends JpaRepository<SavingGoalContribution, UUID> {

    List<SavingGoalContribution> findAllBySavingGoalIdOrderByContributionDateDesc(UUID savingGoalId);

    Optional<SavingGoalContribution> findByIdAndSavingGoalId(UUID id, UUID savingGoalId);

    void deleteAllBySavingGoalId(UUID savingGoalId);

    boolean existsByTransactionId(UUID transactionId);
}
