package com.example.financial_management.telegram;

import com.example.financial_management.constant.Category;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.User;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;
import com.example.financial_management.services.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramCommandHandlerTest {

    @Mock
    private TelegramBotClient botClient;

    @Mock
    private TelegramMessageParser messageParser;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private TelegramService telegramService;

    @Mock
    private com.example.financial_management.services.BudgetService budgetService;

    @Mock
    private com.example.financial_management.repository.TagRepository tagRepository;

    private TelegramBotConfig config;
    private TelegramCommandHandler commandHandler;

    private User sampleUser;
    private long ownerChatId = 123456789L;
    private long strangerChatId = 987654321L;

    @BeforeEach
    void setUp() {
        config = new TelegramBotConfig();
        config.setEnabled(true);
        config.setToken("123456:ABC-DEF");
        config.setUsername("bang_linh_bot");
        config.setDefaultUserEmail("nghyqn1201@gmail.com");

        commandHandler = new TelegramCommandHandler(
                config,
                botClient,
                messageParser,
                userRepository,
                accountRepository,
                transactionRepository,
                transactionService,
                telegramService,
                budgetService,
                tagRepository
        );

        sampleUser = new User();
        sampleUser.setId(UUID.randomUUID());
        sampleUser.setName("Nguyen Huy");
        sampleUser.setEmail("nghyqn1201@gmail.com");
        sampleUser.setStatus(Status.ACTIVE);
        sampleUser.setTelegramChatId(ownerChatId);
        sampleUser.setTelegramDailyReportEnabled(true);
        sampleUser.setTelegramBudgetAlertEnabled(true);
    }

    @Test
    @DisplayName("Whitelist Guard: Chặn người lạ chưa liên kết khi nhắn tin chi tiêu hoặc tra cứu")
    void testWhitelistGuardBlocksStranger() {
        when(userRepository.findByTelegramChatIdAndStatus(strangerChatId, Status.ACTIVE))
                .thenReturn(Optional.empty());

        commandHandler.handleIncomingMessage(strangerChatId, "50k cafe");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient).sendMessage(eq(strangerChatId), captor.capture());

        assertTrue(captor.getValue().contains("Bạn chưa liên kết tài khoản!"));
        assertTrue(captor.getValue().contains("Whitelist Guard"));
        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("Xử lý Deep link /start LINK_123456 để liên kết tài khoản an toàn")
    void testDeepLinkStartCommand() {
        when(telegramService.linkUserByCode("LINK_849201", strangerChatId))
                .thenReturn(Optional.of(sampleUser));

        commandHandler.handleIncomingMessage(strangerChatId, "/start LINK_849201");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient).sendMessage(eq(strangerChatId), captor.capture());

        assertTrue(captor.getValue().contains("LIÊN KẾT TÀI KHOẢN THÀNH CÔNG!"));
        assertTrue(captor.getValue().contains("Nguyen Huy"));
    }

    @Test
    @DisplayName("Xử lý lệnh /link <mã_otp> thành công")
    void testLinkCommandWithOtpCode() {
        when(telegramService.linkUserByCode("849201", strangerChatId))
                .thenReturn(Optional.of(sampleUser));

        commandHandler.handleIncomingMessage(strangerChatId, "/link 849201");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient).sendMessage(eq(strangerChatId), captor.capture());

        assertTrue(captor.getValue().contains("LIÊN KẾT TÀI KHOẢN THÀNH CÔNG!"));
    }

    @Test
    @DisplayName("Cảnh báo an toàn và từ chối khi người dùng nhập mật khẩu qua /link email password")
    void testLinkCommandRejectsPasswordInChat() {
        commandHandler.handleIncomingMessage(strangerChatId, "/link test@gmail.com 123456");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient).sendMessage(eq(strangerChatId), captor.capture());

        assertTrue(captor.getValue().contains("NÂNG CẤP AN TOÀN BẢO MẬT"));
        assertTrue(captor.getValue().contains("không còn hỗ trợ gửi mật khẩu"));
        verifyNoInteractions(telegramService);
    }

    @Test
    @DisplayName("Cho phép người dùng đã liên kết thực hiện lệnh tra cứu số dư /sodu")
    void testAuthorizedUserCanCheckBalance() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account acc = new Account();
        acc.setName("Vietcombank");
        acc.setBalance(new BigDecimal("10000000"));
        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(acc));

        commandHandler.handleIncomingMessage(ownerChatId, "/sodu");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient).sendMessage(eq(ownerChatId), captor.capture());

        assertTrue(captor.getValue().contains("SỐ DƯ CÁC VÍ & TÀI KHOẢN"));
        assertTrue(captor.getValue().contains("Vietcombank"));
    }

    @Test
    @DisplayName("Thông báo không tìm thấy ví khi người dùng gõ 'cash' mà tài khoản chưa có ví tiền mặt")
    void testNotifyWhenCashWalletNotFound() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account vcb = new Account();
        vcb.setId(UUID.randomUUID());
        vcb.setName("VCB");
        vcb.setBalance(new BigDecimal("1000000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(vcb));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("100000"))
                .type(TransactionType.EXPENSE)
                .category(Category.TRANSPORT)
                .categoryName("Di chuyển 🛵")
                .accountKeyword("Tiền mặt")
                .rawAccountKeyword("cash")
                .description("Do xang")
                .build();

        when(messageParser.parse(eq("100 do xang cash"), any())).thenReturn(parsed);

        commandHandler.handleIncomingMessage(ownerChatId, "100 do xang cash");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient).sendMessage(eq(ownerChatId), captor.capture());

        assertTrue(captor.getValue().contains("Không tìm thấy ví: cash (Tiền mặt)"));
        assertTrue(captor.getValue().contains("VCB"));
        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("Trừ đúng ví Tiền mặt khi người dùng gõ 'cash' và tài khoản đã có ví Tiền mặt")
    void testUseCashWalletWhenUserHasIt() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account cashAcc = new Account();
        cashAcc.setId(UUID.randomUUID());
        cashAcc.setName("Tiền mặt");
        cashAcc.setBalance(new BigDecimal("500000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(cashAcc));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("100000"))
                .type(TransactionType.EXPENSE)
                .category(Category.TRANSPORT)
                .categoryName("Di chuyển 🛵")
                .accountKeyword("Tiền mặt")
                .rawAccountKeyword("cash")
                .description("Do xang")
                .build();

        when(messageParser.parse(eq("100 do xang cash"), any())).thenReturn(parsed);
        when(accountRepository.findById(cashAcc.getId())).thenReturn(Optional.of(cashAcc));

        commandHandler.handleIncomingMessage(ownerChatId, "100 do xang cash");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient).sendMessage(eq(ownerChatId), captor.capture());

        assertTrue(captor.getValue().contains("Ví: <b>Tiền mặt</b>"));
        verify(transactionService).createTransaction(any(), any(), any());
    }

    @Test
    @DisplayName("Từ chối và cảnh báo khi người dùng chỉ định ví không tồn tại (không âm thầm trừ ví khác)")
    void testRejectNonExistentSpecifiedWallet() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account vcb = new Account();
        vcb.setName("VCB");

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(vcb));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("100000"))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .categoryName("Ăn uống 🍜")
                .accountKeyword("BIDV")
                .description("An toi")
                .build();

        when(messageParser.parse(eq("100k an toi bidv"), any())).thenReturn(parsed);

        commandHandler.handleIncomingMessage(ownerChatId, "100k an toi bidv");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient).sendMessage(eq(ownerChatId), captor.capture());

        assertTrue(captor.getValue().contains("Không tìm thấy ví: BIDV"));
        assertTrue(captor.getValue().contains("VCB"));
        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("Lệnh /help trả về cẩm nang hướng dẫn cú pháp chi tiết và đầy đủ")
    void testHelpCommandReturnsDetailedGuide() {
        commandHandler.handleIncomingMessage(strangerChatId, "/help");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient).sendMessage(eq(strangerChatId), captor.capture());

        String msg = captor.getValue();
        assertTrue(msg.contains("CẨM NANG HƯỚNG DẪN CÚ PHÁP SỬ DỤNG BOT"));
        assertTrue(msg.contains("CÚ PHÁP GHI CHÉP TỰ NHIÊN"));
        assertTrue(msg.contains("QUY TẮC NHẬP SỐ TIỀN CỰC KỲ LINH HOẠT"));
        assertTrue(msg.contains("CHỈ ĐỊNH VÍ NGUỒN THANH TOÁN"));
        assertTrue(msg.contains("GHI NHẬN KHOẢN THU NHẬP (INCOME)"));
        assertTrue(msg.contains("TỰ ĐỘNG PHÂN LOẠI DANH MỤC"));
        assertTrue(msg.contains("SỬA SAI VÀ HOÀN TÁC (UNDO)"));
        assertTrue(msg.contains("DANH MỤC CÁC LỆNH HỆ THỐNG"));
        assertTrue(msg.contains("/sodu"));
        assertTrue(msg.contains("/vi"));
        assertTrue(msg.contains("/homnay"));
        assertTrue(msg.contains("/thangnay"));
        assertTrue(msg.contains("/huy"));
    }

    @Test
    @DisplayName("Alias /huongdan cũng trả về cẩm nang cú pháp như /help")
    void testHuongDanAliasWorks() {
        commandHandler.handleIncomingMessage(ownerChatId, "/huongdan");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient).sendMessage(eq(ownerChatId), captor.capture());

        String msg = captor.getValue();
        assertTrue(msg.contains("CẨM NANG HƯỚNG DẪN CÚ PHÁP SỬ DỤNG BOT"));
    }

    @Test
    @DisplayName("Cảnh báo ngân sách tức thì: Bắn cảnh báo đỏ khi chi tiêu khiến ngân sách danh mục đạt >= 90%")
    void testExpenseWithBudgetExceeding90PercentSendsAlert() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account cashAcc = new Account();
        cashAcc.setId(UUID.randomUUID());
        cashAcc.setName("Tiền mặt");
        cashAcc.setBalance(new BigDecimal("1000000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(cashAcc));
        when(accountRepository.findById(cashAcc.getId())).thenReturn(Optional.of(cashAcc));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("150000"))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .categoryName("Ăn uống 🍜")
                .description("An trua")
                .build();

        when(messageParser.parse(eq("150k an trua"), any())).thenReturn(parsed);
        when(messageParser.getCategoryVietnameseName(Category.FOOD)).thenReturn("Ăn uống 🍜");

        com.example.financial_management.model.budget.BudgetCheckingResponse budgetResp =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        budgetResp.setCategory(Category.FOOD);
        budgetResp.setAmount(new BigDecimal("5000000"));
        budgetResp.setSpending(new BigDecimal("4625000"));
        budgetResp.setUsedPercentage(new BigDecimal("92.5"));

        when(budgetService.checkingBudget(anyInt(), anyInt(), any()))
                .thenReturn(List.of(budgetResp));

        commandHandler.handleIncomingMessage(ownerChatId, "150k an trua");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient, times(2)).sendMessage(eq(ownerChatId), captor.capture());

        List<String> messages = captor.getAllValues();
        // Tin nhắn 1: Thông báo ghi nhận chi tiêu
        assertTrue(messages.get(0).contains("ĐÃ THÊM CHI TIÊU"));
        // Tin nhắn 2: Cảnh báo đỏ vượt 90% hạn mức ngân sách
        String alertMsg = messages.get(1);
        assertTrue(alertMsg.contains("CẢNH BÁO VƯỢT HẠN MỨC NGÂN SÁCH!"));
        assertTrue(alertMsg.contains("Ăn uống 🍜"));
        assertTrue(alertMsg.contains("92.5%"));
        assertTrue(alertMsg.contains("4.625.000 ₫"));
        assertTrue(alertMsg.contains("5.000.000 ₫"));
        assertTrue(alertMsg.contains("Bạn sắp chạm trần ngân sách cho tháng này!"));
    }

    @Test
    @DisplayName("Cảnh báo ngân sách tức thì: Báo động đã vượt quá trần ngân sách khi usedPercentage >= 100%")
    void testExpenseWithBudgetExceeding100PercentSendsAlert() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account cashAcc = new Account();
        cashAcc.setId(UUID.randomUUID());
        cashAcc.setName("Tiền mặt");
        cashAcc.setBalance(new BigDecimal("1000000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(cashAcc));
        when(accountRepository.findById(cashAcc.getId())).thenReturn(Optional.of(cashAcc));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("200000"))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .categoryName("Ăn uống 🍜")
                .description("Lau bo")
                .build();

        when(messageParser.parse(eq("200k lau bo"), any())).thenReturn(parsed);
        when(messageParser.getCategoryVietnameseName(Category.FOOD)).thenReturn("Ăn uống 🍜");

        com.example.financial_management.model.budget.BudgetCheckingResponse budgetResp =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        budgetResp.setCategory(Category.FOOD);
        budgetResp.setAmount(new BigDecimal("5000000"));
        budgetResp.setSpending(new BigDecimal("5250000"));
        budgetResp.setUsedPercentage(new BigDecimal("105.0"));

        when(budgetService.checkingBudget(anyInt(), anyInt(), any()))
                .thenReturn(List.of(budgetResp));

        commandHandler.handleIncomingMessage(ownerChatId, "200k lau bo");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient, times(2)).sendMessage(eq(ownerChatId), captor.capture());

        String alertMsg = captor.getAllValues().get(1);
        assertTrue(alertMsg.contains("105.0%"));
        assertTrue(alertMsg.contains("Bạn đã vượt quá hạn mức ngân sách cho tháng này!"));
    }

    @Test
    @DisplayName("Không gửi cảnh báo ngân sách khi usedPercentage < 90%")
    void testExpenseWithBudgetBelow90PercentNoAlert() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account cashAcc = new Account();
        cashAcc.setId(UUID.randomUUID());
        cashAcc.setName("Tiền mặt");
        cashAcc.setBalance(new BigDecimal("1000000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(cashAcc));
        when(accountRepository.findById(cashAcc.getId())).thenReturn(Optional.of(cashAcc));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("50000"))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .categoryName("Ăn uống 🍜")
                .description("Cafe")
                .build();

        when(messageParser.parse(eq("50k cafe"), any())).thenReturn(parsed);

        com.example.financial_management.model.budget.BudgetCheckingResponse budgetResp =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        budgetResp.setCategory(Category.FOOD);
        budgetResp.setAmount(new BigDecimal("5000000"));
        budgetResp.setSpending(new BigDecimal("3500000"));
        budgetResp.setUsedPercentage(new BigDecimal("70.0"));

        when(budgetService.checkingBudget(anyInt(), anyInt(), any()))
                .thenReturn(List.of(budgetResp));

        commandHandler.handleIncomingMessage(ownerChatId, "50k cafe");

        // Chỉ có 1 tin nhắn thông báo giao dịch được gửi, không có cảnh báo
        verify(botClient, times(1)).sendMessage(eq(ownerChatId), anyString());
    }

    @Test
    @DisplayName("Không gửi cảnh báo ngân sách nếu người dùng tắt cấu hình budgetAlertEnabled")
    void testExpenseWhenBudgetAlertDisabledNoAlert() {
        sampleUser.setTelegramBudgetAlertEnabled(false);
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account cashAcc = new Account();
        cashAcc.setId(UUID.randomUUID());
        cashAcc.setName("Tiền mặt");
        cashAcc.setBalance(new BigDecimal("1000000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(cashAcc));
        when(accountRepository.findById(cashAcc.getId())).thenReturn(Optional.of(cashAcc));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("50000"))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .categoryName("Ăn uống 🍜")
                .description("Cafe")
                .build();

        when(messageParser.parse(eq("50k cafe"), any())).thenReturn(parsed);

        commandHandler.handleIncomingMessage(ownerChatId, "50k cafe");

        // Chỉ có 1 tin nhắn thêm giao dịch được gửi, không tương tác với budgetService
        verify(botClient, times(1)).sendMessage(eq(ownerChatId), anyString());
        verifyNoInteractions(budgetService);
    }

    @Test
    @DisplayName("Cảnh báo ngân sách có tag: Bắn cảnh báo đúng thẻ khi chi tiêu có hashtag khớp với ngân sách tag")
    void testExpenseWithTagTriggersTaggedBudgetAlert() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account cashAcc = new Account();
        cashAcc.setId(UUID.randomUUID());
        cashAcc.setName("Tiền mặt");
        cashAcc.setBalance(new BigDecimal("1000000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(cashAcc));
        when(accountRepository.findById(cashAcc.getId())).thenReturn(Optional.of(cashAcc));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("50000"))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .categoryName("Ăn uống 🍜")
                .description("Cf")
                .tags(List.of("cafe"))
                .build();

        when(messageParser.parse(eq("50k cf #cafe"), any())).thenReturn(parsed);
        when(messageParser.getCategoryVietnameseName(Category.FOOD)).thenReturn("Ăn uống 🍜");

        // Ngân sách 1: Ăn uống chung (chưa chạm ngưỡng, 50%)
        com.example.financial_management.model.budget.BudgetCheckingResponse generalBudget =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        generalBudget.setCategory(Category.FOOD);
        generalBudget.setAmount(new BigDecimal("5000000"));
        generalBudget.setSpending(new BigDecimal("2500000"));
        generalBudget.setUsedPercentage(new BigDecimal("50.0"));

        // Ngân sách 2: Ăn uống gắn thẻ #cafe (vượt ngưỡng 95%)
        com.example.financial_management.model.budget.BudgetCheckingResponse cafeBudget =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        cafeBudget.setCategory(Category.FOOD);
        cafeBudget.setTagName("cafe");
        cafeBudget.setAmount(new BigDecimal("1000000"));
        cafeBudget.setSpending(new BigDecimal("950000"));
        cafeBudget.setUsedPercentage(new BigDecimal("95.0"));

        when(budgetService.checkingBudget(anyInt(), anyInt(), any()))
                .thenReturn(List.of(generalBudget, cafeBudget));

        commandHandler.handleIncomingMessage(ownerChatId, "50k cf #cafe");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient, times(2)).sendMessage(eq(ownerChatId), captor.capture());

        List<String> messages = captor.getAllValues();
        String alertMsg = messages.get(1);
        assertTrue(alertMsg.contains("CẢNH BÁO VƯỢT HẠN MỨC NGÂN SÁCH!"));
        assertTrue(alertMsg.contains("Ăn uống 🍜 (Thẻ #cafe)"));
        assertTrue(alertMsg.contains("95.0%"));
        assertTrue(alertMsg.contains("950.000 ₫"));
        assertTrue(alertMsg.contains("1.000.000 ₫"));
        assertTrue(alertMsg.contains("Bạn sắp chạm trần ngân sách cho tháng này!"));
    }

    @Test
    @DisplayName("Cảnh báo ngân sách chung: Khoản chi không có tag sẽ tính vào ngân sách chung (trừ tag)")
    void testExpenseWithoutTagTriggersGeneralBudgetAlertExcludingTaggedBudget() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account cashAcc = new Account();
        cashAcc.setId(UUID.randomUUID());
        cashAcc.setName("Tiền mặt");
        cashAcc.setBalance(new BigDecimal("1000000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(cashAcc));
        when(accountRepository.findById(cashAcc.getId())).thenReturn(Optional.of(cashAcc));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("40000"))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .categoryName("Ăn uống 🍜")
                .description("Com trua")
                .tags(List.of())
                .build();

        when(messageParser.parse(eq("40k com trua"), any())).thenReturn(parsed);
        when(messageParser.getCategoryVietnameseName(Category.FOOD)).thenReturn("Ăn uống 🍜");

        // Ngân sách 1: Ăn uống chung (đã chạm ngưỡng 92.5%)
        com.example.financial_management.model.budget.BudgetCheckingResponse generalBudget =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        generalBudget.setCategory(Category.FOOD);
        generalBudget.setAmount(new BigDecimal("4000000"));
        generalBudget.setSpending(new BigDecimal("3700000"));
        generalBudget.setUsedPercentage(new BigDecimal("92.5"));

        // Ngân sách 2: Ăn uống gắn thẻ #cafe (chỉ mới 40%)
        com.example.financial_management.model.budget.BudgetCheckingResponse cafeBudget =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        cafeBudget.setCategory(Category.FOOD);
        cafeBudget.setTagName("cafe");
        cafeBudget.setAmount(new BigDecimal("1000000"));
        cafeBudget.setSpending(new BigDecimal("400000"));
        cafeBudget.setUsedPercentage(new BigDecimal("40.0"));

        when(budgetService.checkingBudget(anyInt(), anyInt(), any()))
                .thenReturn(List.of(generalBudget, cafeBudget));

        commandHandler.handleIncomingMessage(ownerChatId, "40k com trua");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient, times(2)).sendMessage(eq(ownerChatId), captor.capture());

        String alertMsg = captor.getAllValues().get(1);
        assertTrue(alertMsg.contains("CẢNH BÁO VƯỢT HẠN MỨC NGÂN SÁCH!"));
        assertTrue(alertMsg.contains("Ăn uống 🍜"));
        assertFalse(alertMsg.contains("Thẻ #cafe"));
        assertTrue(alertMsg.contains("92.5%"));
    }

    @Test
    @DisplayName("Khoản chi có tag không kích hoạt cảnh báo ngân sách chung ngay cả khi ngân sách chung >= 90%")
    void testExpenseWithTagDoesNotTriggerGeneralBudgetAlertEvenIfGeneralExceeds90() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account cashAcc = new Account();
        cashAcc.setId(UUID.randomUUID());
        cashAcc.setName("Tiền mặt");
        cashAcc.setBalance(new BigDecimal("1000000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(cashAcc));
        when(accountRepository.findById(cashAcc.getId())).thenReturn(Optional.of(cashAcc));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("50000"))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .categoryName("Ăn uống 🍜")
                .description("Cf")
                .tags(List.of("cafe"))
                .build();

        when(messageParser.parse(eq("50k cf #cafe"), any())).thenReturn(parsed);

        // Ngân sách 1: Ăn uống chung (vượt ngưỡng 95%)
        com.example.financial_management.model.budget.BudgetCheckingResponse generalBudget =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        generalBudget.setCategory(Category.FOOD);
        generalBudget.setAmount(new BigDecimal("4000000"));
        generalBudget.setSpending(new BigDecimal("3800000"));
        generalBudget.setUsedPercentage(new BigDecimal("95.0"));

        // Ngân sách 2: Ăn uống gắn thẻ #cafe (mới 50%, an toàn)
        com.example.financial_management.model.budget.BudgetCheckingResponse cafeBudget =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        cafeBudget.setCategory(Category.FOOD);
        cafeBudget.setTagName("cafe");
        cafeBudget.setAmount(new BigDecimal("1000000"));
        cafeBudget.setSpending(new BigDecimal("500000"));
        cafeBudget.setUsedPercentage(new BigDecimal("50.0"));

        when(budgetService.checkingBudget(anyInt(), anyInt(), any()))
                .thenReturn(List.of(generalBudget, cafeBudget));

        commandHandler.handleIncomingMessage(ownerChatId, "50k cf #cafe");

        // Vì khoản chi này thuộc thẻ #cafe (ngân sách cafe mới 50%), và bị loại trừ khỏi ngân sách chung
        // nên không có cảnh báo nào được gửi!
        verify(botClient, times(1)).sendMessage(eq(ownerChatId), anyString());
    }

    @Test
    @DisplayName("Gắn tag chưa có ngân sách riêng: Tự động hạch toán vào ngân sách chung và cảnh báo nếu ngân sách chung >= 90%")
    void testExpenseWithUntrackedTagFallsBackToGeneralBudgetAndAlerts() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account cashAcc = new Account();
        cashAcc.setId(UUID.randomUUID());
        cashAcc.setName("Tiền mặt");
        cashAcc.setBalance(new BigDecimal("1000000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(cashAcc));
        when(accountRepository.findById(cashAcc.getId())).thenReturn(Optional.of(cashAcc));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("40000"))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .categoryName("Ăn uống 🍜")
                .description("Tra sua")
                .tags(List.of("banbe"))
                .build();

        when(messageParser.parse(eq("40k tra sua #banbe"), any())).thenReturn(parsed);
        when(messageParser.getCategoryVietnameseName(Category.FOOD)).thenReturn("Ăn uống 🍜");

        // Ngân sách 1: Ăn uống chung (vượt ngưỡng 92%)
        com.example.financial_management.model.budget.BudgetCheckingResponse generalBudget =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        generalBudget.setCategory(Category.FOOD);
        generalBudget.setAmount(new BigDecimal("5000000"));
        generalBudget.setSpending(new BigDecimal("4600000"));
        generalBudget.setUsedPercentage(new BigDecimal("92.0"));

        // Ngân sách 2: Ăn uống gắn thẻ #cafe (chỉ mới 30%)
        com.example.financial_management.model.budget.BudgetCheckingResponse cafeBudget =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        cafeBudget.setCategory(Category.FOOD);
        cafeBudget.setTagName("cafe");
        cafeBudget.setAmount(new BigDecimal("1000000"));
        cafeBudget.setSpending(new BigDecimal("300000"));
        cafeBudget.setUsedPercentage(new BigDecimal("30.0"));

        when(budgetService.checkingBudget(anyInt(), anyInt(), any()))
                .thenReturn(List.of(generalBudget, cafeBudget));

        commandHandler.handleIncomingMessage(ownerChatId, "40k tra sua #banbe");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient, times(2)).sendMessage(eq(ownerChatId), captor.capture());

        List<String> messages = captor.getAllValues();
        String alertMsg = messages.get(1);
        assertTrue(alertMsg.contains("CẢNH BÁO VƯỢT HẠN MỨC NGÂN SÁCH!"));
        assertTrue(alertMsg.contains("Ăn uống 🍜"));
        assertFalse(alertMsg.contains("Thẻ #cafe"));
        assertTrue(alertMsg.contains("92.0%"));
    }

    @Test
    @DisplayName("Ghi chép giao dịch có tag: Hiển thị số tiền còn lại và % ngân sách, đồng thời gửi cảnh báo khi >= 90%")
    void testExpenseWithTagCalculatesRemainingAndPercentageAndAlertsWhenAbove90Percent() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account cashAcc = new Account();
        cashAcc.setId(UUID.randomUUID());
        cashAcc.setName("Tiền mặt");
        cashAcc.setBalance(new BigDecimal("1000000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(cashAcc));
        when(accountRepository.findById(cashAcc.getId())).thenReturn(Optional.of(cashAcc));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("50000"))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .categoryName("Ăn uống 🍜")
                .description("Ca phe Highland")
                .tags(List.of("cafe"))
                .build();

        when(messageParser.parse(eq("50k ca phe Highland #cafe"), any())).thenReturn(parsed);
        when(messageParser.getCategoryVietnameseName(Category.FOOD)).thenReturn("Ăn uống 🍜");

        com.example.financial_management.model.budget.BudgetCheckingResponse cafeBudget =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        cafeBudget.setCategory(Category.FOOD);
        cafeBudget.setTagName("cafe");
        cafeBudget.setAmount(new BigDecimal("1000000"));
        cafeBudget.setSpending(new BigDecimal("950000"));
        cafeBudget.setUsedPercentage(new BigDecimal("95.0"));

        when(budgetService.checkingBudget(anyInt(), anyInt(), any()))
                .thenReturn(List.of(cafeBudget));

        commandHandler.handleIncomingMessage(ownerChatId, "50k ca phe Highland #cafe");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient, times(2)).sendMessage(eq(ownerChatId), captor.capture());

        List<String> messages = captor.getAllValues();
        String confirmMsg = messages.get(0);
        String alertMsg = messages.get(1);

        // Tin nhắn xác nhận giao dịch phải hiển thị ngân sách thẻ, số tiền còn lại và % ngân sách
        assertTrue(confirmMsg.contains("Ngân sách Ăn uống 🍜 (Thẻ #cafe)"));
        assertTrue(confirmMsg.contains("Còn lại <code>50.000 ₫</code>"));
        assertTrue(confirmMsg.contains("chiếm 95.0%"));

        // Tin nhắn cảnh báo phải hiển thị rõ tỷ lệ %, hạn mức, đã chi và số tiền còn lại
        assertTrue(alertMsg.contains("🚨 <b>CẢNH BÁO VƯỢT HẠN MỨC NGÂN SÁCH!</b>"));
        assertTrue(alertMsg.contains("Ăn uống 🍜 (Thẻ #cafe)"));
        assertTrue(alertMsg.contains("Chiếm ngân sách: <b>95.0%</b>"));
        assertTrue(alertMsg.contains("Đã chi: <b>950.000 ₫</b> / Hạn mức: <b>1.000.000 ₫</b>"));
        assertTrue(alertMsg.contains("Số tiền còn lại: <code>50.000 ₫</code>"));
        assertTrue(alertMsg.contains("Bạn sắp chạm trần ngân sách cho tháng này!"));
    }

    @Test
    @DisplayName("Khớp ngân sách thẻ qua TagId khi tagName bị null trong DTO")
    void testExpenseWithTagMatchingTagIdViaTagRepository() {
        when(userRepository.findByTelegramChatIdAndStatus(ownerChatId, Status.ACTIVE))
                .thenReturn(Optional.of(sampleUser));

        Account cashAcc = new Account();
        cashAcc.setId(UUID.randomUUID());
        cashAcc.setName("Tiền mặt");
        cashAcc.setBalance(new BigDecimal("1000000"));

        when(accountRepository.findAllByUserIdAndStatus(sampleUser.getId(), Status.ACTIVE))
                .thenReturn(List.of(cashAcc));
        when(accountRepository.findById(cashAcc.getId())).thenReturn(Optional.of(cashAcc));

        TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                .success(true)
                .amount(new BigDecimal("70000"))
                .type(TransactionType.EXPENSE)
                .category(Category.TRANSPORT)
                .categoryName("Di chuyển 🛵")
                .description("Xang")
                .tags(List.of("di-lai"))
                .build();

        when(messageParser.parse(eq("xang 70k #di-lai"), any())).thenReturn(parsed);
        when(messageParser.getCategoryVietnameseName(Category.TRANSPORT)).thenReturn("Di chuyển 🛵");

        UUID tagId = UUID.randomUUID();
        com.example.financial_management.entity.Tag tagEntity = new com.example.financial_management.entity.Tag();
        tagEntity.setId(tagId);
        tagEntity.setName("di-lai");

        when(tagRepository.findAllByUserIdAndNameIn(eq(sampleUser.getId()), any()))
                .thenReturn(List.of(tagEntity));

        com.example.financial_management.model.budget.BudgetCheckingResponse taggedBudget =
                new com.example.financial_management.model.budget.BudgetCheckingResponse();
        taggedBudget.setCategory(Category.TRANSPORT);
        taggedBudget.setTagId(tagId);
        taggedBudget.setTagName(null);
        taggedBudget.setAmount(new BigDecimal("1000000"));
        taggedBudget.setSpending(new BigDecimal("910000"));
        taggedBudget.setUsedPercentage(new BigDecimal("91.0"));

        when(budgetService.checkingBudget(anyInt(), anyInt(), any()))
                .thenReturn(List.of(taggedBudget));

        commandHandler.handleIncomingMessage(ownerChatId, "xang 70k #di-lai");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botClient, times(2)).sendMessage(eq(ownerChatId), captor.capture());

        String alertMsg = captor.getAllValues().get(1);
        assertTrue(alertMsg.contains("CẢNH BÁO VƯỢT HẠN MỨC NGÂN SÁCH!"));
        assertTrue(alertMsg.contains("Di chuyển 🛵"));
        assertTrue(alertMsg.contains("91.0%"));
        assertTrue(alertMsg.contains("Số tiền còn lại: <code>90.000 ₫</code>"));
    }
}
