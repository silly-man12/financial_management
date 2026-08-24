package com.example.financial_management.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.financial_management.entity.Debt;

public interface DebtRepository extends JpaRepository<Debt, UUID> {

    List<Debt> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Debt> findAllByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, int type);

    List<Debt> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, int status);

    List<Debt> findAllByUserIdAndTypeAndStatusOrderByCreatedAtDesc(UUID userId, int type, int status);

    Optional<Debt> findByIdAndUserId(UUID id, UUID userId);
}
