package com.example.financial_management.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.financial_management.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndStatus(UUID id, int status);

    Optional<User> findByEmailAndStatus(String email, int status);

    Optional<User> findByTelegramChatId(Long telegramChatId);

    Optional<User> findByTelegramChatIdAndStatus(Long telegramChatId, int status);

    @Query("SELECT u FROM User u WHERE u.status = :status AND u.telegramChatId IS NOT NULL AND u.telegramDailyReportEnabled = true")
    List<User> findActiveUsersWithDailyReportEnabled(@Param("status") int status);
}

