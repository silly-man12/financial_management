package com.example.financial_management.controllers;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.AuthResponse;
import com.example.financial_management.model.user.ForgotPasswordRequest;
import com.example.financial_management.model.user.LoginRequest;
import com.example.financial_management.model.user.ResetPasswordRequest;
import com.example.financial_management.model.user.UserResponse;
import com.example.financial_management.model.user.UserSignUpRequest;
import com.example.financial_management.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth API", description = "authentication and authorization")
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup")
    @Operation(summary = "Sign up")
    public ResponseEntity<AbstractResponse<UserResponse>> signUp(@RequestBody UserSignUpRequest request) {
        return new AbstractResponse<UserResponse>().withData(() -> userService.signUp(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login")
    public ResponseEntity<AbstractResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return new AbstractResponse<AuthResponse>().withData(() -> {
            String token = userService.login(request);

            if (token == null || token.trim().isEmpty()) {
                throw new RuntimeException("Login failed");
            }

            AuthResponse response = new AuthResponse();
            response.setStatus("success");
            response.setEmail(request.getEmail());
            response.setToken(token);
            return response;
        });
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Quên mật khẩu - gửi email kèm reset token")
    public ResponseEntity<AbstractResponse<String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        return new AbstractResponse<String>().withData(() -> {
            userService.forgotPassword(request);
            return "Email hướng dẫn đặt lại mật khẩu đã được gửi.";
        });
    }

    @GetMapping("/verify-reset-token")
    @Operation(summary = "Xác thực token trước khi hiển thị màn hình đặt lại mật khẩu")
    public ResponseEntity<AbstractResponse<String>> verifyResetToken(@RequestParam("token") String token) {
        return new AbstractResponse<String>().withData(() -> userService.verifyResetPasswordToken(token));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt lại mật khẩu mới với token")
    public ResponseEntity<AbstractResponse<UserResponse>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        return new AbstractResponse<UserResponse>().withData(() -> userService.resetPassword(request));
    }

}
