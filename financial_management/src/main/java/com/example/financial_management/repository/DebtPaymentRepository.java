package com.example.financial_management.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.financial_management.entity.DebtPayment;

public interface DebtPaymentRepository extends JpaRepository<DebtPayment, UUID> {

    List<DebtPayment> findAllByDebtIdOrderByPaymentDateDesc(UUID debtId);

    Optional<DebtPayment> findByIdAndDebtId(UUID id, UUID debtId);

    void deleteAllByDebtId(UUID debtId);

    boolean existsByTransactionId(UUID transactionId);
}
