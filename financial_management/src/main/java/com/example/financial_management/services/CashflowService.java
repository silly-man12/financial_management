package com.example.financial_management.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.financial_management.constant.Category;
import com.example.financial_management.constant.RecurrenceType;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.Budget;
import com.example.financial_management.entity.RecurringTransaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.cashflow.BudgetForecastItem;
import com.example.financial_management.model.cashflow.CashflowActual;
import com.example.financial_management.model.cashflow.CashflowForecastResponse;
import com.example.financial_management.model.cashflow.CashflowForecastSummary;
import com.example.financial_management.model.cashflow.CashflowRecurringForecast;
import com.example.financial_management.model.cashflow.RecurringScheduledItem;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.BudgetRepository;
import com.example.financial_management.repository.RecurringTransactionRepository;
import com.example.financial_management.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashflowService {

    private final UserService userService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final BudgetRepository budgetRepository;

    /**
     * Tạo dự báo dòng tiền đến cuối tháng cho user.
     *
     * @param auth      thông tin xác thực
     * @param month     tháng (1-12), null = tháng hiện tại
     * @param year      năm, null = năm hiện tại
     * @param accountId lọc theo tài khoản cụ thể (null = tất cả tài khoản)
     */
    public CashflowForecastResponse getForecast(Auth auth, Integer month, Integer year, UUID accountId) {
        User user = userService.getAuthenticatedUser(auth);

        LocalDate today = LocalDate.now();
        YearMonth ym = resolveYearMonth(month, year, today);

        int totalDays = ym.lengthOfMonth();
        int daysElapsed = ym.equals(YearMonth.from(today))
                ? today.getDayOfMonth()
                : totalDays; // Nếu tháng quá khứ/tương lai, coi như toàn tháng
        int daysRemaining = totalDays - daysElapsed;

        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        // ===== 1. Số dư tài khoản hiện tại =====
        BigDecimal currentBalance = calculateCurrentBalance(user.getId(), accountId);

        // ===== 2. Thực thu/chi từ đầu tháng đến hôm nay =====
        LocalDateTime actualStart = monthStart.atStartOfDay();
        LocalDateTime actualEnd = today.atTime(LocalTime.MAX);
        CashflowActual actualSoFar = calculateActual(user.getId(), actualStart, actualEnd);

        // ===== 3. Giao dịch định kỳ dự kiến từ ngày mai đến cuối tháng =====
        LocalDate forecastFrom = today.plusDays(1);
        List<RecurringScheduledItem> recurringScheduled = new ArrayList<>();
        BigDecimal forecastIncome = BigDecimal.ZERO;
        BigDecimal forecastExpense = BigDecimal.ZERO;

        if (forecastFrom.isBefore(monthEnd) || forecastFrom.isEqual(monthEnd)) {
            List<RecurringTransaction> activeRecurring =
                    recurringTransactionRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(
                            user.getId(), Status.ACTIVE);

            for (RecurringTransaction rt : activeRecurring) {
                // Bỏ qua loại TRANSFER (category = 16) và SAVINGS (category = 17)
                if (rt.getCategory() == Category.TRANSFER || rt.getCategory() == Category.SAVINGS) {
                    continue;
                }

                List<LocalDate> occurrences = getOccurrencesInRange(rt, forecastFrom, monthEnd);
                for (LocalDate occDate : occurrences) {
                    BigDecimal amount = rt.getAmount() != null ? rt.getAmount() : BigDecimal.ZERO;
                    recurringScheduled.add(RecurringScheduledItem.builder()
                            .recurringId(rt.getId())
                            .description(rt.getDescription())
                            .amount(amount)
                            .type(rt.getType())
                            .category(rt.getCategory())
                            .categoryName(Category.getName(rt.getCategory()))
                            .expectedDate(occDate)
                            .recurrenceType(rt.getRecurrenceType())
                            .build());

                    if (rt.getType() == TransactionType.INCOME) {
                        forecastIncome = forecastIncome.add(amount);
                    } else if (rt.getType() == TransactionType.EXPENSE) {
                        forecastExpense = forecastExpense.add(amount);
                    }
                }
            }
        }

        // Sắp xếp theo ngày dự kiến
        recurringScheduled.sort((a, b) -> a.getExpectedDate().compareTo(b.getExpectedDate()));

        CashflowRecurringForecast recurringForecast = CashflowRecurringForecast.builder()
                .expectedIncome(forecastIncome)
                .expectedExpense(forecastExpense)
                .build();

        // ===== 4. Tổng dự báo cuối tháng =====
        BigDecimal projectedIncome = actualSoFar.getIncome().add(forecastIncome);
        BigDecimal projectedExpense = actualSoFar.getExpense().add(forecastExpense);
        BigDecimal projectedNet = projectedIncome.subtract(projectedExpense);
        BigDecimal projectedEndBalance = currentBalance.add(forecastIncome).subtract(forecastExpense);

        BigDecimal safeToSpend = projectedEndBalance.compareTo(BigDecimal.ZERO) > 0
                ? projectedEndBalance
                : BigDecimal.ZERO;

        String warningLevel = computeWarningLevel(projectedEndBalance, currentBalance);

        CashflowForecastSummary forecast = CashflowForecastSummary.builder()
                .projectedEndBalance(projectedEndBalance)
                .projectedIncome(projectedIncome)
                .projectedExpense(projectedExpense)
                .projectedNet(projectedNet)
                .safeToSpend(safeToSpend)
                .warningLevel(warningLevel)
                .build();

        // ===== 5. So sánh ngân sách từng danh mục =====
        List<BudgetForecastItem> budgetComparison = buildBudgetComparison(
                user.getId(), ym.getMonthValue(), ym.getYear(), daysElapsed, totalDays);

        return CashflowForecastResponse.builder()
                .today(today)
                .daysRemaining(daysRemaining)
                .daysElapsed(daysElapsed)
                .totalDaysInMonth(totalDays)
                .currentBalance(currentBalance)
                .actualSoFar(actualSoFar)
                .recurringScheduled(recurringScheduled)
                .recurringForecast(recurringForecast)
                .forecast(forecast)
                .budgetComparison(budgetComparison)
                .build();
    }

    // ========================= PRIVATE HELPERS =========================

    private YearMonth resolveYearMonth(Integer month, Integer year, LocalDate today) {
        int m = (month != null && month >= 1 && month <= 12) ? month : today.getMonthValue();
        int y = (year != null && year > 2000) ? year : today.getYear();
        return YearMonth.of(y, m);
    }

    private BigDecimal calculateCurrentBalance(UUID userId, UUID accountId) {
        List<Account> accounts = (accountId != null)
                ? accountRepository.findByIdAndUserId(accountId, userId)
                        .map(List::of)
                        .orElse(List.of())
                : accountRepository.findAllByUserId(userId);

        return accounts.stream()
                .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CashflowActual calculateActual(UUID userId, LocalDateTime start, LocalDateTime end) {
        List<Object[]> rows = transactionRepository.sumTotalIncomeAndExpense(userId, start, end);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;

        if (!rows.isEmpty() && rows.get(0) != null) {
            Object[] row = rows.get(0);
            if (row[0] != null) income = (BigDecimal) row[0];
            if (row[1] != null) expense = (BigDecimal) row[1];
        }

        return CashflowActual.builder()
                .income(income)
                .expense(expense)
                .net(income.subtract(expense))
                .build();
    }

    /**
     * Tính tất cả các lần xuất hiện của một RecurringTransaction trong khoảng [from, to].
     * Dùng vòng lặp bắt đầu từ nextExecutionDate.
     */
    private List<LocalDate> getOccurrencesInRange(RecurringTransaction rt, LocalDate from, LocalDate to) {
        List<LocalDate> result = new ArrayList<>();

        LocalDate startDate = rt.getNextExecutionDate();
        if (startDate == null) return result;

        LocalDate endDate = (rt.getEndDate() != null && rt.getEndDate().isBefore(to))
                ? rt.getEndDate()
                : to;

        // Nếu nextExecutionDate đã qua "from", tính ngược để tìm lần đầu tiên >= from
        LocalDate candidate = startDate;

        // Nếu candidate > to → không có occurrence nào
        if (candidate.isAfter(to)) return result;

        // Nếu candidate < from → advance đến lần >= from
        while (candidate.isBefore(from)) {
            candidate = advanceDate(candidate, rt.getRecurrenceType(), rt.getRecurrenceInterval());
            if (candidate.isAfter(endDate)) return result;
        }

        // Collect all occurrences in [from, to]
        while (!candidate.isAfter(endDate)) {
            result.add(candidate);
            candidate = advanceDate(candidate, rt.getRecurrenceType(), rt.getRecurrenceInterval());
        }

        return result;
    }

    private LocalDate advanceDate(LocalDate current, int recurrenceType, int interval) {
        return switch (recurrenceType) {
            case RecurrenceType.DAILY -> current.plusDays(interval);
            case RecurrenceType.WEEKLY -> current.plusWeeks(interval);
            case RecurrenceType.MONTHLY -> current.plusMonths(interval);
            case RecurrenceType.YEARLY -> current.plusYears(interval);
            default -> current.plusMonths(1);
        };
    }

    private String computeWarningLevel(BigDecimal projectedEndBalance, BigDecimal currentBalance) {
        if (projectedEndBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return "danger";
        }
        if (currentBalance.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = projectedEndBalance.divide(currentBalance, 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(new BigDecimal("0.20")) <= 0) {
                return "caution";
            }
        }
        return "ok";
    }

    private List<BudgetForecastItem> buildBudgetComparison(
            UUID userId, int month, int year, int daysElapsed, int totalDays) {

        List<Budget> budgets = budgetRepository.findAllWithTagsByUserIdAndMonthAndYear(userId, month, year);
        if (budgets.isEmpty()) return List.of();

        // Lấy chi tiêu theo danh mục trong tháng
        List<Object[]> categorySums = transactionRepository.sumSpendingGroupedByCategory(userId, month, year);
        Map<Integer, BigDecimal> spendingMap = new HashMap<>();
        for (Object[] row : categorySums) {
            if (row[0] != null && row[1] != null) {
                spendingMap.put(((Number) row[0]).intValue(), (BigDecimal) row[1]);
            }
        }

        List<BudgetForecastItem> result = new ArrayList<>();
        for (Budget budget : budgets) {
            BigDecimal spent = spendingMap.getOrDefault(budget.getCategory(), BigDecimal.ZERO);
            BigDecimal limit = budget.getAmount() != null ? budget.getAmount() : BigDecimal.ZERO;

            // Dự kiến chi đến cuối tháng: extrapolate theo tốc độ hiện tại
            BigDecimal forecasted = BigDecimal.ZERO;
            if (daysElapsed > 0 && totalDays > 0) {
                forecasted = spent
                        .multiply(BigDecimal.valueOf(totalDays))
                        .divide(BigDecimal.valueOf(daysElapsed), 0, RoundingMode.CEILING);
            }

            result.add(BudgetForecastItem.builder()
                    .budgetId(budget.getId().toString())
                    .category(budget.getCategory())
                    .categoryName(Category.getName(budget.getCategory()))
                    .limit(limit)
                    .spent(spent)
                    .forecasted(forecasted)
                    .willExceed(forecasted.compareTo(limit) > 0)
                    .build());
        }

        return result;
    }
}
