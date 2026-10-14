package com.example.financial_management.controllers;

import com.example.financial_management.constant.Status;
import com.example.financial_management.entity.Account;
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
import com.example.financial_management.telegram.TelegramParsedTransaction;
import com.example.financial_management.telegram.TelegramService;
import com.example.financial_management.util.JwtTokenUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/telegram")
@RequiredArgsConstructor
@Tag(name = "Telegram API", description = "Quản lý kết nối, cấu hình và giả lập bóc tách tin nhắn Telegram Bot")
public class TelegramController {

    private final TelegramService telegramService;
    private final TelegramMessageParser messageParser;
    private final TelegramCommandHandler commandHandler;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final JwtTokenUtil jwtTokenUtil;

    @GetMapping("/status")
    @Operation(summary = "Lấy trạng thái kết nối Telegram của người dùng hiện tại")
    public ResponseEntity<AbstractResponse<TelegramStatusResponse>> getStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<TelegramStatusResponse>().withData(() -> {
            if (auth == null || auth.getUUID() == null) {
                throw new IllegalArgumentException("Vui lòng đăng nhập để xem trạng thái kết nối");
            }
            return telegramService.getStatus(auth.getUUID());
        });
    }

    @PostMapping("/generate-link-code")
    @Operation(summary = "Tạo mã OTP 6 số và deep link để kết nối Telegram an toàn (hết hạn trong 10 phút)")
    public ResponseEntity<AbstractResponse<TelegramLinkCodeResponse>> generateLinkCode(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<TelegramLinkCodeResponse>().withData(() -> {
            if (auth == null || auth.getUUID() == null) {
                throw new IllegalArgumentException("Vui lòng đăng nhập để tạo mã liên kết");
            }
            return telegramService.generateLinkCode(auth.getUUID());
        });
    }

    @PostMapping("/unlink")
    @Operation(summary = "Hủy liên kết Telegram khỏi tài khoản người dùng")
    public ResponseEntity<AbstractResponse<Object>> unlink(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        if (auth == null || auth.getUUID() == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập để hủy liên kết Telegram");
        }

        telegramService.unlinkUser(auth.getUUID());

        AbstractResponse<Object> response = new AbstractResponse<>()
                .setCode(200)
                .setSuccess(true)
                .setMessage("Đã hủy liên kết Telegram thành công")
                .setData(null);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/settings")
    @Operation(summary = "Cập nhật cài đặt nhận thông báo báo cáo tối và cảnh báo ngân sách Telegram")
    public ResponseEntity<AbstractResponse<TelegramStatusResponse>> updateSettings(
            @RequestBody TelegramSettingsRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<TelegramStatusResponse>().withData(() -> {
            if (auth == null || auth.getUUID() == null) {
                throw new IllegalArgumentException("Vui lòng đăng nhập để cập nhật cài đặt");
            }
            return telegramService.updateSettings(auth.getUUID(), request);
        });
    }

    @PostMapping("/test-daily-summary")
    @Operation(summary = "Gửi thử nghiệm tin nhắn tổng kết chi tiêu hàng ngày về Telegram")
    public ResponseEntity<AbstractResponse<String>> testDailySummary(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<String>().withData(() -> {
            if (auth == null || auth.getUUID() == null) {
                throw new IllegalArgumentException("Vui lòng đăng nhập để gửi tin nhắn thử nghiệm");
            }
            telegramService.sendTestDailySummary(auth);
            return "Đã gửi tin nhắn tổng kết chi tiêu thử nghiệm đến Telegram thành công!";
        });
    }

    @PostMapping("/test-budget-alert")
    @Operation(summary = "Gửi thử nghiệm tin nhắn cảnh báo vượt hạn mức ngân sách về Telegram")
    public ResponseEntity<AbstractResponse<String>> testBudgetAlert(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<String>().withData(() -> {
            if (auth == null || auth.getUUID() == null) {
                throw new IllegalArgumentException("Vui lòng đăng nhập để gửi tin nhắn thử nghiệm");
            }
            telegramService.sendTestBudgetAlert(auth);
            return "Đã gửi tin nhắn cảnh báo ngân sách thử nghiệm đến Telegram thành công!";
        });
    }

    @GetMapping("/test-parse")
    @Operation(summary = "Kiểm tra kết quả bóc tách tin nhắn cú pháp tự nhiên (không lưu vào database)")
    public ResponseEntity<AbstractResponse<TelegramParsedTransaction>> testParse(
            @RequestParam String text,
            jakarta.servlet.http.HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<TelegramParsedTransaction>().withData(() -> {
            Auth resolvedAuth = auth;
            if (resolvedAuth == null) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    if (jwtTokenUtil.validateToken(token)) {
                        resolvedAuth = jwtTokenUtil.extractAuth(token);
                    }
                }
            }

            List<Account> accounts;
            if (resolvedAuth != null && resolvedAuth.getId() != null) {
                accounts = accountRepository.findAllByUserIdAndStatus(resolvedAuth.getUUID(), Status.ACTIVE);
            } else {
                accounts = createMockAccounts();
            }

            return messageParser.parse(text, accounts);
        });
    }

    @PostMapping("/simulate")
    @Operation(summary = "Giả lập gửi tin nhắn Telegram vào hệ thống (thực hiện ghi chép thật)")
    public ResponseEntity<AbstractResponse<String>> simulateMessage(
            @RequestParam String text,
            @RequestParam(required = false) String email,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<String>().withData(() -> {
            if (auth == null || auth.getEmail() == null) {
                throw new IllegalArgumentException("Vui lòng đăng nhập để thực hiện giả lập");
            }

            // Bảo mật: Người dùng thông thường chỉ được giả lập cho tài khoản của mình.
            // Chỉ tài khoản ADMIN mới có quyền giả lập cho email khác.
            final String targetEmail = (auth.isAdmin() && email != null && !email.trim().isEmpty())
                    ? email.trim()
                    : auth.getEmail();

            User user = userRepository.findByEmailAndStatus(targetEmail, Status.ACTIVE)
                    .orElseThrow(
                            () -> new IllegalArgumentException("Không tìm thấy người dùng với email: " + targetEmail));

            // Dùng chatId hiện tại nếu có, hoặc tạo simChatId duy nhất cho user này
            long simChatId;
            if (user.getTelegramChatId() != null) {
                simChatId = user.getTelegramChatId();
            } else {
                simChatId = Math.abs((long) user.getId().hashCode()) + 1_000_000_000L;
                user.setTelegramChatId(simChatId);
                userRepository.save(user);
            }

            commandHandler.handleIncomingMessage(simChatId, text);
            return "Đã xử lý tin nhắn giả lập thành công!";
        });
    }

    private List<Account> createMockAccounts() {
        List<Account> list = new ArrayList<>();
        Account vcb = new Account();
        vcb.setName("Vietcombank");
        list.add(vcb);

        Account cash = new Account();
        cash.setName("Tiền mặt");
        list.add(cash);

        Account momo = new Account();
        momo.setName("MoMo");
        list.add(momo);

        Account mb = new Account();
        mb.setName("MB Bank");
        list.add(mb);

        Account zalopay = new Account();
        zalopay.setName("ZaloPay");
        list.add(zalopay);

        return list;
    }
}
