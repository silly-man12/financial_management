package com.example.financial_management.controllers;

import com.example.financial_management.constant.Role;
import com.example.financial_management.constant.Status;
import com.example.financial_management.entity.User;
import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.telegram.TelegramLinkCodeResponse;
import com.example.financial_management.model.telegram.TelegramSettingsRequest;
import com.example.financial_management.model.telegram.TelegramStatusResponse;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.UserRepository;
import com.example.financial_management.telegram.TelegramCommandHandler;
import com.example.financial_management.telegram.TelegramMessageParser;
import com.example.financial_management.telegram.TelegramService;
import com.example.financial_management.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramControllerTest {

    @Mock
    private TelegramService telegramService;

    @Mock
    private TelegramMessageParser messageParser;

    @Mock
    private TelegramCommandHandler commandHandler;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    private TelegramController controller;

    private Auth userAuth;
    private Auth adminAuth;
    private User normalUser;

    @BeforeEach
    void setUp() {
        controller = new TelegramController(
                telegramService,
                messageParser,
                commandHandler,
                accountRepository,
                userRepository,
                jwtTokenUtil);

        UUID userId = UUID.randomUUID();
        userAuth = new Auth();
        userAuth.setId(userId.toString());
        userAuth.setEmail("user@example.com");
        userAuth.setName("Normal User");
        userAuth.setRole(Role.USER);
        userAuth.setStatus(Status.ACTIVE);

        adminAuth = new Auth();
        adminAuth.setId(UUID.randomUUID().toString());
        adminAuth.setEmail("admin@example.com");
        adminAuth.setName("Admin User");
        adminAuth.setRole(Role.ADMIN);
        adminAuth.setStatus(Status.ACTIVE);

        normalUser = new User();
        normalUser.setId(userId);
        normalUser.setEmail("user@example.com");
        normalUser.setName("Normal User");
        normalUser.setStatus(Status.ACTIVE);
        normalUser.setTelegramChatId(123456789L);
    }

    @Test
    @DisplayName("GET /telegram/status - Lấy trạng thái kết nối thành công khi đã xác thực")
    void testGetStatusSuccess() {
        TelegramStatusResponse mockStatus = TelegramStatusResponse.builder()
                .isLinked(true)
                .telegramChatId(123456789L)
                .botUsername("bang_linh_bot")
                .dailyReportEnabled(true)
                .budgetAlertEnabled(true)
                .reportTime("21:30")
                .build();

        when(telegramService.getStatus(userAuth.getUUID())).thenReturn(mockStatus);

        ResponseEntity<AbstractResponse<TelegramStatusResponse>> response = controller.getStatus(userAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(mockStatus, response.getBody().getData());
    }

    @Test
    @DisplayName("POST /telegram/generate-link-code - Tạo mã OTP liên kết thành công")
    void testGenerateLinkCodeSuccess() {
        TelegramLinkCodeResponse mockCode = TelegramLinkCodeResponse.builder()
                .code("849201")
                .expiredAt(LocalDateTime.now().plusMinutes(10))
                .deepLink("https://t.me/bang_linh_bot?start=LINK_849201")
                .build();

        when(telegramService.generateLinkCode(userAuth.getUUID())).thenReturn(mockCode);

        ResponseEntity<AbstractResponse<TelegramLinkCodeResponse>> response = controller.generateLinkCode(userAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("849201", response.getBody().getData().getCode());
        verify(telegramService).generateLinkCode(userAuth.getUUID());
    }

    @Test
    @DisplayName("POST /telegram/unlink - Hủy liên kết Telegram thành công và trả về data null")
    void testUnlinkSuccess() {
        ResponseEntity<AbstractResponse<Object>> response = controller.unlink(userAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Đã hủy liên kết Telegram thành công", response.getBody().getMessage());
        assertNull(response.getBody().getData());
        verify(telegramService).unlinkUser(userAuth.getUUID());
    }

    @Test
    @DisplayName("POST /telegram/settings - Cập nhật cài đặt nhận thông báo thành công")
    void testUpdateSettingsSuccess() {
        TelegramSettingsRequest request = new TelegramSettingsRequest(false, true);
        TelegramStatusResponse mockUpdated = TelegramStatusResponse.builder()
                .isLinked(true)
                .telegramChatId(123456789L)
                .botUsername("bang_linh_bot")
                .dailyReportEnabled(false)
                .budgetAlertEnabled(true)
                .reportTime("21:30")
                .build();

        when(telegramService.updateSettings(userAuth.getUUID(), request)).thenReturn(mockUpdated);

        ResponseEntity<AbstractResponse<TelegramStatusResponse>> response = controller.updateSettings(request,
                userAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertFalse(response.getBody().getData().isDailyReportEnabled());
        verify(telegramService).updateSettings(userAuth.getUUID(), request);
    }

    @Test
    @DisplayName("POST /telegram/test-daily-summary - Kích hoạt gửi báo cáo tối thử nghiệm")
    void testDailySummaryTrigger() {
        ResponseEntity<AbstractResponse<String>> response = controller.testDailySummary(userAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        verify(telegramService).sendTestDailySummary(userAuth);
    }

    @Test
    @DisplayName("POST /telegram/test-budget-alert - Kích hoạt gửi cảnh báo ngân sách thử nghiệm")
    void testBudgetAlertTrigger() {
        ResponseEntity<AbstractResponse<String>> response = controller.testBudgetAlert(userAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        verify(telegramService).sendTestBudgetAlert(userAuth);
    }

    @Test
    @DisplayName("POST /telegram/simulate - User thông thường bị chặn mạo danh tài khoản khác")
    void testSimulateForcesCurrentUserForNonAdmin() {
        when(userRepository.findByEmailAndStatus("user@example.com", Status.ACTIVE))
                .thenReturn(Optional.of(normalUser));

        // Người dùng thông thường cố tình truyền email của người khác
        // ("victim@example.com")
        ResponseEntity<AbstractResponse<String>> response = controller.simulateMessage("50k cafe", "victim@example.com",
                userAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Hệ thống vẫn dùng email của chính user ("user@example.com") chứ không dùng
        // victim
        verify(userRepository).findByEmailAndStatus("user@example.com", Status.ACTIVE);
        verify(commandHandler).handleIncomingMessage(eq(123456789L), eq("50k cafe"));
    }

    @Test
    @DisplayName("POST /telegram/simulate - Admin được quyền chỉ định email người dùng khác để giả lập")
    void testSimulateAllowsAdminToSpecifyEmail() {
        when(userRepository.findByEmailAndStatus("target@example.com", Status.ACTIVE))
                .thenReturn(Optional.of(normalUser));

        ResponseEntity<AbstractResponse<String>> response = controller.simulateMessage("50k cafe", "target@example.com",
                adminAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).findByEmailAndStatus("target@example.com", Status.ACTIVE);
        verify(commandHandler).handleIncomingMessage(eq(123456789L), eq("50k cafe"));
    }
}
