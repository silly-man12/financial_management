package com.example.financial_management.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.example.financial_management.model.report.response.CategoryDistribution;
import com.example.financial_management.util.DateTimeUtils;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.financial_management.constant.Category;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.mapper.TransactionMapper;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.report.request.CategoryReportRequest;
import com.example.financial_management.model.report.request.ReportRequest;
import com.example.financial_management.model.report.request.MonthlyReportRequest;
import com.example.financial_management.model.report.request.SummaryReportRequest;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;

import com.example.financial_management.model.report.response.AccountFlowResponse;
import com.example.financial_management.model.report.response.AnalyticsChartPoint;
import com.example.financial_management.model.report.response.AnalyticsKpiResponse;
import com.example.financial_management.model.report.response.AnalyticsReportResponse;
import com.example.financial_management.model.report.response.CategoryDistributionResponse;
import com.example.financial_management.model.report.response.TopExpenseResponse;
import com.example.financial_management.model.report.response.AccountSummary;
import com.example.financial_management.model.report.response.CategoryReportItem;
import com.example.financial_management.model.report.response.CategoryReportResponse;
import com.example.financial_management.model.report.response.CompareReportResponse;
import com.example.financial_management.model.report.response.DailyReportResponse;
import com.example.financial_management.model.report.response.DailyReportResponseItem;
import com.example.financial_management.model.report.response.DistributionSummary;
import com.example.financial_management.model.report.response.MonthlyReportResponse;
import com.example.financial_management.model.report.response.MonthlyReportResponseItem;
import com.example.financial_management.model.report.response.SummaryReportResponse;
import com.example.financial_management.model.transaction.TransactionResponse;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {
        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;
        private final TransactionMapper transactionMapper;
        private final UserService userService;
        private final CurrencyExchangeService currencyExchangeService;
        private final PdfReportService pdfReportService;

        private TransactionResponse toEnrichedTransaction(Transaction transaction) {
                TransactionResponse response = transactionMapper.toResponse(transaction);
                if (response != null) {
                        response.setExchangeRate(currencyExchangeService.getCurrentRate());
                        response.setAmountUsd(currencyExchangeService.calculateUsd(response.getAmount(),
                                        response.getCurrency()));
                }
                return response;
        }

        public List<TransactionResponse> getSummaryByDataRange(Auth auth, String from, String to) {
                LocalDateTime[] dateRange = DateTimeUtils.resolveDateRange(null, from, to);
                LocalDateTime startDate = dateRange[0];
                LocalDateTime endDate = dateRange[1];

                List<Transaction> transactions = transactionRepository.findAllByUserIdAndCreatedAtBetween(
                                auth.getUUID(), startDate, endDate);

                List<TransactionResponse> response = List.copyOf(transactions.stream()
                                .map(this::toEnrichedTransaction)
                                .toList());

                return response;
        }

        public SummaryReportResponse getSummary(SummaryReportRequest request, Auth auth) {
                User user = getUser(auth);

                validateAccountAccess(auth, request.getAccountId());

                // Convert LocalDate -> LocalDateTime
                LocalDateTime fromDateTime = request.getFrom() != null
                                ? request.getFrom().atStartOfDay()
                                : LocalDate.MIN.atStartOfDay();

                LocalDateTime toDateTime = request.getTo() != null
                                ? request.getTo().atTime(LocalTime.MAX)
                                : LocalDateTime.now();

                BigDecimal income = transactionRepository
                                .sumAmount(user.getId(), fromDateTime, toDateTime, request.getAccountId(),
                                                TransactionType.INCOME)
                                .orElse(BigDecimal.ZERO);

                BigDecimal expense = transactionRepository
                                .sumAmount(user.getId(), fromDateTime, toDateTime, request.getAccountId(),
                                                TransactionType.EXPENSE)
                                .orElse(BigDecimal.ZERO);

                BigDecimal netBalance = income.subtract(expense);

                SummaryReportResponse response = new SummaryReportResponse();
                response.setIncome(income);
                response.setExpense(expense);
                response.setNetBalance(netBalance);

                return response;
        }

        public DailyReportResponse getDailyReport(ReportRequest request, Auth auth) {
                User user = getUser(auth);

                validateAccountAccess(auth, request.getAccountId());

                YearMonth month = DateTimeUtils.parseYearMonth(request.getMonth());
                String monthFormatted = month.format(DateTimeFormatter.ofPattern("MM-yyyy"));
                LocalDateTime start = month.atDay(1).atStartOfDay();
                LocalDateTime end = month.atEndOfMonth().atTime(LocalTime.MAX);

                List<Object[]> rows = transactionRepository.sumDaily(
                                user.getId(), start, end, request.getAccountId());

                List<DailyReportResponseItem> items = rows.stream()
                                .map(row -> {
                                        UUID accountId = null;
                                        if (row[1] != null) {
                                                if (row[1] instanceof UUID) {
                                                        accountId = (UUID) row[1];
                                                } else {
                                                        accountId = UUID.fromString(row[1].toString());
                                                }
                                        }
                                        LocalDate date = row[0] instanceof java.sql.Date
                                                        ? ((java.sql.Date) row[0]).toLocalDate()
                                                        : LocalDate.parse(row[0].toString());
                                        return new DailyReportResponseItem(
                                                        date,
                                                        accountId,
                                                        (BigDecimal) row[2],
                                                        (BigDecimal) row[3]);
                                })
                                .toList();

                // Tính tổng income và expense
                BigDecimal totalIncome = items.stream()
                                .map(DailyReportResponseItem::getIncome)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalExpense = items.stream()
                                .map(DailyReportResponseItem::getExpense)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal net = totalIncome.subtract(totalExpense);

                DailyReportResponse response = new DailyReportResponse();
                response.setMonth(monthFormatted);
                response.setTotalIncome(totalIncome);
                response.setTotalExpense(totalExpense);
                response.setNet(net);
                response.setItems(items);

                return response;
        }

        public MonthlyReportResponse getMonthlyReport(MonthlyReportRequest request, Auth auth) {
                User user = getUser(auth);

                validateAccountAccess(auth, request.getAccountId());

                List<MonthlyReportResponseItem> items = transactionRepository.sumMonthly(
                                user.getId(), request.getYear(), request.getAccountId());

                BigDecimal income = items.stream().map(MonthlyReportResponseItem::getIncome).reduce(BigDecimal.ZERO,
                                BigDecimal::add);
                BigDecimal expense = items.stream().map(MonthlyReportResponseItem::getExpense).reduce(BigDecimal.ZERO,
                                BigDecimal::add);
                BigDecimal net = income.subtract(expense);

                MonthlyReportResponse response = new MonthlyReportResponse();
                response.setYear(request.getYear());
                response.setNet(net);
                response.setItems(items);

                return response;
        }

        public CategoryReportResponse getCategoryReport(CategoryReportRequest request, Auth auth) {
                User user = getUser(auth);

                validateAccountAccess(auth, request.getAccountId());

                // Query
                List<CategoryReportItem> items = transactionRepository.sumByCategory(
                                user.getId(),
                                request.getAccountId(),
                                request.getFromDate() != null ? request.getFromDate().atStartOfDay() : null,
                                request.getToDate() != null ? request.getToDate().atTime(LocalTime.MAX) : null);

                // Tổng chi / thu
                BigDecimal totalExpense = items.stream()
                                .map(CategoryReportItem::getExpense)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalIncome = items.stream()
                                .map(CategoryReportItem::getIncome)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Tính % trên chi tiêu
                for (CategoryReportItem item : items) {
                        if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
                                double expensePercentage = item.getExpense()
                                                .divide(totalExpense, 4, RoundingMode.HALF_UP)
                                                .multiply(BigDecimal.valueOf(100))
                                                .doubleValue();
                                item.setExpensePercentage(expensePercentage);
                        } else {
                                item.setExpensePercentage(0.0);
                        }

                        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
                                double incomePercent = item.getIncome()
                                                .divide(totalIncome, 4, RoundingMode.HALF_UP)
                                                .multiply(BigDecimal.valueOf(100))
                                                .doubleValue();
                                item.setIncomePercentage(incomePercent);
                        } else {
                                item.setIncomePercentage(0.0);
                        }

                        // Xác định transaction type dựa vào giá trị
                        if (item.getExpense().compareTo(BigDecimal.ZERO) > 0) {
                                item.setTransactionTypeName("Expense");
                        } else if (item.getIncome().compareTo(BigDecimal.ZERO) > 0) {
                                item.setTransactionTypeName("Income");
                        } else {
                                item.setTransactionTypeName("Unknown");
                        }

                        // Gán tên category
                        item.setCategoryName(Category.getName(item.getCategory()));
                }

                // Response
                CategoryReportResponse response = new CategoryReportResponse();
                response.setItems(items);
                response.setTotalExpense(totalExpense);
                response.setTotalIncome(totalIncome);
                return response;
        }

        public CompareReportResponse getCompareReport(ReportRequest request, Auth auth) {
                User user = getUser(auth);
                validateAccountAccess(auth, request.getAccountId());

                YearMonth thisMonth = DateTimeUtils.parseYearMonth(request.getMonth());
                String monthFormatted = thisMonth.format(DateTimeFormatter.ofPattern("MM-yyyy"));
                YearMonth lastMonth = thisMonth.minusMonths(1);

                // Range tháng này
                LocalDateTime startThisMonth = thisMonth.atDay(1).atStartOfDay();
                LocalDateTime endThisMonth = thisMonth.atEndOfMonth().atTime(LocalTime.MAX);

                // Range tháng trước
                LocalDateTime startLastMonth = lastMonth.atDay(1).atStartOfDay();
                LocalDateTime endLastMonth = lastMonth.atEndOfMonth().atTime(LocalTime.MAX);

                // Query
                BigDecimal incomeThis = transactionRepository.sumAmount(user.getId(), startThisMonth, endThisMonth,
                                request.getAccountId(), TransactionType.INCOME).orElse(BigDecimal.ZERO);

                BigDecimal expenseThis = transactionRepository.sumAmount(user.getId(), startThisMonth, endThisMonth,
                                request.getAccountId(), TransactionType.EXPENSE).orElse(BigDecimal.ZERO);

                BigDecimal incomeLast = transactionRepository.sumAmount(user.getId(), startLastMonth, endLastMonth,
                                request.getAccountId(), TransactionType.INCOME).orElse(BigDecimal.ZERO);

                BigDecimal expenseLast = transactionRepository.sumAmount(user.getId(), startLastMonth, endLastMonth,
                                request.getAccountId(), TransactionType.EXPENSE).orElse(BigDecimal.ZERO);

                // Net
                BigDecimal netThis = incomeThis.subtract(expenseThis);
                BigDecimal netLast = incomeLast.subtract(expenseLast);

                // % thay đổi
                Double incomeChange = calcPercentChange(incomeThis, incomeLast);
                Double expenseChange = calcPercentChange(expenseThis, expenseLast);
                Double netChange = calcPercentChange(netThis, netLast);

                // Response
                CompareReportResponse response = new CompareReportResponse();
                response.setMonth(monthFormatted);

                response.setIncomeThisMonth(incomeThis);
                response.setExpenseThisMonth(expenseThis);
                response.setNetThisMonth(netThis);

                response.setIncomeLastMonth(incomeLast);
                response.setExpenseLastMonth(expenseLast);
                response.setNetLastMonth(netLast);

                response.setIncomeChangePercent(incomeChange);
                response.setExpenseChangePercent(expenseChange);
                response.setNetChangePercent(netChange);

                return response;
        }

        public ResponseEntity<byte[]> exportMonthlyReportByMonthPDF(ReportRequest request, Auth auth) {
                DailyReportResponse data = getDailyReport(request, auth);
                return pdfReportService.exportMonthlyReportByMonthPDF(request.getMonth(), data);
        }

        public ResponseEntity<byte[]> exportMonthlyReportByYearPDF(MonthlyReportRequest request, Auth auth) {
                MonthlyReportResponse data = getMonthlyReport(request, auth);
                return pdfReportService.exportMonthlyReportByYearPDF(request.getYear(), data);
        }

        public AccountSummary getReportByAccount(UUID accountId, Auth auth) {
                User user = getUser(auth);
                validateAccountAccess(auth, accountId);

                // Lấy thông tin account
                Account account = accountRepository.findByIdAndUserId(accountId, user.getId())
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Account not found"));

                // Tính toán số dư đầu kỳ và cuối kỳ
                BigDecimal endBalance = account.getBalance(); // Giả sử đây là số dư cuối kỳ

                // Tính tổng thu nhập và chi phí
                BigDecimal totalIncome = transactionRepository.sumAmountByType(user.getId(), accountId,
                                TransactionType.INCOME).orElse(BigDecimal.ZERO);
                BigDecimal totalExpense = transactionRepository.sumAmountByType(user.getId(), accountId,
                                TransactionType.EXPENSE).orElse(BigDecimal.ZERO);

                BigDecimal remaining = totalIncome.subtract(totalExpense);
                BigDecimal startBalance = endBalance.subtract(remaining);

                // Lấy lịch sử giao dịch
                List<TransactionResponse> balanceHistory = transactionRepository.findAllByAccountIdAndUserId(
                                accountId, user.getId()).stream()
                                .map(this::toEnrichedTransaction)
                                .toList();

                // Tạo response
                AccountSummary summary = new AccountSummary();
                summary.setStartBalance(startBalance);
                summary.setEndBalance(endBalance);
                summary.setTotalIncome(totalIncome);
                summary.setTotalExpense(totalExpense);
                summary.setBalanceHistory(balanceHistory);

                return summary;
        }

        public DistributionSummary getReportDistributionByAccount(UUID accountId, Auth auth) {
                User user = getUser(auth);
                validateAccountAccess(auth, accountId);

                // Lấy thông tin account
                Account account = accountRepository.findByIdAndUserId(accountId, user.getId())
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Account not found"));

                // Tính toán số dư đầu kỳ và cuối kỳ
                BigDecimal endBalance = account.getBalance(); // Giả sử đây là số dư cuối kỳ

                // Tính tổng thu nhập và chi phí
                BigDecimal totalIncome = transactionRepository.sumAmountByType(user.getId(), accountId,
                                TransactionType.INCOME).orElse(BigDecimal.ZERO);
                BigDecimal totalExpense = transactionRepository.sumAmountByType(user.getId(), accountId,
                                TransactionType.EXPENSE).orElse(BigDecimal.ZERO);

                BigDecimal remaining = totalIncome.subtract(totalExpense);
                BigDecimal startBalance = endBalance.subtract(remaining);

                // Lấy lịch sử giao dịch
                List<CategoryDistribution> incomeByCategory = transactionRepository.sumAmountByCategoryAndType(
                                user.getId(), accountId, TransactionType.INCOME);

                List<CategoryDistribution> expenseByCategory = transactionRepository.sumAmountByCategoryAndType(
                                user.getId(), accountId, TransactionType.EXPENSE);

                // Tạo response
                DistributionSummary summary = new DistributionSummary();
                summary.setStartBalance(startBalance);
                summary.setEndBalance(endBalance);
                summary.setTotalIncome(totalIncome);
                summary.setTotalExpense(totalExpense);
                summary.setIncomeByCategory(incomeByCategory);
                summary.setExpenseByCategory(expenseByCategory);

                return summary;
        }

        private Double calcPercentChange(BigDecimal current, BigDecimal previous) {
                if (previous.compareTo(BigDecimal.ZERO) == 0) {
                        return null; // hoặc 100% nếu bạn muốn coi là tăng toàn bộ
                }
                return current.subtract(previous)
                                .divide(previous, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .doubleValue();
        }

        private User getUser(Auth auth) {
                return userService.getAuthenticatedUser(auth);
        }

        private void validateAccountAccess(Auth auth, UUID accountId) {
                if (accountId == null) {
                        // Nếu null, cho phép xem tất cả account của user
                        return;
                }

                boolean hasAccess = accountRepository.existsByIdAndUserId(accountId, auth.getUUID());

                if (!hasAccess) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied for this account");
                }
        }

        public AnalyticsReportResponse getAnalyticsReport(Auth auth, String period, String startDateStr,
                        String endDateStr) {
                User user = getUser(auth);
                LocalDateTime[] dateRange = DateTimeUtils.resolveDateRange(period, startDateStr, endDateStr);
                LocalDateTime startDateTime = dateRange[0];
                LocalDateTime endDateTime = dateRange[1];

                long days = Math.max(1,
                                ChronoUnit.DAYS.between(startDateTime.toLocalDate(), endDateTime.toLocalDate()) + 1);
                LocalDateTime prevEndDateTime = startDateTime.minusSeconds(1);
                LocalDateTime prevStartDateTime = startDateTime.minusDays(days);

                // 1. Tối ưu: Lấy trực tiếp dữ liệu nhóm theo ngày từ SQL Server
                List<Object[]> dailyRows = transactionRepository.sumDailyAggregatedByUser(
                                user.getId(), startDateTime, endDateTime);

                Map<LocalDate, BigDecimal[]> dailyAggMap = new java.util.HashMap<>();
                BigDecimal totalIncome = BigDecimal.ZERO;
                BigDecimal totalExpense = BigDecimal.ZERO;

                for (Object[] row : dailyRows) {
                        LocalDate date;
                        if (row[0] instanceof java.sql.Date sqlDate) {
                                date = sqlDate.toLocalDate();
                        } else if (row[0] instanceof LocalDate localDate) {
                                date = localDate;
                        } else {
                                date = LocalDate.parse(row[0].toString());
                        }
                        BigDecimal inc = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                        BigDecimal exp = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
                        dailyAggMap.put(date, new BigDecimal[] { inc, exp });
                        totalIncome = totalIncome.add(inc);
                        totalExpense = totalExpense.add(exp);
                }

                BigDecimal netIncome = totalIncome.subtract(totalExpense);

                Double savingsRate = 0.0;
                if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
                        savingsRate = netIncome.divide(totalIncome, 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .doubleValue();
                }

                BigDecimal dailyAverage = totalExpense.divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP);
                BigDecimal forecastExpense = dailyAverage.multiply(BigDecimal.valueOf(days));

                // 2. Tối ưu: Lấy tổng thu chi kỳ trước trực tiếp từ SQL Server
                List<Object[]> prevTotals = transactionRepository.sumTotalIncomeAndExpense(
                                user.getId(), prevStartDateTime, prevEndDateTime);
                BigDecimal prevIncome = BigDecimal.ZERO;
                BigDecimal prevExpense = BigDecimal.ZERO;
                if (prevTotals != null && !prevTotals.isEmpty() && prevTotals.get(0) != null) {
                        Object[] pRow = prevTotals.get(0);
                        if (pRow[0] != null)
                                prevIncome = (BigDecimal) pRow[0];
                        if (pRow[1] != null)
                                prevExpense = (BigDecimal) pRow[1];
                }

                Double incomeGrowthRate = calcPercentChange(totalIncome, prevIncome);
                Double expenseGrowthRate = calcPercentChange(totalExpense, prevExpense);

                AnalyticsKpiResponse kpi = AnalyticsKpiResponse.builder()
                                .totalIncome(totalIncome)
                                .totalExpense(totalExpense)
                                .netIncome(netIncome)
                                .savingsRate(savingsRate)
                                .dailyAverage(dailyAverage)
                                .forecastExpense(forecastExpense)
                                .incomeGrowthRate(incomeGrowthRate)
                                .expenseGrowthRate(expenseGrowthRate)
                                .build();

                List<AnalyticsChartPoint> chart = new java.util.ArrayList<>();
                LocalDate cur = startDateTime.toLocalDate();
                LocalDate end = endDateTime.toLocalDate();
                DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("dd/MM");

                while (!cur.isAfter(end)) {
                        BigDecimal[] dayVals = dailyAggMap.get(cur);
                        BigDecimal dayIncome = dayVals != null ? dayVals[0] : BigDecimal.ZERO;
                        BigDecimal dayExpense = dayVals != null ? dayVals[1] : BigDecimal.ZERO;

                        chart.add(AnalyticsChartPoint.builder()
                                        .label(cur.format(labelFormatter))
                                        .date(cur.toString())
                                        .income(dayIncome)
                                        .expense(dayExpense)
                                        .net(dayIncome.subtract(dayExpense))
                                        .build());

                        cur = cur.plusDays(1);
                }

                return AnalyticsReportResponse.builder()
                                .kpi(kpi)
                                .chart(chart)
                                .build();
        }

        public List<CategoryDistributionResponse> getCategoryDistributionReport(
                        Auth auth, String startDateStr, String endDateStr, Integer type) {
                User user = getUser(auth);
                int txType = (type != null) ? type : TransactionType.EXPENSE;

                LocalDateTime[] dateRange = DateTimeUtils.resolveDateRange(null, startDateStr, endDateStr);
                LocalDateTime startDateTime = dateRange[0];
                LocalDateTime endDateTime = dateRange[1];

                long days = Math.max(1,
                                ChronoUnit.DAYS.between(startDateTime.toLocalDate(), endDateTime.toLocalDate()) + 1);
                LocalDateTime prevEndDateTime = startDateTime.minusSeconds(1);
                LocalDateTime prevStartDateTime = startDateTime.minusDays(days);

                // 1. Tối ưu: Lấy phân bổ danh mục kỳ hiện tại trực tiếp từ SQL Server
                List<Object[]> currentCatRows = transactionRepository.sumGroupedByCategoryAndType(
                                user.getId(), txType, startDateTime, endDateTime);

                // 2. Tối ưu: Lấy phân bổ danh mục kỳ trước trực tiếp từ SQL Server
                List<Object[]> prevCatRows = transactionRepository.sumGroupedByCategoryAndType(
                                user.getId(), txType, prevStartDateTime, prevEndDateTime);

                BigDecimal totalAmount = BigDecimal.ZERO;
                for (Object[] row : currentCatRows) {
                        if (row[1] != null) {
                                totalAmount = totalAmount.add((BigDecimal) row[1]);
                        }
                }

                Map<Integer, BigDecimal> prevCategorySumMap = new java.util.HashMap<>();
                for (Object[] row : prevCatRows) {
                        if (row[0] != null && row[1] != null) {
                                int cat = ((Number) row[0]).intValue();
                                prevCategorySumMap.put(cat, (BigDecimal) row[1]);
                        }
                }

                List<CategoryDistributionResponse> result = new java.util.ArrayList<>();

                for (Object[] row : currentCatRows) {
                        int categoryId = ((Number) row[0]).intValue();
                        BigDecimal catTotal = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                        int txCount = row[2] != null ? ((Number) row[2]).intValue() : 0;

                        Double percentage = 0.0;
                        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                                percentage = catTotal.divide(totalAmount, 4, RoundingMode.HALF_UP)
                                                .multiply(BigDecimal.valueOf(100))
                                                .doubleValue();
                        }

                        BigDecimal prevCatTotal = prevCategorySumMap.getOrDefault(categoryId, BigDecimal.ZERO);
                        Double change = calcPercentChange(catTotal, prevCatTotal);

                        result.add(CategoryDistributionResponse.builder()
                                        .category(categoryId)
                                        .categoryName(Category.getName(categoryId))
                                        .total(catTotal)
                                        .percentage(percentage)
                                        .transactionCount(txCount)
                                        .changeVsPreviousPeriod(change)
                                        .build());
                }

                result.sort((a, b) -> b.getTotal().compareTo(a.getTotal()));
                return result;
        }

        public List<AccountFlowResponse> getAccountFlowReport(Auth auth, String startDateStr, String endDateStr) {
                User user = getUser(auth);
                LocalDateTime[] dateRange = DateTimeUtils.resolveDateRange(null, startDateStr, endDateStr);
                LocalDateTime startDateTime = dateRange[0];
                LocalDateTime endDateTime = dateRange[1];

                List<Account> accounts = accountRepository.findAllByUserId(user.getId());

                // 1. Tối ưu: Lấy dòng tiền vào/ra gom nhóm theo account_id trực tiếp từ SQL
                // Server
                List<Object[]> flowRows = transactionRepository.sumFlowByAccount(
                                user.getId(), startDateTime, endDateTime);

                Map<UUID, Object[]> flowMap = new java.util.HashMap<>();
                for (Object[] row : flowRows) {
                        if (row[0] != null) {
                                UUID accId;
                                if (row[0] instanceof UUID u) {
                                        accId = u;
                                } else {
                                        accId = UUID.fromString(row[0].toString());
                                }
                                flowMap.put(accId, row);
                        }
                }

                List<AccountFlowResponse> result = new java.util.ArrayList<>();

                for (Account account : accounts) {
                        Object[] accFlow = flowMap.get(account.getId());

                        BigDecimal inflow = (accFlow != null && accFlow[1] != null)
                                        ? (BigDecimal) accFlow[1]
                                        : BigDecimal.ZERO;

                        BigDecimal outflow = (accFlow != null && accFlow[2] != null)
                                        ? (BigDecimal) accFlow[2]
                                        : BigDecimal.ZERO;

                        BigDecimal netFlow = inflow.subtract(outflow);

                        result.add(AccountFlowResponse.builder()
                                        .accountId(account.getId())
                                        .accountName(account.getName())
                                        .accountType(account.getType())
                                        .inflow(inflow)
                                        .outflow(outflow)
                                        .netFlow(netFlow)
                                        .currentBalance(account.getBalance())
                                        .build());
                }

                result.sort((a, b) -> b.getInflow().add(b.getOutflow()).compareTo(a.getInflow().add(a.getOutflow())));
                return result;
        }

        public List<TopExpenseResponse> getTopExpensesReport(
                        Auth auth, String startDateStr, String endDateStr, Integer limit) {
                User user = getUser(auth);
                LocalDateTime[] dateRange = DateTimeUtils.resolveDateRange(null, startDateStr, endDateStr);
                LocalDateTime startDateTime = dateRange[0];
                LocalDateTime endDateTime = dateRange[1];

                int queryLimit = (limit != null && limit > 0) ? limit : 5;
                List<Transaction> topTransactions = transactionRepository.findTopExpenses(
                                user.getId(),
                                TransactionType.EXPENSE,
                                startDateTime,
                                endDateTime,
                                PageRequest.of(0, queryLimit));

                Map<UUID, String> accountNameMap = accountRepository.findAllByUserId(user.getId()).stream()
                                .collect(Collectors.toMap(Account::getId, Account::getName, (a, b) -> a));

                return topTransactions.stream()
                                .map(t -> TopExpenseResponse.builder()
                                                .id(t.getId())
                                                .description(t.getDescription())
                                                .amount(t.getAmount())
                                                .category(t.getCategory())
                                                .categoryName(Category.getName(t.getCategory()))
                                                .accountId(t.getAccountId())
                                                .accountName(t.getAccountId() != null
                                                                ? accountNameMap.getOrDefault(t.getAccountId(),
                                                                                "Không xác định")
                                                                : "Không xác định")
                                                .createdAt(t.getCreatedAt())
                                                .build())
                                .toList();
        }

}
