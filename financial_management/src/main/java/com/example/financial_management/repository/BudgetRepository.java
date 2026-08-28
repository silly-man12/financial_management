package com.example.financial_management.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.financial_management.entity.Budget;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findAllByUserId(UUID userId);

    List<Budget> findAllByUserIdAndMonthAndYear(UUID userId, int month, int year);

    @Query("""
            SELECT DISTINCT b
            FROM Budget b
            LEFT JOIN FETCH b.tags
            WHERE b.userId = :userId
            """)
    List<Budget> findAllWithTagsByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT DISTINCT b
            FROM Budget b
            LEFT JOIN FETCH b.tags
            WHERE b.userId = :userId
              AND b.month = :month
              AND b.year = :year
            """)
    List<Budget> findAllWithTagsByUserIdAndMonthAndYear(
            @Param("userId") UUID userId,
            @Param("month") int month,
            @Param("year") int year);
}