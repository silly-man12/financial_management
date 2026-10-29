package com.example.financial_management.services;

import com.example.financial_management.config.BankWebhookProperties;
import com.example.financial_management.constant.AccountType;
import com.example.financial_management.constant.Currency;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.BankCategoryRule;
import com.example.financial_management.entity.BankWebhookEvent;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.entity.WalletSourceMapping;
import com.example.financial_management.model.PageResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.webhook.BankCategoryRuleRequest;
import com.example.financial_management.model.webhook.BankCategoryRuleResponse;
import com.example.financial_management.model.webhook.BankWebhookEventResponse;
import com.example.financial_management.model.webhook.BankWebhookProcessResult;
import com.example.financial_management.model.webhook.BankWebhookSimulateRequest;
import com.example.financial_management.model.webhook.WalletSourceMappingRequest;
import com.example.financial_management.model.webhook.WalletSourceMappingResponse;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.BankCategoryRuleRepository;
import com.example.financial_management.repository.BankWebhookEventRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;
import com.example.financial_management.repository.WalletSourceMappingRepository;
import com.example.financial_management.telegram.TelegramMessageParser;
import com.example.financial_management.webhook.BankDirection;
import com.example.financial_management.webhook.BankTransactionEvent;
import com.example.financial_management.webhook.BankWebhookProcessedEvent;
import com.example.financial_management.webhook.BankWebhookProvider;
import com.example.financial_management.webhook.BankWebhookStatus;
import com.example.financial_management.webhook.adapter.BankPayloadAdapter;
import com.example.financial_management.webhook.verifier.BankWebhookVerifier;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankWebhookService {

    private final BankWebhookProperties properties;
    private final List<BankWebhookVerifier> verifiers;
    private final List<BankPayloadAdapter> adapters;
    private final BankWebhookEventRepository eventRepository;
    private final WalletSourceMappingRepository mappingRepository;
    private final BankCategoryRuleRepository ruleRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final BankTransactionClassifier classifier;
    private final TelegramMessageParser messageParser;
    private final BankWebhookNotifier webhookNotifier;
    private final TransactionTemplate transactionTemplate;

    private record TxExecutionOutcome(
            BankWebhookProcessResult result,
            BankWebhookProcessedEvent notificationEvent) {
    }

    /**
     * Tự động xử lý bù các sự kiện webhook bị SKIPPED / RECEIVED (chưa tạo
     * Transaction) ngay khi khởi động
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverUnprocessedWebhookEventsOnStartup() {
        try {
            List<BankWebhookEvent> pendingEvents = eventRepository
                    .findByStatusInAndTransactionIdIsNullOrderByCreatedAtAsc(
                            List.of(BankWebhookStatus.SKIPPED.name(), BankWebhookStatus.RECEIVED.name()));
            if (pendingEvents.isEmpty()) {
                return;
            }
            log.info(
                    "[BankWebhook] Found {} unprocessed SePay webhook events in DB. Automatically processing into transactions...",
                    pendingEvents.size());
            for (BankWebhookEvent pending : pendingEvents) {
                try {
                    BankWebhookProvider provider = BankWebhookProvider.fromString(pending.getProvider());
                    BankPayloadAdapter adapter = adapters.stream()
                            .filter(a -> a.getProvider() == provider)
                            .findFirst()
                            .orElse(null);
                    if (adapter != null && pending.getRawPayload() != null) {
                        List<BankTransactionEvent> adapted = adapter.adapt(pending.getRawPayload());
                        if (!adapted.isEmpty()) {
                            BankTransactionEvent ev = adapted.get(0).toBuilder()
                                    .externalId(pending.getExternalId())
                                    .build();
                            processEventWithRetry(ev, null, pending.getId());
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[BankWebhook] Failed to recover event {}: {}", pending.getId(), ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[BankWebhook] Error checking unprocessed webhook events on startup: {}", e.getMessage());
        }
    }

    /**
     * Xác thực và xử lý webhook biến động số dư gửi từ SePay (MB Bank / Vietcombank
     * / ...)
     */
    public List<BankWebhookProcessResult> handleIncomingWebhook(
            BankWebhookProvider provider,
            HttpServletRequest request,
            String rawBody) {

        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Bank webhook module is currently disabled");
        }

        BankWebhookVerifier verifier = verifiers.stream()
                .filter(v -> v.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No verifier for provider: " + provider));

        verifier.verify(request, rawBody);

        BankPayloadAdapter adapter = adapters.stream()
                .filter(a -> a.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No payload adapter for provider: " + provider));

        List<BankTransactionEvent> events = adapter.adapt(rawBody);
        List<BankWebhookProcessResult> results = new ArrayList<>();

        for (BankTransactionEvent event : events) {
            results.add(processEventWithRetry(event, null, null));
        }

        return results;
    }

    /**
     * Thực thi xử lý sự kiện kèm cơ chế retry khi gặp xung đột OptimisticLock trên
     * Account.version
     * và gửi thông báo Telegram ngay sau khi DB transaction đã commit.
     */
    public BankWebhookProcessResult processEventWithRetry(
            BankTransactionEvent event,
            User explicitUser,
            UUID existingEventId) {

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                TxExecutionOutcome outcome = transactionTemplate.execute(
                        status -> processSingleEventInTransaction(event, explicitUser, existingEventId));
                if (outcome == null) {
                    throw new IllegalStateException("Transaction execution returned null outcome");
                }
                if (outcome.notificationEvent() != null) {
                    webhookNotifier.notifyWebhookProcessed(outcome.notificationEvent());
                }
                return outcome.result();
            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
                log.warn("[BankWebhook] Optimistic lock conflict on attempt {}/{} for externalId={}",
                        attempt, maxAttempts, event.externalId());
                if (attempt == maxAttempts) {
                    throw e;
                }
            } catch (DataIntegrityViolationException e) {
                log.info("[BankWebhook] Concurrent duplicate detected for provider={} externalId={}",
                        event.provider(), event.externalId());
                return BankWebhookProcessResult.builder()
                        .provider(event.provider().name())
                        .externalId(event.externalId())
                        .status(BankWebhookStatus.DUPLICATE.name())
                        .message("Duplicate webhook event ignored")
                        .amount(event.amount())
                        .build();
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    protected TxExecutionOutcome processSingleEventInTransaction(
            BankTransactionEvent event,
            User explicitUser,
            UUID existingEventId) {

        String providerName = event.provider().name();

        // 1. Kiểm tra trùng lặp (Deduplication)
        BankWebhookEvent record;
        if (existingEventId != null) {
            record = eventRepository.findById(existingEventId)
                    .orElseThrow(() -> new NoSuchElementException("Webhook event not found: " + existingEventId));
        } else {
            Optional<BankWebhookEvent> existingOpt = eventRepository.findByProviderAndExternalId(providerName,
                    event.externalId());
            if (existingOpt.isPresent()) {
                BankWebhookEvent existing = existingOpt.get();
                if ((BankWebhookStatus.PROCESSED.name().equals(existing.getStatus())
                        || BankWebhookStatus.MATCHED.name().equals(existing.getStatus())
                        || BankWebhookStatus.DUPLICATE.name().equals(existing.getStatus()))
                        && existing.getTransactionId() != null) {
                    log.info("[BankWebhook] Duplicate event ignored: provider={}, externalId={}", providerName,
                            event.externalId());
                    BankWebhookProcessResult dupResult = BankWebhookProcessResult.builder()
                            .eventId(existing.getId())
                            .provider(providerName)
                            .externalId(event.externalId())
                            .status(BankWebhookStatus.DUPLICATE.name())
                            .message("Duplicate webhook event ignored")
                            .transactionId(existing.getTransactionId())
                            .matchedEventId(existing.getMatchedEventId())
                            .amount(existing.getAmount())
                            .build();
                    return new TxExecutionOutcome(dupResult, null);
                }
                record = existing;
            } else {
                record = new BankWebhookEvent();
            }
        }

        // 2. Xác định User và Ví ngân hàng (Account) đích (tự động chọn ví MB Bank /
        // tạo ví nếu chưa có)
        ResolvedWallet resolved = resolveUserAndWallet(event, explicitUser);
        User user = resolved.user();
        Account targetAccount = resolved.account();

        record.setUserId(user != null ? user.getId() : null)
                .setProvider(providerName)
                .setExternalId(event.externalId())
                .setSourceKey(event.sourceKey())
                .setGateway(event.gateway())
                .setDirection(event.direction() != null ? event.direction().name() : null)
                .setEventKind(event.eventKind() != null ? event.eventKind().name() : null)
                .setFundingSource(event.fundingSource() != null ? event.fundingSource().name() : null)
                .setAmount(event.amount())
                .setBankBalance(event.bankBalance())
                .setContent(truncate(event.content(), 500))
                .setCounterparty(truncate(event.counterparty(), 255))
                .setReferenceCode(truncate(event.referenceCode(), 120))
                .setOccurredAt(event.occurredAt() != null ? event.occurredAt() : LocalDateTime.now())
                .setRawPayload(event.rawPayload());

        if (user == null || targetAccount == null) {
            String errMsg = "Chưa tìm thấy ví ánh xạ cho nguồn '" + event.sourceKey() + "' (" + event.gateway() + ")";
            record.setStatus(BankWebhookStatus.SKIPPED.name())
                    .setErrorMessage(truncate(errMsg, 500));
            BankWebhookEvent saved = eventRepository.save(record);

            BankWebhookProcessedEvent notifEvent = BankWebhookProcessedEvent.builder()
                    .notificationType(BankWebhookProcessedEvent.NotificationType.SKIPPED_UNMAPPED)
                    .user(user)
                    .webhookEvent(saved)
                    .build();

            BankWebhookProcessResult skippedRes = BankWebhookProcessResult.builder()
                    .eventId(saved.getId())
                    .provider(providerName)
                    .externalId(event.externalId())
                    .status(BankWebhookStatus.SKIPPED.name())
                    .message(errMsg)
                    .amount(event.amount())
                    .build();
            return new TxExecutionOutcome(skippedRes, notifEvent);
        }

        // Lưu trạng thái RECEIVED trước khi xử lý
        record.setStatus(BankWebhookStatus.RECEIVED.name())
                .setErrorMessage(null);
        record = eventRepository.save(record);

        // 3. Tạo Transaction mới vào bảng transactions và cập nhật số dư ví (không bao
        // giờ lỗi Insufficient balance)
        int txType = event.direction() == BankDirection.IN
                ? TransactionType.INCOME
                : TransactionType.EXPENSE;
        int category = classifier.classify(user.getId(), event, txType);
        String description = buildTransactionDescription(event);

        BigDecimal delta = txType == TransactionType.INCOME
                ? event.amount()
                : event.amount().negate();

        applyWebhookBalanceUpdate(targetAccount, delta, event.bankBalance());

        Transaction tx = new Transaction();
        tx.setUserId(user.getId());
        tx.setAccountId(targetAccount.getId());
        tx.setAmount(event.amount());
        tx.setType(txType);
        tx.setCategory(category);
        tx.setCurrency(targetAccount.getCurrency());
        tx.setDescription(description);
        tx.setHaveImage(false);
        tx.setCreatedAt(event.occurredAt() != null ? event.occurredAt() : LocalDateTime.now());
        Transaction savedTx = transactionRepository.save(tx);

        record.setStatus(BankWebhookStatus.PROCESSED.name())
                .setTransactionId(savedTx.getId())
                .setErrorMessage(null);
        eventRepository.save(record);

        BigDecimal discrepancy = null;
        if (event.bankBalance() != null) {
            BigDecimal diff = event.bankBalance().subtract(targetAccount.getBalance());
            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                discrepancy = diff;
            }
        }

        BankWebhookProcessedEvent notifEvent = BankWebhookProcessedEvent.builder()
                .notificationType(BankWebhookProcessedEvent.NotificationType.NORMAL_TRANSACTION)
                .user(user)
                .webhookEvent(record)
                .transaction(savedTx)
                .primaryAccount(targetAccount)
                .balanceDiscrepancy(discrepancy)
                .build();

        String catName = messageParser.getCategoryVietnameseName(category);

        BankWebhookProcessResult processedRes = BankWebhookProcessResult.builder()
                .eventId(record.getId())
                .provider(providerName)
                .externalId(event.externalId())
                .status(BankWebhookStatus.PROCESSED.name())
                .message("Đã tự động ghi nhận giao dịch và gửi thông báo Telegram thành công")
                .transactionId(savedTx.getId())
                .accountId(targetAccount.getId())
                .accountName(targetAccount.getName())
                .amount(event.amount())
                .category(category)
                .categoryName(catName)
                .newAccountBalance(targetAccount.getBalance())
                .balanceDiscrepancy(discrepancy)
                .build();

        return new TxExecutionOutcome(processedRes, notifEvent);
    }

    /**
     * Cập nhật số dư ví an toàn cho Webhook:
     * Nếu số dư trong ví trên web đang nhỏ hơn số tiền trừ (ví dụ bằng 0đ do chưa
     * nhập số dư đầu kỳ),
     * hệ thống sẽ không ném lỗi "Insufficient balance" mà tự đồng bộ theo số dư
     * thực tế (accumulated) từ SePay.
     */
    private void applyWebhookBalanceUpdate(Account targetAccount, BigDecimal delta, BigDecimal sepayAccumulated) {
        BigDecimal currentBal = targetAccount.getBalance() != null ? targetAccount.getBalance() : BigDecimal.ZERO;
        if (currentBal.add(delta).compareTo(BigDecimal.ZERO) < 0) {
            if (targetAccount.getVersion() == null) {
                targetAccount.setVersion(0L);
            }
            BigDecimal newBal = (sepayAccumulated != null && sepayAccumulated.compareTo(BigDecimal.ZERO) >= 0)
                    ? sepayAccumulated
                    : BigDecimal.ZERO;
            targetAccount.setBalance(newBal);
            accountRepository.save(targetAccount);
            log.info("[BankWebhook] Synced wallet '{}' balance to {} (previous balance {} was lower than debit {})",
                    targetAccount.getName(), newBal, currentBal, delta);
        } else {
            accountService.applyDelta(targetAccount, delta);
        }
    }

    private record ResolvedWallet(User user, Account account) {
    }

    private ResolvedWallet resolveUserAndWallet(BankTransactionEvent event, User explicitUser) {
        // 1. Tìm trong bảng cấu hình ánh xạ WalletSourceMapping trước
        if (event.sourceKey() != null && !event.sourceKey().isBlank()) {
            Optional<WalletSourceMapping> mappingOpt = mappingRepository
                    .findByProviderAndSourceKeyAndActiveTrue(event.provider().name(), event.sourceKey().trim());
            if (mappingOpt.isPresent()) {
                WalletSourceMapping mapping = mappingOpt.get();
                User mappedUser = explicitUser != null
                        ? explicitUser
                        : userRepository.findById(mapping.getUserId()).orElse(null);
                Account mappedAccount = accountRepository
                        .findByIdAndStatus(mapping.getAccountId(), Status.ACTIVE)
                        .orElse(null);
                if (mappedUser != null && mappedAccount != null) {
                    return new ResolvedWallet(mappedUser, mappedAccount);
                }
            }
        }

        // 2. Xác định User (ưu tiên User có email cấu hình hoặc User đã liên kết
        // Telegram)
        User user = resolveBestTargetUser(explicitUser);
        if (user == null) {
            return new ResolvedWallet(null, null);
        }

        List<Account> activeAccounts = accountRepository.findAllByUserIdAndStatus(user.getId(), Status.ACTIVE);

        // Nếu chưa có ví nào trong tài khoản này, tự động tạo ví mới theo tên cổng ngân
        // hàng (gateway, vd: MBBank)
        if (activeAccounts.isEmpty()) {
            Account autoCreated = autoCreateWalletForEvent(user, event);
            if (autoCreated != null) {
                return new ResolvedWallet(user, autoCreated);
            }
            return new ResolvedWallet(user, null);
        }

        // 3. Tự động dò ví theo số tài khoản (trong tên hoặc mô tả ví)
        if (event.sourceKey() != null && !event.sourceKey().isBlank()) {
            String sk = event.sourceKey().trim().toLowerCase(Locale.ROOT);
            for (Account acc : activeAccounts) {
                String name = acc.getName() != null ? acc.getName().toLowerCase(Locale.ROOT) : "";
                String desc = acc.getDescription() != null ? acc.getDescription().toLowerCase(Locale.ROOT) : "";
                if (name.contains(sk) || desc.contains(sk)) {
                    return new ResolvedWallet(user, acc);
                }
            }
        }

        // 4. Tự động dò ví theo tên cổng / ngân hàng (MBBank / MB, Vietcombank / VCB,
        // Techcombank / TCB...)
        Account byGateway = matchAccountByKeyword(activeAccounts, event.gateway());
        if (byGateway != null) {
            return new ResolvedWallet(user, byGateway);
        }
        Account bySourceKey = matchAccountByKeyword(activeAccounts, event.sourceKey());
        if (bySourceKey != null) {
            return new ResolvedWallet(user, bySourceKey);
        }

        // 5. Fallback thông minh: Ưu tiên ví loại Ngân hàng (AccountType.BANK), hoặc ví
        // đang hoạt động đầu tiên
        for (Account acc : activeAccounts) {
            if (acc.getType() == AccountType.BANK) {
                return new ResolvedWallet(user, acc);
            }
        }
        for (Account acc : activeAccounts) {
            String normName = messageParser.removeDiacritics(acc.getName()).toLowerCase(Locale.ROOT);
            if (normName.contains("ngan hang") || normName.contains("bank")
                    || normName.contains("tai khoan") || normName.contains("the ")
                    || normName.contains("atm")) {
                return new ResolvedWallet(user, acc);
            }
        }

        return new ResolvedWallet(user, activeAccounts.get(0));
    }

    private User resolveBestTargetUser(User explicitUser) {
        if (explicitUser != null) {
            return explicitUser;
        }
        User byEmail = null;
        if (properties.getDefaultUserEmail() != null && !properties.getDefaultUserEmail().isBlank()) {
            byEmail = userRepository.findByEmailAndStatus(properties.getDefaultUserEmail().trim(), Status.ACTIVE)
                    .orElse(null);
        }
        if (byEmail != null && byEmail.getTelegramChatId() != null) {
            return byEmail;
        }

        List<User> allUsers = userRepository.findAll();
        if (allUsers == null || allUsers.isEmpty()) {
            return byEmail;
        }

        User linkedTelegramUser = allUsers.stream()
                .filter(u -> u.getStatus() == Status.ACTIVE && u.getTelegramChatId() != null)
                .findFirst()
                .orElse(null);

        if (byEmail != null) {
            if (byEmail.getTelegramChatId() == null && linkedTelegramUser != null) {
                byEmail.setTelegramChatId(linkedTelegramUser.getTelegramChatId());
            }
            return byEmail;
        }

        if (linkedTelegramUser != null) {
            return linkedTelegramUser;
        }

        return allUsers.stream()
                .filter(u -> u.getStatus() == Status.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    private Account autoCreateWalletForEvent(User user, BankTransactionEvent event) {
        try {
            String walletName = (event.gateway() != null && !event.gateway().isBlank())
                    ? event.gateway().trim()
                    : "MB Bank";

            BigDecimal initialBalance = BigDecimal.ZERO;
            if (event.bankBalance() != null && event.amount() != null) {
                initialBalance = event.direction() == BankDirection.IN
                        ? event.bankBalance().subtract(event.amount())
                        : event.bankBalance().add(event.amount());
                if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
                    initialBalance = BigDecimal.ZERO;
                }
            }

            Account newAcc = new Account()
                    .setUserId(user.getId())
                    .setName(walletName)
                    .setType(AccountType.BANK)
                    .setCurrency(Currency.VND)
                    .setStatus(Status.ACTIVE)
                    .setBalance(initialBalance)
                    .setDescription("Tự động khởi tạo từ SePay Webhook ("
                            + (event.sourceKey() != null ? event.sourceKey() : walletName) + ")");

            Account saved = accountRepository.save(newAcc);
            log.info("[BankWebhook] Auto-created active bank wallet '{}' (id={}) for user {}",
                    saved.getName(), saved.getId(), user.getEmail());
            return saved;
        } catch (Exception e) {
            log.warn("[BankWebhook] Could not auto-create wallet: {}", e.getMessage());
            return null;
        }
    }

    private Account matchAccountByKeyword(List<Account> accounts, String rawHint) {
        if (rawHint == null || rawHint.isBlank()) {
            return null;
        }
        String hint = messageParser.removeDiacritics(rawHint).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (hint.isEmpty()) {
            return null;
        }

        Map<String, List<String>> bankAliases = Map.ofEntries(
                Map.entry("mbbank", List.of("mbbank", "mb", "mb bank", "quan doi")),
                Map.entry("mb", List.of("mbbank", "mb", "mb bank", "quan doi")),
                Map.entry("vietcombank", List.of("vietcombank", "vcb")),
                Map.entry("vcb", List.of("vietcombank", "vcb")),
                Map.entry("techcombank", List.of("techcombank", "tcb", "techcom")),
                Map.entry("tcb", List.of("techcombank", "tcb", "techcom")),
                Map.entry("vietinbank", List.of("vietinbank", "vietin", "ctg", "icb")),
                Map.entry("ctg", List.of("vietinbank", "vietin", "ctg", "icb")),
                Map.entry("bidv", List.of("bidv")),
                Map.entry("acb", List.of("acb", "a chau")),
                Map.entry("tpbank", List.of("tpbank", "tpb", "tien phong")),
                Map.entry("vpbank", List.of("vpbank", "vpb")),
                Map.entry("agribank", List.of("agribank", "agri", "vba")),
                Map.entry("sacombank", List.of("sacombank", "stb")),
                Map.entry("vib", List.of("vib")),
                Map.entry("hdbank", List.of("hdbank", "hdb")),
                Map.entry("msb", List.of("msb", "hang hai")),
                Map.entry("ocb", List.of("ocb", "phuong dong")),
                Map.entry("shb", List.of("shb")),
                Map.entry("seabank", List.of("seabank")),
                Map.entry("eximbank", List.of("eximbank", "eib")),
                Map.entry("lpbank", List.of("lpbank", "lienviet")),
                Map.entry("cake", List.of("cake")),
                Map.entry("timo", List.of("timo")));

        List<String> candidateTokens = new ArrayList<>();
        candidateTokens.add(hint);
        for (Map.Entry<String, List<String>> entry : bankAliases.entrySet()) {
            if (hint.contains(entry.getKey())) {
                candidateTokens.addAll(entry.getValue());
            }
        }

        for (Account acc : accounts) {
            String accNorm = messageParser.removeDiacritics(acc.getName())
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]", "");
            for (String token : candidateTokens) {
                String cleanToken = token.replaceAll("[^a-z0-9]", "");
                if (!cleanToken.isEmpty() && (accNorm.equals(cleanToken) || accNorm.contains(cleanToken))) {
                    return acc;
                }
            }
        }
        return null;
    }

    private String buildTransactionDescription(BankTransactionEvent event) {
        String content = event.content() != null ? event.content().trim() : "";
        if (!content.isEmpty()) {
            return truncate(content, 250);
        }
        return truncate("Giao dịch tự động qua " + event.gateway(), 250);
    }

    // =========================================================================
    // Management APIs (Lịch sử sự kiện, Xử lý lại, Ánh xạ ví, Quy tắc phân loại)
    // =========================================================================

    public PageResponse<BankWebhookEventResponse> getEvents(Auth auth, String status, Pageable pageable) {
        User user = requireUser(auth);
        Page<BankWebhookEvent> page;
        if (status != null && !status.isBlank()) {
            page = eventRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                    user.getId(), status.trim().toUpperCase(Locale.ROOT), pageable);
        } else {
            page = eventRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        }
        return PageResponse.of(page, this::toEventResponse);
    }

    public BankWebhookProcessResult reprocessEvent(Auth auth, UUID eventId) {
        User user = requireUser(auth);
        BankWebhookEvent existing = eventRepository.findByIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy sự kiện webhook: " + eventId));

        if ((BankWebhookStatus.PROCESSED.name().equals(existing.getStatus())
                || BankWebhookStatus.MATCHED.name().equals(existing.getStatus()))
                && existing.getTransactionId() != null) {
            throw new IllegalArgumentException("Sự kiện này đã được xử lý trước đó");
        }

        BankWebhookProvider provider = BankWebhookProvider.fromString(existing.getProvider());
        BankPayloadAdapter adapter = adapters.stream()
                .filter(a -> a.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy adapter cho provider: " + provider));

        List<BankTransactionEvent> adapted = adapter.adapt(existing.getRawPayload());
        if (adapted.isEmpty()) {
            throw new IllegalArgumentException("Không thể đọc lại payload của sự kiện");
        }

        BankTransactionEvent event = adapted.get(0).toBuilder()
                .externalId(existing.getExternalId())
                .build();

        return processEventWithRetry(event, user, existing.getId());
    }

    public List<BankWebhookProcessResult> reprocessAllPendingEvents(Auth auth) {
        User user = requireUser(auth);
        List<BankWebhookEvent> pendingEvents = eventRepository.findByStatusInAndTransactionIdIsNullOrderByCreatedAtAsc(
                List.of(BankWebhookStatus.SKIPPED.name(), BankWebhookStatus.RECEIVED.name()));
        List<BankWebhookProcessResult> results = new ArrayList<>();
        for (BankWebhookEvent pending : pendingEvents) {
            if (pending.getUserId() != null && !pending.getUserId().equals(user.getId())) {
                continue;
            }
            BankWebhookProvider provider = BankWebhookProvider.fromString(pending.getProvider());
            BankPayloadAdapter adapter = adapters.stream()
                    .filter(a -> a.getProvider() == provider)
                    .findFirst()
                    .orElse(null);
            if (adapter != null && pending.getRawPayload() != null) {
                List<BankTransactionEvent> adapted = adapter.adapt(pending.getRawPayload());
                if (!adapted.isEmpty()) {
                    BankTransactionEvent ev = adapted.get(0).toBuilder()
                            .externalId(pending.getExternalId())
                            .build();
                    results.add(processEventWithRetry(ev, user, pending.getId()));
                }
            }
        }
        return results;
    }

    public List<WalletSourceMappingResponse> getMappings(Auth auth) {
        User user = requireUser(auth);
        return mappingRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toMappingResponse)
                .toList();
    }

    public WalletSourceMappingResponse upsertMapping(Auth auth, WalletSourceMappingRequest request) {
        User user = requireUser(auth);
        BankWebhookProvider provider = BankWebhookProvider.fromString(request.getProvider());
        String sourceKey = request.getSourceKey().trim();

        Account targetAccount = accountRepository.findByIdAndUserIdAndStatus(
                request.getAccountId(), user.getId(), Status.ACTIVE)
                .orElseThrow(() -> new NoSuchElementException(
                        "Không tìm thấy ví hoạt động với ID: " + request.getAccountId()));

        WalletSourceMapping mapping = mappingRepository
                .findByUserIdAndProviderAndSourceKey(user.getId(), provider.name(), sourceKey)
                .orElseGet(WalletSourceMapping::new);

        mapping.setUserId(user.getId())
                .setProvider(provider.name())
                .setSourceKey(sourceKey)
                .setAccountId(targetAccount.getId())
                .setBankCode(request.getBankCode() != null ? request.getBankCode().trim() : null)
                .setLabel(request.getLabel() != null ? request.getLabel().trim() : targetAccount.getName())
                .setActive(request.getActive() == null || request.getActive());

        WalletSourceMapping saved = mappingRepository.save(mapping);
        return toMappingResponse(saved);
    }

    public void deleteMapping(Auth auth, UUID mappingId) {
        User user = requireUser(auth);
        WalletSourceMapping mapping = mappingRepository.findByIdAndUserId(mappingId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy ánh xạ nguồn: " + mappingId));
        mappingRepository.delete(mapping);
    }

    public List<BankCategoryRuleResponse> getRules(Auth auth) {
        User user = requireUser(auth);
        return ruleRepository.findAllByUserIdOrderByPriorityDescCreatedAtAsc(user.getId())
                .stream()
                .map(this::toRuleResponse)
                .toList();
    }

    public BankCategoryRuleResponse createRule(Auth auth, BankCategoryRuleRequest request) {
        User user = requireUser(auth);
        String direction = null;
        if (request.getDirection() != null && !request.getDirection().isBlank()) {
            direction = BankDirection.valueOf(request.getDirection().trim().toUpperCase(Locale.ROOT)).name();
        }

        BankCategoryRule rule = new BankCategoryRule()
                .setUserId(user.getId())
                .setKeyword(request.getKeyword().trim())
                .setCategory(request.getCategory())
                .setDirection(direction)
                .setPriority(request.getPriority() != null ? request.getPriority() : 100)
                .setDescription(request.getDescription())
                .setActive(request.getActive() == null || request.getActive());

        BankCategoryRule saved = ruleRepository.save(rule);
        return toRuleResponse(saved);
    }

    public void deleteRule(Auth auth, UUID ruleId) {
        User user = requireUser(auth);
        BankCategoryRule rule = ruleRepository.findByIdAndUserId(ruleId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy quy tắc phân loại: " + ruleId));
        ruleRepository.delete(rule);
    }

    public List<BankWebhookProcessResult> simulateWebhook(Auth auth, BankWebhookSimulateRequest request) {
        User user = requireUser(auth);
        BankWebhookProvider provider = BankWebhookProvider.fromString(request.getProvider());
        BankPayloadAdapter adapter = adapters.stream()
                .filter(a -> a.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy adapter cho provider: " + provider));

        List<BankTransactionEvent> events = adapter.adapt(request.getRawPayload());
        List<BankWebhookProcessResult> results = new ArrayList<>();
        for (BankTransactionEvent event : events) {
            results.add(processEventWithRetry(event, user, null));
        }
        return results;
    }

    private User requireUser(Auth auth) {
        if (auth == null || auth.getUUID() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để thực hiện thao tác");
        }
        return userRepository.findById(auth.getUUID())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy người dùng"));
    }

    private BankWebhookEventResponse toEventResponse(BankWebhookEvent e) {
        return BankWebhookEventResponse.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .provider(e.getProvider())
                .externalId(e.getExternalId())
                .sourceKey(e.getSourceKey())
                .gateway(e.getGateway())
                .direction(e.getDirection())
                .eventKind(e.getEventKind())
                .fundingSource(e.getFundingSource())
                .amount(e.getAmount())
                .bankBalance(e.getBankBalance())
                .content(e.getContent())
                .counterparty(e.getCounterparty())
                .referenceCode(e.getReferenceCode())
                .occurredAt(e.getOccurredAt())
                .status(e.getStatus())
                .errorMessage(e.getErrorMessage())
                .transactionId(e.getTransactionId())
                .matchedEventId(e.getMatchedEventId())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private WalletSourceMappingResponse toMappingResponse(WalletSourceMapping m) {
        String accName = accountRepository.findById(m.getAccountId())
                .map(Account::getName)
                .orElse(null);
        return WalletSourceMappingResponse.builder()
                .id(m.getId())
                .userId(m.getUserId())
                .provider(m.getProvider())
                .sourceKey(m.getSourceKey())
                .accountId(m.getAccountId())
                .accountName(accName)
                .bankCode(m.getBankCode())
                .label(m.getLabel())
                .active(m.isActive())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private BankCategoryRuleResponse toRuleResponse(BankCategoryRule r) {
        return BankCategoryRuleResponse.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .keyword(r.getKeyword())
                .category(r.getCategory())
                .categoryName(messageParser.getCategoryVietnameseName(r.getCategory()))
                .direction(r.getDirection())
                .priority(r.getPriority())
                .description(r.getDescription())
                .active(r.isActive())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        return str.length() <= maxLen ? str : str.substring(0, maxLen);
    }
}
