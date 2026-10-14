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
}
