package com.example.financial_management.repository;

import com.example.financial_management.entity.BankWebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankWebhookEventRepository extends JpaRepository<BankWebhookEvent, UUID> {

    Optional<BankWebhookEvent> findByProviderAndExternalId(String provider, String externalId);

    Page<BankWebhookEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<BankWebhookEvent> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status, Pageable pageable);

    Optional<BankWebhookEvent> findByIdAndUserId(UUID id, UUID userId);

    List<BankWebhookEvent> findByUserIdAndStatusInAndOccurredAtBetweenOrderByOccurredAtAsc(
            UUID userId,
            Collection<String> statuses,
            LocalDateTime from,
            LocalDateTime to
    );

    List<BankWebhookEvent> findByStatusInAndTransactionIdIsNullOrderByCreatedAtAsc(Collection<String> statuses);

    boolean existsByTransactionId(UUID transactionId);
}
