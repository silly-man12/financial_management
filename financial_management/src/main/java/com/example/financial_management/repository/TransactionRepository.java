package com.example.financial_management.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.example.financial_management.model.report.response.CategoryDistribution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.financial_management.entity.Transaction;
import com.example.financial_management.model.report.response.CategoryReportItem;
import com.example.financial_management.model.report.response.MonthlyReportResponseItem;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    @EntityGraph(attributePaths = {"tags"})
    Page<Transaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"tags"})
    List<Transaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Override
    @EntityGraph(attributePaths = {"tags"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    List<Transaction> findAllByUserIdAndType(UUID userId, int type);

    List<Transaction> findAllByAccountId(UUID accountId);

    List<Transaction> findAllByUserIdAndCategory(UUID userId, int category);

    List<Transaction> findAllByUserIdAndCurrency(UUID userId, int currency);

    List<Transaction> findAllByUserIdAndCreatedAtBetween(
            UUID userId,
            LocalDateTime from,
            LocalDateTime to);

    boolean existsByAccountIdAndCurrencyNot(UUID accountId, int currency);

    boolean existsByAccountId(UUID accountId);

    @EntityGraph(attributePaths = {"tags"})
    Page<Transaction> findByAccountIdAndUserId(UUID accountId, UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"tags"})
    List<Transaction> findTop6ByAccountIdAndUserIdOrderByCreatedAtDesc(UUID accountId, UUID userId);

    List<Transaction> findAllByAccountIdAndUserId(UUID accountId, UUID userId);

    List<Transaction> findAllByAccountIdAndUserIdAndType(UUID accountId, UUID userId, int type);

    @Query("""
                SELECT t
                FROM Transaction t
                WHERE t.userId = :userId
                  AND t.type = :type
                  AND t.category = :category
                  AND FUNCTION('MONTH', t.createdAt) = :month
                  AND FUNCTION('YEAR', t.createdAt) = :year
                ORDER BY t.createdAt DESC
            """)
    List<Transaction> findAllByCategoryAndMonth(
            @Param("userId") UUID userId,
            @Param("type") int type,
            @Param("category") int category,
            @Param("month") int month,
            @Param("year") int year);

    @Query("""
                SELECT COALESCE(SUM(t.amount), 0)
                FROM Transaction t
                WHERE t.userId = :userId
                  AND (:accountId IS NULL OR t.accountId = :accountId)
                  AND (:type IS NULL OR t.type = :type)
            """)
    Optional<BigDecimal> sumAmountByType(@Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("type") Integer type);

    @Query("""
            SELECT new com.example.financial_management.model.report.response.CategoryDistribution(
                t.category,
                SUM(t.amount)
            )
            FROM Transaction t
            WHERE t.userId = :userId
              AND t.accountId = :accountId
              AND t.type = :type
            GROUP BY t.category
            """)
    List<CategoryDistribution> sumAmountByCategoryAndType(@Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("type") int type);

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
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.userId = :userId
              AND t.category = :categoryId
              AND t.type = 0
              AND t.category != 16
              AND MONTH(t.createdAt) = :month
              AND YEAR(t.createdAt) = :year
            """)
    BigDecimal sumSpendingByCategoryAndMonth(
            @Param("userId") UUID userId,
            @Param("categoryId") int categoryId,
            @Param("month") int month,
            @Param("year") int year);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.userId = :userId
              AND t.category = :categoryId
              AND t.type = 0
              AND t.category != 16
              AND MONTH(t.createdAt) = :month
              AND YEAR(t.createdAt) = :year
              AND NOT EXISTS (
                  SELECT 1 FROM t.tags tag WHERE tag.id IN :excludedTagIds
              )
            """)
    BigDecimal sumSpendingByCategoryAndMonthExcludingTags(
            @Param("userId") UUID userId,
            @Param("categoryId") int categoryId,
            @Param("month") int month,
            @Param("year") int year,
            @Param("excludedTagIds") Set<UUID> excludedTagIds);

    @Query("""
            SELECT t
            FROM Transaction t
            WHERE t.userId = :userId
              AND t.type = :type
              AND t.category != 16
              AND t.createdAt BETWEEN :start AND :end
            ORDER BY t.amount DESC
            """)
    List<Transaction> findTopExpenses(
            @Param("userId") UUID userId,
            @Param("type") int type,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT t
            FROM Transaction t
            LEFT JOIN FETCH t.tags
            WHERE t.userId = :userId
            ORDER BY t.createdAt DESC
            """)
    List<Transaction> findByUserIdWithTagsOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("""
            SELECT t
            FROM Transaction t
            JOIN t.tags tag
            WHERE t.userId = :userId
              AND tag.id = :tagId
            """)
    List<Transaction> findAllByUserIdAndTagId(@Param("userId") UUID userId, @Param("tagId") UUID tagId);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            JOIN t.tags tag
            WHERE t.userId = :userId
              AND tag.id = :tagId
              AND t.type = 0
              AND t.category != 16
              AND MONTH(t.createdAt) = :month
              AND YEAR(t.createdAt) = :year
            """)
    BigDecimal sumSpendingByTagAndMonth(
            @Param("userId") UUID userId,
            @Param("tagId") UUID tagId,
            @Param("month") int month,
            @Param("year") int year);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.userId = :userId
              AND t.type = 0
              AND t.category != 16
              AND MONTH(t.createdAt) = :month
              AND YEAR(t.createdAt) = :year
              AND EXISTS (
                  SELECT 1 FROM t.tags tag WHERE tag.id IN :tagIds
              )
            """)
    BigDecimal sumSpendingByTagIdsAndMonth(
            @Param("userId") UUID userId,
            @Param("tagIds") Set<UUID> tagIds,
            @Param("month") int month,
            @Param("year") int year);

    List<Transaction> findAllByTransferId(UUID transferId);

    @Query("""
            SELECT t.category, COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.userId = :userId
              AND t.type = 0
              AND t.category != 16
              AND MONTH(t.createdAt) = :month
              AND YEAR(t.createdAt) = :year
            GROUP BY t.category
            """)
    List<Object[]> sumSpendingGroupedByCategory(
            @Param("userId") UUID userId,
            @Param("month") int month,
            @Param("year") int year);

    @Query("""
            SELECT tag.id, COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            JOIN t.tags tag
            WHERE t.userId = :userId
              AND t.type = 0
              AND t.category != 16
              AND MONTH(t.createdAt) = :month
              AND YEAR(t.createdAt) = :year
            GROUP BY tag.id
            """)
    List<Object[]> sumSpendingGroupedByTag(
            @Param("userId") UUID userId,
            @Param("month") int month,
            @Param("year") int year);

    @Query(value = """
            SELECT
                tt.tag_id,
                COALESCE(SUM(CASE WHEN t.type = 0 AND t.category != 16 THEN t.amount ELSE 0 END), 0) AS total_expense,
                COALESCE(SUM(CASE WHEN t.type = 1 AND t.category != 16 THEN t.amount ELSE 0 END), 0) AS total_income,
                COUNT(t.id) AS tx_count
            FROM transaction_tags tt
            JOIN transactions t ON tt.transaction_id = t.id
            WHERE t.user_id = :userId
            GROUP BY tt.tag_id
            """, nativeQuery = true)
    List<Object[]> sumTagStatsByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT t.category, SUM(t.amount), COUNT(t.id)
            FROM Transaction t
            WHERE t.userId = :userId
              AND t.type = :type
              AND t.category != 16
              AND t.createdAt BETWEEN :start AND :end
            GROUP BY t.category
            """)
    List<Object[]> sumGroupedByCategoryAndType(
            @Param("userId") UUID userId,
            @Param("type") int type,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = """
            SELECT
                CAST(created_at AS date) AS tx_date,
                COALESCE(SUM(CASE WHEN type = 1 AND category != 16 THEN amount ELSE 0 END), 0) AS income,
                COALESCE(SUM(CASE WHEN type = 0 AND category != 16 THEN amount ELSE 0 END), 0) AS expense
            FROM transactions
            WHERE user_id = :userId
              AND created_at BETWEEN :start AND :end
            GROUP BY CAST(created_at AS date)
            ORDER BY tx_date ASC
            """, nativeQuery = true)
    List<Object[]> sumDailyAggregatedByUser(
            @Param("userId") UUID userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = """
            SELECT
                account_id,
                COALESCE(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0) AS inflow,
                COALESCE(SUM(CASE WHEN type = 0 THEN amount ELSE 0 END), 0) AS outflow
            FROM transactions
            WHERE user_id = :userId
              AND account_id IS NOT NULL
              AND created_at BETWEEN :start AND :end
            GROUP BY account_id
            """, nativeQuery = true)
    List<Object[]> sumFlowByAccount(
            @Param("userId") UUID userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = """
            SELECT
                COALESCE(SUM(CASE WHEN type = 1 AND category != 16 THEN amount ELSE 0 END), 0) AS income,
                COALESCE(SUM(CASE WHEN type = 0 AND category != 16 THEN amount ELSE 0 END), 0) AS expense
            FROM transactions
            WHERE user_id = :userId
              AND created_at BETWEEN :start AND :end
            """, nativeQuery = true)
    List<Object[]> sumTotalIncomeAndExpense(
            @Param("userId") UUID userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}


