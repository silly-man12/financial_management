package com.example.financial_management.services;

import com.example.financial_management.config.BankWebhookProperties;
import com.example.financial_management.constant.AccountType;
import com.example.financial_management.constant.Category;
import com.example.financial_management.constant.Currency;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.BankCategoryRule;
import com.example.financial_management.entity.BankWebhookEvent;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.model.webhook.BankWebhookProcessResult;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.BankCategoryRuleRepository;
import com.example.financial_management.repository.BankWebhookEventRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;
import com.example.financial_management.repository.WalletSourceMappingRepository;
import com.example.financial_management.telegram.TelegramMessageParser;
import com.example.financial_management.webhook.BankDirection;
import com.example.financial_management.webhook.BankEventKind;
import com.example.financial_management.webhook.BankFundingSource;
import com.example.financial_management.webhook.BankTransactionEvent;
import com.example.financial_management.webhook.BankWebhookProvider;
import com.example.financial_management.webhook.BankWebhookStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankWebhookServiceAndReconciliationTest {

    @Mock
    private BankWebhookEventRepository eventRepository;
    @Mock
    private WalletSourceMappingRepository mappingRepository;
    @Mock
    private BankCategoryRuleRepository ruleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountService accountService;
    @Mock
    private BankWebhookNotifier webhookNotifier;
    @Mock
    private TransactionTemplate transactionTemplate;

    private BankWebhookProperties properties;
    private TelegramMessageParser messageParser;
    private BankTransactionClassifier classifier;
    private BankWebhookService webhookService;

    private User testUser;
    private Account mbBankAccount;

    private final Map<UUID, BankWebhookEvent> savedEvents = new HashMap<>();
    private final Map<UUID, Transaction> savedTransactions = new HashMap<>();

    @BeforeEach
    void setUp() {
        properties = new BankWebhookProperties();
        properties.setEnabled(true);
        properties.setDefaultUserEmail("nghyqn1201@gmail.com");

        messageParser = new TelegramMessageParser();
        classifier = new BankTransactionClassifier(ruleRepository, messageParser);

        webhookService = new BankWebhookService(
                properties,
                List.of(),
                List.of(),
                eventRepository,
                mappingRepository,
                ruleRepository,
                userRepository,
                accountRepository,
                transactionRepository,
                accountService,
                classifier,
                messageParser,
                webhookNotifier,
                transactionTemplate
        );

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("nghyqn1201@gmail.com");
        testUser.setName("Quyen");
        testUser.setStatus(Status.ACTIVE);
        testUser.setTelegramChatId(123456L);

        mbBankAccount = new Account();
        mbBankAccount.setId(UUID.randomUUID());
        mbBankAccount.setUserId(testUser.getId());
        mbBankAccount.setName("MB Bank");
        mbBankAccount.setBalance(new BigDecimal("10000000"));
        mbBankAccount.setType(AccountType.BANK);
        mbBankAccount.setCurrency(Currency.VND);
        mbBankAccount.setStatus(Status.ACTIVE);

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        lenient().when(userRepository.findByEmailAndStatus("nghyqn1201@gmail.com", Status.ACTIVE))
                .thenReturn(Optional.of(testUser));
        lenient().when(accountRepository.findAllByUserIdAndStatus(testUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(mbBankAccount));

        lenient().doAnswer(inv -> {
            Account acc = inv.getArgument(0);
            BigDecimal delta = inv.getArgument(1);
            acc.setBalance(acc.getBalance().add(delta));
            return null;
        }).when(accountService).applyDelta(any(Account.class), any(BigDecimal.class));

        lenient().when(eventRepository.save(any(BankWebhookEvent.class))).thenAnswer(inv -> {
            BankWebhookEvent ev = inv.getArgument(0);
            if (ev.getId() == null) {
                ev.setId(UUID.randomUUID());
            }
            savedEvents.put(ev.getId(), ev);
            return ev;
        });

        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
            }
            savedTransactions.put(tx.getId(), tx);
            return tx;
        });

        lenient().when(ruleRepository.findAllByUserIdAndActiveTrueOrderByPriorityDescCreatedAtAsc(any()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("1. Giao dịch SePay MB Bank (thanh toán trực tiếp hoặc qua ZaloPay liên kết MB) tạo Transaction và gửi Telegram")
    void testNormalSePayTransactionAndDeduplication() {
        BankTransactionEvent event = BankTransactionEvent.builder()
                .provider(BankWebhookProvider.SEPAY)
                .externalId("SEPAY_92704")
                .sourceKey("0987654321")
                .gateway("MBBank")
                .direction(BankDirection.OUT)
                .eventKind(BankEventKind.PAYMENT)
                .fundingSource(BankFundingSource.BANK_ACCOUNT)
                .amount(new BigDecimal("50000"))
                .bankBalance(new BigDecimal("9950000"))
                .content("ZALOPAY HIGHLANDS COFFEE 50000")
                .occurredAt(LocalDateTime.now())
                .parsed(true)
                .build();

        when(eventRepository.findByProviderAndExternalId("SEPAY", "SEPAY_92704"))
                .thenReturn(Optional.empty());

        BankWebhookProcessResult result1 = webhookService.processEventWithRetry(event, null, null);
        assertEquals(BankWebhookStatus.PROCESSED.name(), result1.getStatus());
        assertNotNull(result1.getTransactionId());
        assertEquals(Category.FOOD, result1.getCategory());
        assertEquals(0, new BigDecimal("9950000").compareTo(mbBankAccount.getBalance()));
        verify(webhookNotifier, times(1)).notifyWebhookProcessed(any());

        // Khi SePay retry lần 2 cùng externalId -> trả về DUPLICATE, không trừ tiền lần nữa
        BankWebhookEvent existing = savedEvents.get(result1.getEventId());
        when(eventRepository.findByProviderAndExternalId("SEPAY", "SEPAY_92704"))
                .thenReturn(Optional.of(existing));

        BankWebhookProcessResult result2 = webhookService.processEventWithRetry(event, null, null);
        assertEquals(BankWebhookStatus.DUPLICATE.name(), result2.getStatus());
        assertEquals(0, new BigDecimal("9950000").compareTo(mbBankAccount.getBalance()));
    }

    @Test
    @DisplayName("2. Không bao giờ lỗi Insufficient Balance khi ví trên web đang có số dư 0đ mà nhận giao dịch trừ tiền từ SePay")
    void testZeroBalanceWalletAutoSyncsInsteadOfInsufficientBalance() {
        mbBankAccount.setBalance(BigDecimal.ZERO);

        BankTransactionEvent outEvent = BankTransactionEvent.builder()
                .provider(BankWebhookProvider.SEPAY)
                .externalId("SEPAY_OUT_ZERO")
                .sourceKey("0987654321")
                .gateway("MBBank")
                .direction(BankDirection.OUT)
                .eventKind(BankEventKind.PAYMENT)
                .fundingSource(BankFundingSource.BANK_ACCOUNT)
                .amount(new BigDecimal("100000"))
                .bankBalance(new BigDecimal("2450000"))
                .content("CHUYEN TIEN AN TRUA")
                .occurredAt(LocalDateTime.now())
                .parsed(true)
                .build();

        when(eventRepository.findByProviderAndExternalId("SEPAY", "SEPAY_OUT_ZERO"))
                .thenReturn(Optional.empty());

        BankWebhookProcessResult res = webhookService.processEventWithRetry(outEvent, null, null);
        assertEquals(BankWebhookStatus.PROCESSED.name(), res.getStatus());
        assertNotNull(res.getTransactionId());
        assertEquals(0, new BigDecimal("2450000").compareTo(mbBankAccount.getBalance()));
        verify(webhookNotifier, times(1)).notifyWebhookProcessed(any());
    }

    @Test
    @DisplayName("3. Quy tắc phân loại tùy chỉnh (BankCategoryRule) được ưu tiên hơn NLP mặc định")
    void testCustomBankCategoryRulePriority() {
        BankCategoryRule customRule = new BankCategoryRule()
                .setUserId(testUser.getId())
                .setKeyword("ACME CORP")
                .setCategory(Category.SALARY)
                .setDirection("IN")
                .setPriority(200)
                .setActive(true);

        when(ruleRepository.findAllByUserIdAndActiveTrueOrderByPriorityDescCreatedAtAsc(testUser.getId()))
                .thenReturn(List.of(customRule));

        BankTransactionEvent incomeEvent = BankTransactionEvent.builder()
                .provider(BankWebhookProvider.SEPAY)
                .externalId("SEPAY_INC_1")
                .gateway("MBBank")
                .direction(BankDirection.IN)
                .amount(new BigDecimal("25000000"))
                .content("ACME CORP CHUYEN KHOAN")
                .parsed(true)
                .build();

        int cat = classifier.classify(testUser.getId(), incomeEvent, TransactionType.INCOME);
        assertEquals(Category.SALARY, cat);
    }
}
