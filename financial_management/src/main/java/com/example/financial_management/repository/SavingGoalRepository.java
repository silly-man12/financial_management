package com.example.financial_management.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.financial_management.entity.SavingGoal;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, UUID> {

    List<SavingGoal> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SavingGoal> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, int status);

    Optional<SavingGoal> findByIdAndUserId(UUID id, UUID userId);
}
