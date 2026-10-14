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
                telegramService
        );

        sampleUser = new User();
        sampleUser.setId(UUID.randomUUID());
        sampleUser.setName("Nguyen Huy");
        sampleUser.setEmail("nghyqn1201@gmail.com");
        sampleUser.setStatus(Status.ACTIVE);
        sampleUser.setTelegramChatId(ownerChatId);
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
}
