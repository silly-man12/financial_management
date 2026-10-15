package com.example.financial_management.telegram;

import com.example.financial_management.constant.Status;
import com.example.financial_management.entity.User;
import com.example.financial_management.model.telegram.TelegramLinkCodeResponse;
import com.example.financial_management.model.telegram.TelegramSettingsRequest;
import com.example.financial_management.model.telegram.TelegramStatusResponse;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;
import com.example.financial_management.services.BudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramServiceTest {

    @Mock
    private TelegramBotClient botClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetService budgetService;

    private TelegramBotConfig config;
    private TelegramService telegramService;

    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        config = new TelegramBotConfig();
        config.setEnabled(true);
        config.setToken("123456:ABC-DEF");
        config.setUsername("bang_linh_bot");

        telegramService = new TelegramService(config, botClient, userRepository, transactionRepository, budgetService);

        userId = UUID.randomUUID();
        sampleUser = new User();
        sampleUser.setId(userId);
        sampleUser.setName("Nguyen Huy");
        sampleUser.setEmail("nghyqn1201@gmail.com");
        sampleUser.setStatus(Status.ACTIVE);
        sampleUser.setTelegramDailyReportEnabled(true);
        sampleUser.setTelegramBudgetAlertEnabled(true);
    }

    @Test
    @DisplayName("Tạo mã OTP 6 số thành công với deep link và thời hạn 10 phút")
    void testGenerateLinkCode() {
        when(userRepository.findByIdAndStatus(userId, Status.ACTIVE)).thenReturn(Optional.of(sampleUser));

        TelegramLinkCodeResponse response = telegramService.generateLinkCode(userId);

        assertNotNull(response);
        assertNotNull(response.getCode());
        assertEquals(6, response.getCode().length());
        assertTrue(response.getCode().matches("\\d{6}"));
        assertNotNull(response.getExpiredAt());
        assertNotNull(response.getDeepLink());
        assertTrue(response.getDeepLink().startsWith("https://t.me/bang_linh_bot?start=LINK_"));
        assertTrue(response.getDeepLink().contains(response.getCode()));
    }

    @Test
    @DisplayName("Liên kết thành công khi nhập đúng mã OTP 6 số và tự động xóa mã sau khi dùng")
    void testLinkUserByCodeSuccess() {
        when(userRepository.findByIdAndStatus(userId, Status.ACTIVE)).thenReturn(Optional.of(sampleUser));
        when(userRepository.findByTelegramChatId(99887766L)).thenReturn(Optional.empty());

        TelegramLinkCodeResponse codeResponse = telegramService.generateLinkCode(userId);
        String code = codeResponse.getCode();

        // Thử liên kết với mã thô (6 số)
        Optional<User> linkedUser = telegramService.linkUserByCode(code, 99887766L);

        assertTrue(linkedUser.isPresent());
        assertEquals(99887766L, linkedUser.get().getTelegramChatId());
        verify(userRepository, times(1)).save(sampleUser);

        // Mã đã được dùng nên lần gọi tiếp theo phải thất bại (One-Time Password)
        Optional<User> reusedAttempt = telegramService.linkUserByCode(code, 99887766L);
        assertTrue(reusedAttempt.isEmpty());
    }

    @Test
    @DisplayName("Liên kết thành công khi tham số có tiền tố LINK_ từ Deep Link Telegram")
    void testLinkUserByCodeWithPrefix() {
        when(userRepository.findByIdAndStatus(userId, Status.ACTIVE)).thenReturn(Optional.of(sampleUser));
        when(userRepository.findByTelegramChatId(11223344L)).thenReturn(Optional.empty());

        TelegramLinkCodeResponse codeResponse = telegramService.generateLinkCode(userId);
        String rawParam = "LINK_" + codeResponse.getCode();

        Optional<User> linkedUser = telegramService.linkUserByCode(rawParam, 11223344L);

        assertTrue(linkedUser.isPresent());
        assertEquals(11223344L, linkedUser.get().getTelegramChatId());
    }

    @Test
    @DisplayName("Liên kết thất bại nếu mã không tồn tại hoặc sai")
    void testLinkUserByInvalidCode() {
        Optional<User> result = telegramService.linkUserByCode("999999", 11223344L);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Hủy liên kết Telegram thành công từ Web")
    void testUnlinkUser() {
        sampleUser.setTelegramChatId(88776655L);
        when(userRepository.findByIdAndStatus(userId, Status.ACTIVE)).thenReturn(Optional.of(sampleUser));

        telegramService.unlinkUser(userId);

        assertNull(sampleUser.getTelegramChatId());
        verify(userRepository, times(1)).save(sampleUser);
    }

    @Test
    @DisplayName("Lấy trạng thái kết nối Telegram chính xác")
    void testGetStatus() {
        sampleUser.setTelegramChatId(123456789L);
        when(userRepository.findByIdAndStatus(userId, Status.ACTIVE)).thenReturn(Optional.of(sampleUser));

        TelegramStatusResponse status = telegramService.getStatus(userId);

        assertNotNull(status);
        assertTrue(status.isLinked());
        assertEquals(123456789L, status.getTelegramChatId());
        assertEquals("bang_linh_bot", status.getBotUsername());
        assertTrue(status.isDailyReportEnabled());
        assertTrue(status.isBudgetAlertEnabled());
        assertEquals("21:30", status.getReportTime());
    }

    @Test
    @DisplayName("Cập nhật cài đặt nhận thông báo Telegram thành công")
    void testUpdateSettings() {
        when(userRepository.findByIdAndStatus(userId, Status.ACTIVE)).thenReturn(Optional.of(sampleUser));

        TelegramSettingsRequest request = new TelegramSettingsRequest(false, true);
        TelegramStatusResponse response = telegramService.updateSettings(userId, request);

        assertNotNull(response);
        assertFalse(response.isDailyReportEnabled());
        assertTrue(response.isBudgetAlertEnabled());
        verify(userRepository, times(1)).save(sampleUser);
    }

    @Test
    @DisplayName("Gửi tổng kết chi tiêu hàng ngày tới người dùng thành công")
    void testSendDailySummaryToUserSuccess() {
        sampleUser.setTelegramChatId(123456789L);
        sampleUser.setTelegramDailyReportEnabled(true);

        com.example.financial_management.entity.Transaction t1 = new com.example.financial_management.entity.Transaction();
        t1.setType(com.example.financial_management.constant.TransactionType.EXPENSE);
        t1.setAmount(new java.math.BigDecimal("50000"));

        com.example.financial_management.entity.Transaction t2 = new com.example.financial_management.entity.Transaction();
        t2.setType(com.example.financial_management.constant.TransactionType.EXPENSE);
        t2.setAmount(new java.math.BigDecimal("195000"));

        when(transactionRepository.findAllByUserIdAndCreatedAtBetween(eq(userId), any(), any()))
                .thenReturn(java.util.List.of(t1, t2));

        com.example.financial_management.model.budget.BudgetSummaryResponse budgetSummary =
                com.example.financial_management.model.budget.BudgetSummaryResponse.builder()
                        .remainingAmount(new java.math.BigDecimal("4250000"))
                        .build();

        when(budgetService.getBudgetSummary(anyInt(), anyInt(), any()))
                .thenReturn(budgetSummary);
        when(botClient.sendMessage(eq(123456789L), anyString())).thenReturn(true);

        boolean result = telegramService.sendDailySummaryToUser(sampleUser);

        assertTrue(result);
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(botClient).sendMessage(eq(123456789L), captor.capture());

        String sentMsg = captor.getValue();
        assertTrue(sentMsg.contains("TỔNG KẾT CHI TIÊU HÔM NAY"));
        assertTrue(sentMsg.contains("245.000 ₫"));
        assertTrue(sentMsg.contains("2 giao dịch"));
        assertTrue(sentMsg.contains("4.250.000 ₫"));
        assertTrue(sentMsg.contains("Chúc bạn một buổi tối thư thái!"));
    }

    @Test
    @DisplayName("Không gửi tổng kết chi tiêu nếu người dùng tắt dailyReportEnabled")
    void testSendDailySummaryToUserDisabledReport() {
        sampleUser.setTelegramChatId(123456789L);
        sampleUser.setTelegramDailyReportEnabled(false);

        boolean result = telegramService.sendDailySummaryToUser(sampleUser);

        assertFalse(result);
        verifyNoInteractions(botClient);
    }

    @Test
    @DisplayName("Không gửi tổng kết chi tiêu nếu tài khoản chưa liên kết Chat ID")
    void testSendDailySummaryToUserNoChatId() {
        sampleUser.setTelegramChatId(null);
        sampleUser.setTelegramDailyReportEnabled(true);

        boolean result = telegramService.sendDailySummaryToUser(sampleUser);

        assertFalse(result);
        verifyNoInteractions(botClient);
    }

    @Test
    @DisplayName("Phát sóng tổng kết chi tiêu cho toàn bộ người dùng đủ điều kiện")
    void testSendDailySummaryToAllUsers() {
        sampleUser.setTelegramChatId(99887766L);
        sampleUser.setTelegramDailyReportEnabled(true);

        when(userRepository.findActiveUsersWithDailyReportEnabled(Status.ACTIVE))
                .thenReturn(java.util.List.of(sampleUser));
        when(transactionRepository.findAllByUserIdAndCreatedAtBetween(eq(userId), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        when(botClient.sendMessage(eq(99887766L), anyString())).thenReturn(true);

        int sentCount = telegramService.sendDailySummaryToAllUsers();

        assertEquals(1, sentCount);
        verify(botClient, times(1)).sendMessage(eq(99887766L), anyString());
    }

    @Test
    @DisplayName("Không phát sóng tổng kết chi tiêu khi telegram.bot.enabled = false")
    void testSendDailySummaryToAllUsersWhenBotDisabled() {
        config.setEnabled(false);

        int sentCount = telegramService.sendDailySummaryToAllUsers();

        assertEquals(0, sentCount);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(botClient);
    }
}
