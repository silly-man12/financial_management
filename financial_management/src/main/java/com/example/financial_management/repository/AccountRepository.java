package com.example.financial_management.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.financial_management.entity.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    List<Account> findAllByUserIdAndStatus(UUID userId, int status);

    List<Account> findAllByUserIdAndCurrency(UUID userId, int currency);

    Optional<Account> findByIdAndStatus(UUID id, int status);

    List<Account> findAllByUserIdAndType(UUID userId, int type);

    Optional<Account> findByIdAndUserIdAndStatus(UUID accountId, UUID userId, int status);

}
