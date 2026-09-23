package com.example.financial_management.controllers;

import com.example.financial_management.constant.Status;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.User;
import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.UserRepository;
import com.example.financial_management.telegram.TelegramCommandHandler;
import com.example.financial_management.telegram.TelegramMessageParser;
import com.example.financial_management.telegram.TelegramParsedTransaction;
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
@Tag(name = "Telegram API", description = "Kiểm tra và giả lập bóc tách tin nhắn Telegram Bot")
public class TelegramController {

    private final TelegramMessageParser messageParser;
    private final TelegramCommandHandler commandHandler;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final com.example.financial_management.util.JwtTokenUtil jwtTokenUtil;

    @GetMapping("/test-parse")
    @Operation(summary = "Kiểm tra kết quả bóc tách tin nhắn (không lưu vào database)")
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
                // Tạo danh sách ví mẫu phổ biến nếu chưa đăng nhập
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
            String targetEmail = (auth != null && auth.getEmail() != null) ? auth.getEmail() : email;
            if (targetEmail == null || targetEmail.trim().isEmpty()) {
                throw new IllegalArgumentException("Vui lòng đăng nhập hoặc cung cấp email người dùng để giả lập");
            }

            User user = userRepository.findByEmailAndStatus(targetEmail.trim(), Status.ACTIVE)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với email: " + targetEmail));

            // Tạm thời gán một chatId giả lập nếu user chưa có
            long simChatId = user.getTelegramChatId() != null ? user.getTelegramChatId() : 999999999L;
            if (user.getTelegramChatId() == null) {
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
