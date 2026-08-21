package com.example.financial_management.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.financial_management.entity.RecurringTransaction;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

    List<RecurringTransaction> findByStatus(int status);

    List<RecurringTransaction> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<RecurringTransaction> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, int status);

    Optional<RecurringTransaction> findByIdAndUserId(UUID id, UUID userId);

    // Tìm các giao dịch định kỳ đang ACTIVE và đến ngày thực hiện
    List<RecurringTransaction> findAllByStatusAndNextExecutionDateLessThanEqual(int status, LocalDate date);
}
