package com.example.financial_management.repository;

import com.example.financial_management.entity.WalletSourceMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletSourceMappingRepository extends JpaRepository<WalletSourceMapping, UUID> {

    Optional<WalletSourceMapping> findByProviderAndSourceKeyAndActiveTrue(String provider, String sourceKey);

    Optional<WalletSourceMapping> findByUserIdAndProviderAndSourceKey(UUID userId, String provider, String sourceKey);

    List<WalletSourceMapping> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<WalletSourceMapping> findAllByUserIdAndActiveTrue(UUID userId);

    Optional<WalletSourceMapping> findByIdAndUserId(UUID id, UUID userId);
}
