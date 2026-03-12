package com.example.financial_management.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.financial_management.entity.Transaction;
import com.example.financial_management.model.report.response.CategoryReportItem;
import com.example.financial_management.model.report.response.MonthlyReportResponseItem;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    Page<Transaction> findByUserId(UUID userId, Pageable pageable);

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    List<Transaction> findAllByUserIdAndType(UUID userId, int type);

    List<Transaction> findAllByAccountId(UUID accountId);

    List<Transaction> findAllByUserIdAndCategory(UUID userId, int category);

    List<Transaction> findAllByUserIdAndCurrency(UUID userId, int currency);

    boolean existsByAccountIdAndCurrencyNot(UUID accountId, int currency);

    Page<Transaction> findByAccountIdAndUserId(UUID accountId, UUID userId, Pageable pageable);

    @Query("""
                SELECT COALESCE(SUM(t.amount), 0)
                FROM Transaction t
                WHERE t.userId = :userId
                  AND t.createdAt BETWEEN :from AND :to
                  AND (:accountId IS NULL OR t.accountId = :accountId)
                  AND (:type IS NULL OR t.type = :type)
            """)
    Optional<BigDecimal> sumAmount(@Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("accountId") UUID accountId,
            @Param("type") Integer type);

    @Query(value = """
                  SELECT
                CAST([created_at] AS date) AS date,
                account_id,
                COALESCE(SUM(CASE WHEN [type] = 1 THEN [amount] ELSE 0 END), 0) AS income,
                COALESCE(SUM(CASE WHEN [type] = 0 THEN [amount] ELSE 0 END), 0) AS expense
            FROM [transactions]
            WHERE [user_id] = :userId
              AND [created_at] BETWEEN :start AND :end
              AND (:accountId IS NULL OR [account_id] = :accountId)
            GROUP BY CAST([created_at] AS date), account_id
            ORDER BY CAST([created_at] AS date), account_id
                  """, nativeQuery = true)
    List<Object[]> sumDaily(
            @Param("userId") UUID userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("accountId") UUID accountId);

    @Query("""
                SELECT new com.example.financial_management.model.report.response.MonthlyReportResponseItem(
                    MONTH(t.createdAt),
                    COALESCE(SUM(CASE WHEN t.type = 1 THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 0 THEN t.amount ELSE 0 END), 0),
                    YEAR(t.createdAt)
                )
                FROM Transaction t
                WHERE t.userId = :userId
                  AND YEAR(t.createdAt) = :year
                  AND (:accountId IS NULL OR t.accountId = :accountId)
                GROUP BY MONTH(t.createdAt), YEAR(t.createdAt)
                ORDER BY MONTH(t.createdAt)
            """)
    List<MonthlyReportResponseItem> sumMonthly(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("accountId") UUID accountId);

    @Query("""
                SELECT new com.example.financial_management.model.report.response.CategoryReportItem(
                    t.category,
                    COALESCE(SUM(CASE WHEN t.type = 0 THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 1 THEN t.amount ELSE 0 END), 0)
                )
                FROM Transaction t
                WHERE t.userId = :userId
                  AND (:accountId IS NULL OR t.accountId = :accountId)
                  AND (:fromDate IS NULL OR t.createdAt >= :fromDate)
                  AND (:toDate IS NULL OR t.createdAt <= :toDate)
                GROUP BY t.category
            """)
    List<CategoryReportItem> sumByCategory(
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    @Query("""
            SELECT SUM(t.amount)
            FROM Transaction t
            WHERE t.userId = :userId
            AND t.category = :categoryId
            AND MONTH(t.createdAt) = :month
            AND YEAR(t.createdAt) = :year
            """)
    BigDecimal sumSpendingByCategoryAndMonth(
            UUID userId,
            int categoryId,
            int month,
            int year);
}
