package com.example.financial_management.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.financial_management.constant.Status;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.Budget;
import com.example.financial_management.entity.Debt;
import com.example.financial_management.entity.DebtPayment;
import com.example.financial_management.entity.RecurringTransaction;
import com.example.financial_management.entity.SavingGoal;
import com.example.financial_management.entity.SavingGoalContribution;
import com.example.financial_management.entity.Tag;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.backup.BackupFileData;
import com.example.financial_management.model.backup.BackupImportResponse;
import com.example.financial_management.model.backup.BackupImportResponse.ImportedCounts;
import com.example.financial_management.model.backup.BackupValidateResponse;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.BudgetRepository;
import com.example.financial_management.repository.DebtPaymentRepository;
import com.example.financial_management.repository.DebtRepository;
import com.example.financial_management.repository.RecurringTransactionRepository;
import com.example.financial_management.repository.SavingGoalContributionRepository;
import com.example.financial_management.repository.SavingGoalRepository;
import com.example.financial_management.repository.TagRepository;
import com.example.financial_management.repository.TransactionRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    /** Phiên bản schema backup hiện tại — tăng khi cấu trúc thay đổi */
    public static final String SCHEMA_VERSION = "1.0";

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final UserService userService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final BudgetRepository budgetRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final SavingGoalContributionRepository savingGoalContributionRepository;
    private final TagRepository tagRepository;
    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;

    // ========================= EXPORT =========================

    /**
     * Xuất toàn bộ dữ liệu người dùng thành file JSON.
     * Response trả về blob dạng application/octet-stream.
     */
    public ResponseEntity<byte[]> exportBackup(Auth auth, boolean includeImages) {
        User user = userService.getAuthenticatedUser(auth);
        UUID userId = user.getId();

        log.info("Bắt đầu export backup cho user={}", userId);

        BackupFileData backup = buildBackupData(user, userId, includeImages);

        byte[] jsonBytes = serializeToJson(backup);

        String filename = String.format("backup_%s_%s.json",
                userId.toString().substring(0, 8),
                LocalDateTime.now().toString().replace(":", "-").substring(0, 16));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(jsonBytes.length);

        log.info("Export backup hoàn tất: {} bytes, file={}", jsonBytes.length, filename);
        return new ResponseEntity<>(jsonBytes, headers, HttpStatus.OK);
    }

    // ========================= VALIDATE =========================

    /**
     * Validate file backup mà không thực sự import — kiểm tra format, version,
     * thống kê record.
     */
    public BackupValidateResponse validateBackup(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return BackupValidateResponse.builder()
                    .valid(false)
                    .errorMessage("File không hợp lệ hoặc rỗng")
                    .build();
        }

        try {
            BackupFileData data = parseBackupFile(file);

            if (data.getSchemaVersion() == null) {
                return BackupValidateResponse.builder()
                        .valid(false)
                        .errorMessage("File backup không có trường schemaVersion")
                        .build();
            }

            if (!SCHEMA_VERSION.equals(data.getSchemaVersion())) {
                return BackupValidateResponse.builder()
                        .valid(false)
                        .schemaVersion(data.getSchemaVersion())
                        .errorMessage("Phiên bản schema không tương thích: file=" + data.getSchemaVersion()
                                + ", server=" + SCHEMA_VERSION)
                        .build();
            }

            ImportedCounts counts = countRecordsInFile(data);

            return BackupValidateResponse.builder()
                    .valid(true)
                    .schemaVersion(data.getSchemaVersion())
                    .userEmail(data.getUserEmail())
                    .recordCounts(counts)
                    .build();

        } catch (Exception e) {
            log.warn("Validate backup file thất bại: {}", e.getMessage());
            return BackupValidateResponse.builder()
                    .valid(false)
                    .errorMessage("Không thể đọc file backup: " + e.getMessage())
                    .build();
        }
    }

    // ========================= IMPORT =========================

    /**
     * Import (restore) dữ liệu từ file backup.
     *
     * @param file file JSON backup
     * @param mode "merge" = giữ data cũ + thêm mới (bỏ qua nếu trùng ID);
     *             "overwrite" = xoá toàn bộ data cũ rồi restore (CỰC KỲ NGUY HIỂM)
     */
    @Transactional
    public BackupImportResponse importBackup(Auth auth, MultipartFile file, String mode) {
        User user = userService.getAuthenticatedUser(auth);
        UUID userId = user.getId();

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File backup không được để trống");
        }

        String importMode = (mode != null && mode.equalsIgnoreCase("overwrite")) ? "overwrite" : "merge";
        log.info("Bắt đầu import backup cho user={}, mode={}", userId, importMode);

        BackupFileData data;
        try {
            data = parseBackupFile(file);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không thể đọc file backup: " + e.getMessage());
        }

        // Kiểm tra schema version
        if (!SCHEMA_VERSION.equals(data.getSchemaVersion())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Phiên bản backup không tương thích. Vui lòng validate file trước khi import.");
        }

        List<String> errorMessages = new ArrayList<>();

        // Mode overwrite: xoá toàn bộ data cũ trước khi import
        if ("overwrite".equals(importMode)) {
            log.warn("Mode OVERWRITE: xoá toàn bộ dữ liệu của user={}", userId);
            deleteAllUserData(userId);
        }
        entityManager.clear();

        // Thực hiện import từng entity qua JdbcTemplate
        int accounts = importAccounts(data.getAccounts(), userId, importMode, errorMessages);
        int tags = importTags(data.getTags(), userId, importMode, errorMessages);
        int transactions = importTransactions(data.getTransactions(), userId, importMode, errorMessages);
        int recurringTransactions = importRecurringTransactions(
                data.getRecurringTransactions(), userId, importMode, errorMessages);
        int budgets = importBudgets(data.getBudgets(), userId, importMode, errorMessages);
        int savingGoals = importSavingGoals(data.getSavingGoals(), userId, importMode, errorMessages);
        int savingGoalContributions = importSavingGoalContributions(
                data.getSavingGoalContributions(), userId, importMode, errorMessages);
        int debts = importDebts(data.getDebts(), userId, importMode, errorMessages);
        int debtPayments = importDebtPayments(data.getDebtPayments(), userId, importMode, errorMessages);

        entityManager.clear();

        String status = errorMessages.isEmpty() ? "success" : "partial";

        log.info("Import backup hoàn tất cho user={}: accounts={}, transactions={}, tags={}",
                userId, accounts, transactions, tags);

        return BackupImportResponse.builder()
                .status(status)
                .imported(ImportedCounts.builder()
                        .accounts(accounts)
                        .transactions(transactions)
                        .recurringTransactions(recurringTransactions)
                        .budgets(budgets)
                        .savingGoals(savingGoals)
                        .savingGoalContributions(savingGoalContributions)
                        .tags(tags)
                        .debts(debts)
                        .debtPayments(debtPayments)
                        .build())
                .skipped(0)
                .errors(errorMessages.size())
                .errorMessages(errorMessages)
                .build();
    }

    // ========================= PRIVATE: BUILD BACKUP =========================

    private BackupFileData buildBackupData(User user, UUID userId, boolean includeImages) {
        // Accounts
        List<Account> accounts = accountRepository.findAllByUserId(userId);
        List<Map<String, Object>> accountMaps = accounts.stream()
                .map(a -> entityToMap(a, includeImages))
                .toList();

        // Transactions
        List<Transaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> transactionMaps = transactions.stream()
                .map(t -> {
                    Map<String, Object> m = entityToMap(t, includeImages);
                    if (!includeImages)
                        m.remove("imagePath");
                    return m;
                })
                .toList();

        // Recurring transactions
        List<RecurringTransaction> recurringList = recurringTransactionRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> recurringMaps = recurringList.stream()
                .map(r -> entityToMap(r, includeImages))
                .toList();

        // Budgets
        List<Budget> budgets = budgetRepository.findAllByUserId(userId);
        List<Map<String, Object>> budgetMaps = budgets.stream()
                .map(b -> entityToMap(b, includeImages))
                .toList();

        // Saving goals
        List<SavingGoal> savingGoals = savingGoalRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> savingGoalMaps = savingGoals.stream()
                .map(s -> entityToMap(s, includeImages))
                .toList();

        // Saving goal contributions
        List<SavingGoalContribution> contributions = new ArrayList<>();
        for (SavingGoal sg : savingGoals) {
            contributions.addAll(
                    savingGoalContributionRepository.findAllBySavingGoalIdOrderByContributionDateDesc(sg.getId()));
        }
        List<Map<String, Object>> contributionMaps = contributions.stream()
                .map(c -> entityToMap(c, includeImages))
                .toList();

        // Tags
        List<Tag> tags = tagRepository.findAllByUserIdOrderByNameAsc(userId);
        List<Map<String, Object>> tagMaps = tags.stream()
                .map(t -> entityToMap(t, includeImages))
                .toList();

        // Debts
        List<Debt> debts = debtRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> debtMaps = debts.stream()
                .map(d -> entityToMap(d, includeImages))
                .toList();

        // Debt payments
        List<DebtPayment> debtPayments = new ArrayList<>();
        for (Debt debt : debts) {
            debtPayments.addAll(
                    debtPaymentRepository.findAllByDebtIdOrderByPaymentDateDesc(debt.getId()));
        }
        List<Map<String, Object>> debtPaymentMaps = debtPayments.stream()
                .map(dp -> entityToMap(dp, includeImages))
                .toList();

        return BackupFileData.builder()
                .schemaVersion(SCHEMA_VERSION)
                .exportedAt(LocalDateTime.now())
                .userId(userId.toString())
                .userEmail(user.getEmail())
                .accounts(accountMaps)
                .transactions(transactionMaps)
                .recurringTransactions(recurringMaps)
                .budgets(budgetMaps)
                .savingGoals(savingGoalMaps)
                .savingGoalContributions(contributionMaps)
                .tags(tagMaps)
                .debts(debtMaps)
                .debtPayments(debtPaymentMaps)
                .build();
    }

    // ========================= PRIVATE: IMPORT HELPERS =========================

    /**
     * Xoá toàn bộ dữ liệu của user (dùng cho mode overwrite).
     * Thứ tự xoá phải đảm bảo các bảng con được xoá trước bảng cha.
     */
    private void deleteAllUserData(UUID userId) {
        // 0. Gỡ bỏ các liên kết khoá ngoại chéo / tự tham chiếu để tránh dính FK
        // constraint
        try {
            jdbcTemplate.update("UPDATE transactions SET transfer_id = NULL WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.warn("Lỗi gỡ transfer_id trong transactions: {}", e.getMessage());
        }

        try {
            jdbcTemplate.update("UPDATE debts SET transaction_id = NULL WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.warn("Lỗi gỡ transaction_id trong debts: {}", e.getMessage());
        }

        try {
            jdbcTemplate.update("""
                        UPDATE debt_payments SET transaction_id = NULL
                        WHERE debt_id IN (SELECT id FROM debts WHERE user_id = ?)
                    """, userId);
        } catch (Exception e) {
            log.warn("Lỗi gỡ transaction_id trong debt_payments: {}", e.getMessage());
        }

        try {
            jdbcTemplate.update("""
                        UPDATE saving_goal_contributions SET transaction_id = NULL
                        WHERE saving_goal_id IN (SELECT id FROM saving_goals WHERE user_id = ?)
                    """, userId);
        } catch (Exception e) {
            log.warn("Lỗi gỡ transaction_id trong saving_goal_contributions: {}", e.getMessage());
        }

        // 1. Xóa bảng trung gian ManyToMany
        try {
            jdbcTemplate.update("""
                        DELETE FROM transaction_tags
                        WHERE transaction_id IN (SELECT id FROM transactions WHERE user_id = ?)
                           OR tag_id IN (SELECT id FROM tags WHERE user_id = ?)
                    """, userId, userId);
        } catch (Exception e) {
            log.warn("Lỗi xóa transaction_tags: {}", e.getMessage());
        }

        try {
            jdbcTemplate.update("""
                        DELETE FROM budget_tags
                        WHERE budget_id IN (SELECT id FROM budgets WHERE user_id = ?)
                           OR tag_id IN (SELECT id FROM tags WHERE user_id = ?)
                    """, userId, userId);
        } catch (Exception e) {
            log.warn("Lỗi xóa budget_tags: {}", e.getMessage());
        }

        // 2. Xóa debt_payments và debts
        try {
            jdbcTemplate.update("""
                        DELETE FROM debt_payments
                        WHERE debt_id IN (SELECT id FROM debts WHERE user_id = ?)
                    """, userId);
        } catch (Exception e) {
            log.warn("Lỗi xóa debt_payments: {}", e.getMessage());
        }

        try {
            jdbcTemplate.update("DELETE FROM debts WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.warn("Lỗi xóa debts: {}", e.getMessage());
        }

        // 3. Xóa saving_goal_contributions và saving_goals
        try {
            jdbcTemplate.update("""
                        DELETE FROM saving_goal_contributions
                        WHERE saving_goal_id IN (SELECT id FROM saving_goals WHERE user_id = ?)
                    """, userId);
        } catch (Exception e) {
            log.warn("Lỗi xóa saving_goal_contributions: {}", e.getMessage());
        }

        try {
            jdbcTemplate.update("DELETE FROM saving_goals WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.warn("Lỗi xóa saving_goals: {}", e.getMessage());
        }

        // 4. Xóa budgets, recurring_transactions
        try {
            jdbcTemplate.update("DELETE FROM budgets WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.warn("Lỗi xóa budgets: {}", e.getMessage());
        }

        try {
            jdbcTemplate.update("DELETE FROM recurring_transactions WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.warn("Lỗi xóa recurring_transactions: {}", e.getMessage());
        }

        // 5. Xóa transactions và tags
        try {
            jdbcTemplate.update("DELETE FROM transactions WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.warn("Lỗi xóa transactions: {}", e.getMessage());
        }

        try {
            jdbcTemplate.update("DELETE FROM tags WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.warn("Lỗi xóa tags: {}", e.getMessage());
        }

        // 6. Xóa accounts
        try {
            jdbcTemplate.update("DELETE FROM accounts WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.warn("Lỗi xóa accounts: {}", e.getMessage());
        }

        log.info("Đã xoá toàn bộ dữ liệu của user={} qua JdbcTemplate", userId);
    }

    private int importAccounts(List<Map<String, Object>> list, UUID userId,
            String mode, List<String> errors) {
        if (list == null || list.isEmpty())
            return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            try {
                String idStr = getString(map, "id");
                if (idStr == null)
                    continue;
                UUID id = UUID.fromString(idStr);

                String name = getString(map, "name");
                BigDecimal balance = getBigDecimal(map, "balance");
                int type = getInt(map, "type");
                int currency = getInt(map, "currency");
                int status = getInt(map, "status", Status.ACTIVE);
                String description = getString(map, "description");
                Timestamp createdAt = toSqlTimestamp(getLocalDateTime(map, "createdAt"));
                Timestamp updatedAt = toSqlTimestamp(getLocalDateTime(map, "updatedAt"));

                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM accounts WHERE id = ?", Integer.class, id);

                if (exists != null && exists > 0) {
                    if ("merge".equals(mode)) {
                        jdbcTemplate.update(
                                """
                                            UPDATE accounts
                                            SET name = ?, balance = ?, type = ?, currency = ?, status = ?, description = ?, created_at = ?, updated_at = ?
                                            WHERE id = ?
                                        """,
                                name, balance, type, currency, status, description, createdAt, updatedAt, id);
                        count++;
                    }
                } else {
                    jdbcTemplate.update(
                            """
                                        INSERT INTO accounts (id, user_id, name, balance, type, currency, status, description, version, created_at, updated_at)
                                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                                    """,
                            id, userId, name, balance, type, currency, status, description, createdAt, updatedAt);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Lỗi import account: {}", e.getMessage(), e);
                errors.add("Account: " + e.getMessage());
            }
        }
        return count;
    }

    private int importTags(List<Map<String, Object>> list, UUID userId,
            String mode, List<String> errors) {
        if (list == null || list.isEmpty())
            return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            try {
                String idStr = getString(map, "id");
                if (idStr == null)
                    continue;
                UUID id = UUID.fromString(idStr);

                String name = getString(map, "name");
                String color = getString(map, "color") != null ? getString(map, "color") : "#6366f1";
                Timestamp createdAt = toSqlTimestamp(getLocalDateTime(map, "createdAt"));
                Timestamp updatedAt = toSqlTimestamp(getLocalDateTime(map, "updatedAt"));

                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM tags WHERE id = ?", Integer.class, id);

                if (exists != null && exists > 0) {
                    if ("merge".equals(mode)) {
                        jdbcTemplate.update("""
                                    UPDATE tags
                                    SET name = ?, color = ?, created_at = ?, updated_at = ?
                                    WHERE id = ?
                                """, name, color, createdAt, updatedAt, id);
                        count++;
                    }
                } else {
                    jdbcTemplate.update("""
                                INSERT INTO tags (id, user_id, name, color, created_at, updated_at)
                                VALUES (?, ?, ?, ?, ?, ?)
                            """, id, userId, name, color, createdAt, updatedAt);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Lỗi import tag: {}", e.getMessage(), e);
                errors.add("Tag: " + e.getMessage());
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int importTransactions(List<Map<String, Object>> list, UUID userId,
            String mode, List<String> errors) {
        if (list == null || list.isEmpty())
            return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            try {
                String idStr = getString(map, "id");
                if (idStr == null)
                    continue;
                UUID id = UUID.fromString(idStr);

                String accIdStr = getString(map, "accountId");
                UUID accountId = accIdStr != null ? UUID.fromString(accIdStr) : null;
                BigDecimal amount = getBigDecimal(map, "amount");
                int type = getInt(map, "type");
                int category = getInt(map, "category");
                int currency = getInt(map, "currency");
                String description = getString(map, "description");
                boolean haveImage = getBoolean(map, "haveImage");
                String imagePath = getString(map, "imagePath");
                String transferIdStr = getString(map, "transferId");
                UUID transferId = transferIdStr != null ? UUID.fromString(transferIdStr) : null;
                Timestamp createdAt = toSqlTimestamp(getLocalDateTime(map, "createdAt"));
                Timestamp updatedAt = toSqlTimestamp(getLocalDateTime(map, "updatedAt"));

                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM transactions WHERE id = ?", Integer.class, id);

                if (exists != null && exists > 0) {
                    if ("merge".equals(mode)) {
                        jdbcTemplate.update("""
                                    UPDATE transactions
                                    SET account_id = ?, amount = ?, type = ?, category = ?, currency = ?,
                                        description = ?, have_image = ?, image_path = ?, transfer_id = ?,
                                        created_at = ?, updated_at = ?
                                    WHERE id = ?
                                """, accountId, amount, type, category, currency, description,
                                haveImage, imagePath, transferId, createdAt, updatedAt, id);
                        count++;
                    }
                } else {
                    jdbcTemplate.update(
                            """
                                        INSERT INTO transactions (id, user_id, account_id, amount, type, category, currency,
                                                                 description, have_image, image_path, transfer_id, created_at, updated_at)
                                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                            id, userId, accountId, amount, type, category, currency,
                            description, haveImage, imagePath, transferId, createdAt, updatedAt);
                    count++;
                }

                // Khôi phục ManyToMany transaction_tags
                Object tagsObj = map.get("tags");
                if (tagsObj instanceof List<?> tagList && !tagList.isEmpty()) {
                    jdbcTemplate.update("DELETE FROM transaction_tags WHERE transaction_id = ?", id);
                    for (Object t : tagList) {
                        if (t instanceof Map<?, ?> tagMap) {
                            Object tagIdObj = tagMap.get("id");
                            if (tagIdObj != null) {
                                try {
                                    UUID tagId = UUID.fromString(tagIdObj.toString());
                                    jdbcTemplate.update("""
                                                INSERT INTO transaction_tags (transaction_id, tag_id)
                                                VALUES (?, ?)
                                            """, id, tagId);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Lỗi import transaction: {}", e.getMessage(), e);
                errors.add("Transaction: " + e.getMessage());
            }
        }
        return count;
    }

    private int importRecurringTransactions(List<Map<String, Object>> list, UUID userId,
            String mode, List<String> errors) {
        if (list == null || list.isEmpty())
            return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            try {
                String idStr = getString(map, "id");
                if (idStr == null)
                    continue;
                UUID id = UUID.fromString(idStr);

                String accIdStr = getString(map, "accountId");
                UUID accountId = accIdStr != null ? UUID.fromString(accIdStr) : null;
                BigDecimal amount = getBigDecimal(map, "amount");
                int type = getInt(map, "type");
                int category = getInt(map, "category");
                int currency = getInt(map, "currency");
                String description = getString(map, "description");
                int recurrenceType = getInt(map, "recurrenceType");
                int recurrenceInterval = getInt(map, "recurrenceInterval", 1);
                Date startDate = toSqlDate(getLocalDate(map, "startDate"));
                Date endDate = toSqlDate(getLocalDate(map, "endDate"));
                java.time.LocalDate nextExecLd = getLocalDate(map, "nextExecutionDate");
                Date nextExec = toSqlDate(nextExecLd != null ? nextExecLd : getLocalDate(map, "startDate"));
                int status = getInt(map, "status", Status.ACTIVE);
                Timestamp createdAt = toSqlTimestamp(getLocalDateTime(map, "createdAt"));
                Timestamp updatedAt = toSqlTimestamp(getLocalDateTime(map, "updatedAt"));

                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM recurring_transactions WHERE id = ?", Integer.class, id);

                if (exists != null && exists > 0) {
                    if ("merge".equals(mode)) {
                        jdbcTemplate.update("""
                                    UPDATE recurring_transactions
                                    SET account_id = ?, amount = ?, type = ?, category = ?, currency = ?,
                                        description = ?, recurrence_type = ?, recurrence_interval = ?,
                                        start_date = ?, end_date = ?, next_execution_date = ?, status = ?,
                                        created_at = ?, updated_at = ?
                                    WHERE id = ?
                                """, accountId, amount, type, category, currency, description,
                                recurrenceType, recurrenceInterval, startDate, endDate, nextExec, status,
                                createdAt, updatedAt, id);
                        count++;
                    }
                } else {
                    jdbcTemplate.update(
                            """
                                        INSERT INTO recurring_transactions (id, user_id, account_id, amount, type, category, currency,
                                                                           description, recurrence_type, recurrence_interval,
                                                                           start_date, end_date, next_execution_date, status,
                                                                           created_at, updated_at)
                                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                            id, userId, accountId, amount, type, category, currency, description,
                            recurrenceType, recurrenceInterval, startDate, endDate, nextExec, status,
                            createdAt, updatedAt);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Lỗi import recurringTransaction: {}", e.getMessage(), e);
                errors.add("RecurringTransaction: " + e.getMessage());
            }
        }
        return count;
    }

    private int importBudgets(List<Map<String, Object>> list, UUID userId,
            String mode, List<String> errors) {
        if (list == null || list.isEmpty())
            return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            try {
                String idStr = getString(map, "id");
                if (idStr == null)
                    continue;
                UUID id = UUID.fromString(idStr);

                int category = getInt(map, "category");
                String description = getString(map, "description");
                BigDecimal amount = getBigDecimal(map, "amount");
                int month = getInt(map, "month");
                int year = getInt(map, "year");
                String tagIdStr = getString(map, "tagId");
                UUID tagId = tagIdStr != null ? UUID.fromString(tagIdStr) : null;
                Timestamp createdAt = toSqlTimestamp(getLocalDateTime(map, "createdAt"));
                Timestamp updatedAt = toSqlTimestamp(getLocalDateTime(map, "updatedAt"));

                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM budgets WHERE id = ?", Integer.class, id);

                if (exists != null && exists > 0) {
                    if ("merge".equals(mode)) {
                        jdbcTemplate.update("""
                                    UPDATE budgets
                                    SET category = ?, description = ?, amount = ?, month = ?, year = ?, tag_id = ?,
                                        created_at = ?, updated_at = ?
                                    WHERE id = ?
                                """, category, description, amount, month, year, tagId, createdAt, updatedAt, id);
                        count++;
                    }
                } else {
                    jdbcTemplate.update(
                            """
                                        INSERT INTO budgets (id, user_id, category, description, amount, month, year, tag_id, created_at, updated_at)
                                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                            id, userId, category, description, amount, month, year, tagId, createdAt, updatedAt);
                    count++;
                }

                // Khôi phục ManyToMany budget_tags nếu có
                Object tagsObj = map.get("tags");
                if (tagsObj instanceof List<?> tagList && !tagList.isEmpty()) {
                    jdbcTemplate.update("DELETE FROM budget_tags WHERE budget_id = ?", id);
                    for (Object t : tagList) {
                        if (t instanceof Map<?, ?> tagMap) {
                            Object tId = tagMap.get("id");
                            if (tId != null) {
                                try {
                                    jdbcTemplate.update("""
                                                INSERT INTO budget_tags (budget_id, tag_id)
                                                VALUES (?, ?)
                                            """, id, UUID.fromString(tId.toString()));
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Lỗi import budget: {}", e.getMessage(), e);
                errors.add("Budget: " + e.getMessage());
            }
        }
        return count;
    }

    private int importSavingGoals(List<Map<String, Object>> list, UUID userId,
            String mode, List<String> errors) {
        if (list == null || list.isEmpty())
            return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            try {
                String idStr = getString(map, "id");
                if (idStr == null)
                    continue;
                UUID id = UUID.fromString(idStr);

                String name = getString(map, "name");
                BigDecimal targetAmount = getBigDecimal(map, "targetAmount");
                BigDecimal currentAmount = getBigDecimal(map, "currentAmount");
                Date targetDate = toSqlDate(getLocalDate(map, "targetDate"));
                String color = getString(map, "color");
                String description = getString(map, "description");
                int status = getInt(map, "status", 1);
                Timestamp createdAt = toSqlTimestamp(getLocalDateTime(map, "createdAt"));
                Timestamp updatedAt = toSqlTimestamp(getLocalDateTime(map, "updatedAt"));

                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM saving_goals WHERE id = ?", Integer.class, id);

                if (exists != null && exists > 0) {
                    if ("merge".equals(mode)) {
                        jdbcTemplate.update("""
                                    UPDATE saving_goals
                                    SET name = ?, target_amount = ?, current_amount = ?, target_date = ?,
                                        color = ?, description = ?, status = ?, created_at = ?, updated_at = ?
                                    WHERE id = ?
                                """, name, targetAmount, currentAmount, targetDate, color, description, status,
                                createdAt, updatedAt, id);
                        count++;
                    }
                } else {
                    jdbcTemplate.update(
                            """
                                        INSERT INTO saving_goals (id, user_id, name, target_amount, current_amount,
                                                                 target_date, color, description, status, created_at, updated_at)
                                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                            id, userId, name, targetAmount, currentAmount, targetDate, color, description,
                            status, createdAt, updatedAt);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Lỗi import savingGoal: {}", e.getMessage(), e);
                errors.add("SavingGoal: " + e.getMessage());
            }
        }
        return count;
    }

    private int importSavingGoalContributions(List<Map<String, Object>> list, UUID userId,
            String mode, List<String> errors) {
        if (list == null || list.isEmpty())
            return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            try {
                String idStr = getString(map, "id");
                if (idStr == null)
                    continue;
                UUID id = UUID.fromString(idStr);

                String goalIdStr = getString(map, "savingGoalId");
                if (goalIdStr == null)
                    continue;
                UUID goalId = UUID.fromString(goalIdStr);

                BigDecimal amount = getBigDecimal(map, "amount");
                Date contributionDate = toSqlDate(getLocalDate(map, "contributionDate"));
                int type = getInt(map, "type");
                String accIdStr = getString(map, "accountId");
                UUID accountId = accIdStr != null ? UUID.fromString(accIdStr) : null;
                String txIdStr = getString(map, "transactionId");
                UUID transactionId = txIdStr != null ? UUID.fromString(txIdStr) : null;
                String note = getString(map, "note");
                Timestamp createdAt = toSqlTimestamp(getLocalDateTime(map, "createdAt"));
                Timestamp updatedAt = toSqlTimestamp(getLocalDateTime(map, "updatedAt"));

                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM saving_goal_contributions WHERE id = ?", Integer.class, id);

                if (exists != null && exists > 0) {
                    if ("merge".equals(mode)) {
                        jdbcTemplate.update("""
                                    UPDATE saving_goal_contributions
                                    SET saving_goal_id = ?, amount = ?, contribution_date = ?, type = ?,
                                        account_id = ?, transaction_id = ?, note = ?, created_at = ?, updated_at = ?
                                    WHERE id = ?
                                """, goalId, amount, contributionDate, type, accountId, transactionId,
                                note, createdAt, updatedAt, id);
                        count++;
                    }
                } else {
                    jdbcTemplate.update("""
                                INSERT INTO saving_goal_contributions (id, saving_goal_id, amount, contribution_date,
                                                                      type, account_id, transaction_id, note,
                                                                      created_at, updated_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, id, goalId, amount, contributionDate, type, accountId, transactionId,
                            note, createdAt, updatedAt);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Lỗi import savingGoalContribution: {}", e.getMessage(), e);
                errors.add("SavingGoalContribution: " + e.getMessage());
            }
        }
        return count;
    }

    private int importDebts(List<Map<String, Object>> list, UUID userId,
            String mode, List<String> errors) {
        if (list == null || list.isEmpty())
            return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            try {
                String idStr = getString(map, "id");
                if (idStr == null)
                    continue;
                UUID id = UUID.fromString(idStr);

                int type = getInt(map, "type");
                String personName = getString(map, "personName");
                String phoneNumber = getString(map, "phoneNumber");
                BigDecimal initialAmount = getBigDecimal(map, "initialAmount");
                BigDecimal remainingAmount = getBigDecimal(map, "remainingAmount");
                Date startDate = toSqlDate(getLocalDate(map, "startDate"));
                Date dueDate = toSqlDate(getLocalDate(map, "dueDate"));
                int status = getInt(map, "status", 1);
                String note = getString(map, "note");
                String txIdStr = getString(map, "transactionId");
                UUID transactionId = txIdStr != null ? UUID.fromString(txIdStr) : null;
                Timestamp createdAt = toSqlTimestamp(getLocalDateTime(map, "createdAt"));
                Timestamp updatedAt = toSqlTimestamp(getLocalDateTime(map, "updatedAt"));

                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM debts WHERE id = ?", Integer.class, id);

                if (exists != null && exists > 0) {
                    if ("merge".equals(mode)) {
                        jdbcTemplate.update("""
                                    UPDATE debts
                                    SET type = ?, person_name = ?, phone_number = ?, initial_amount = ?,
                                        remaining_amount = ?, start_date = ?, due_date = ?, status = ?,
                                        note = ?, transaction_id = ?, created_at = ?, updated_at = ?
                                    WHERE id = ?
                                """, type, personName, phoneNumber, initialAmount, remainingAmount,
                                startDate, dueDate, status, note, transactionId, createdAt, updatedAt, id);
                        count++;
                    }
                } else {
                    jdbcTemplate.update("""
                                INSERT INTO debts (id, user_id, type, person_name, phone_number, initial_amount,
                                                  remaining_amount, start_date, due_date, status, note, transaction_id,
                                                  created_at, updated_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, id, userId, type, personName, phoneNumber, initialAmount, remainingAmount,
                            startDate, dueDate, status, note, transactionId, createdAt, updatedAt);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Lỗi import debt: {}", e.getMessage(), e);
                errors.add("Debt: " + e.getMessage());
            }
        }
        return count;
    }

    private int importDebtPayments(List<Map<String, Object>> list, UUID userId,
            String mode, List<String> errors) {
        if (list == null || list.isEmpty())
            return 0;
        int count = 0;
        for (Map<String, Object> map : list) {
            try {
                String idStr = getString(map, "id");
                if (idStr == null)
                    continue;
                UUID id = UUID.fromString(idStr);

                String debtIdStr = getString(map, "debtId");
                if (debtIdStr == null)
                    continue;
                UUID debtId = UUID.fromString(debtIdStr);

                BigDecimal amount = getBigDecimal(map, "amount");
                Date paymentDate = toSqlDate(getLocalDate(map, "paymentDate"));
                String accIdStr = getString(map, "accountId");
                UUID accountId = accIdStr != null ? UUID.fromString(accIdStr) : null;
                String note = getString(map, "note");
                String txIdStr = getString(map, "transactionId");
                UUID transactionId = txIdStr != null ? UUID.fromString(txIdStr) : null;
                Timestamp createdAt = toSqlTimestamp(getLocalDateTime(map, "createdAt"));
                Timestamp updatedAt = toSqlTimestamp(getLocalDateTime(map, "updatedAt"));

                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM debt_payments WHERE id = ?", Integer.class, id);

                if (exists != null && exists > 0) {
                    if ("merge".equals(mode)) {
                        jdbcTemplate.update("""
                                    UPDATE debt_payments
                                    SET debt_id = ?, amount = ?, payment_date = ?, account_id = ?,
                                        note = ?, transaction_id = ?, created_at = ?, updated_at = ?
                                    WHERE id = ?
                                """, debtId, amount, paymentDate, accountId, note, transactionId,
                                createdAt, updatedAt, id);
                        count++;
                    }
                } else {
                    jdbcTemplate.update("""
                                INSERT INTO debt_payments (id, debt_id, amount, payment_date, account_id,
                                                          note, transaction_id, created_at, updated_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, id, debtId, amount, paymentDate, accountId, note, transactionId,
                            createdAt, updatedAt);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Lỗi import debtPayment: {}", e.getMessage(), e);
                errors.add("DebtPayment: " + e.getMessage());
            }
        }
        return count;
    }

    // ========================= PRIVATE: UTILITIES =========================

    private BackupFileData parseBackupFile(MultipartFile file) throws IOException {
        ObjectMapper mapper = createObjectMapper();
        return mapper.readValue(file.getBytes(), BackupFileData.class);
    }

    private byte[] serializeToJson(BackupFileData data) {
        try {
            ObjectMapper mapper = createObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (Exception e) {
            log.error("Không thể serialize backup data: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể tạo file backup: " + e.getMessage());
        }
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Chuyển entity thành Map<String, Object> để serialize vào backup file.
     * Dùng ObjectMapper để đảm bảo tất cả field đều được serialize đúng.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> entityToMap(Object entity, boolean includeImages) {
        ObjectMapper mapper = createObjectMapper();
        try {
            Map<String, Object> map = mapper.convertValue(entity, LinkedHashMap.class);
            // Loại bỏ các field không cần thiết cho backup
            map.remove("version"); // optimistic lock
            if (!includeImages) {
                map.remove("imagePath");
                map.remove("haveImage");
            }
            return map;
        } catch (Exception e) {
            log.error("Lỗi convert entity to map: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private ImportedCounts countRecordsInFile(BackupFileData data) {
        return ImportedCounts.builder()
                .accounts(size(data.getAccounts()))
                .transactions(size(data.getTransactions()))
                .recurringTransactions(size(data.getRecurringTransactions()))
                .budgets(size(data.getBudgets()))
                .savingGoals(size(data.getSavingGoals()))
                .savingGoalContributions(size(data.getSavingGoalContributions()))
                .tags(size(data.getTags()))
                .debts(size(data.getDebts()))
                .debtPayments(size(data.getDebtPayments()))
                .build();
    }

    private int size(List<?> list) {
        return list != null ? list.size() : 0;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null)
            return BigDecimal.ZERO;
        return new BigDecimal(val.toString());
    }

    private int getInt(Map<String, Object> map, String key) {
        return getInt(map, key, 0);
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val == null)
            return defaultValue;
        return ((Number) val).intValue();
    }

    private boolean getBoolean(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null)
            return false;
        return Boolean.parseBoolean(val.toString());
    }

    private LocalDateTime getLocalDateTime(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null)
            return null;
        try {
            return LocalDateTime.parse(val.toString());
        } catch (Exception e) {
            log.warn("Không thể parse LocalDateTime từ {}: {}", val, e.getMessage());
            return null;
        }
    }

    private LocalDate getLocalDate(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null)
            return null;
        try {
            return LocalDate.parse(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Timestamp toSqlTimestamp(LocalDateTime ldt) {
        return ldt != null ? Timestamp.valueOf(ldt) : null;
    }

    private Date toSqlDate(LocalDate ld) {
        return ld != null ? Date.valueOf(ld) : null;
    }
}
