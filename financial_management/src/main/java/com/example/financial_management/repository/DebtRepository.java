package com.example.financial_management.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.financial_management.entity.Debt;

public interface DebtRepository extends JpaRepository<Debt, UUID> {

    List<Debt> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Debt> findAllByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, int type);

    List<Debt> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, int status);

    List<Debt> findAllByUserIdAndTypeAndStatusOrderByCreatedAtDesc(UUID userId, int type, int status);

    Optional<Debt> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByTransactionId(UUID transactionId);

    @Modifying
    @Query("""
                UPDATE Debt d
                SET d.status = :overdueStatus
                WHERE d.userId = :userId
                  AND d.dueDate IS NOT NULL
                  AND d.dueDate < :today
                  AND d.status = :inProgressStatus
            """)
    int updateOverdueStatus(
            @Param("userId") UUID userId,
            @Param("today") LocalDate today,
            @Param("overdueStatus") int overdueStatus,
            @Param("inProgressStatus") int inProgressStatus);
}
