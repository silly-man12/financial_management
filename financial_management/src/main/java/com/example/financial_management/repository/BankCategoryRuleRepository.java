package com.example.financial_management.repository;

import com.example.financial_management.entity.BankCategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankCategoryRuleRepository extends JpaRepository<BankCategoryRule, UUID> {

    List<BankCategoryRule> findAllByUserIdAndActiveTrueOrderByPriorityDescCreatedAtAsc(UUID userId);

    List<BankCategoryRule> findAllByUserIdOrderByPriorityDescCreatedAtAsc(UUID userId);

    Optional<BankCategoryRule> findByIdAndUserId(UUID id, UUID userId);
}
