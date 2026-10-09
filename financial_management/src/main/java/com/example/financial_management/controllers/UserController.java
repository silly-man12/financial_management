package com.example.financial_management.controllers;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.user.ChangeNameRequest;
import com.example.financial_management.model.user.ChangePasswordRequest;
import com.example.financial_management.model.user.ChangeUserStatusRequest;
import com.example.financial_management.model.user.UserResponse;
import com.example.financial_management.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User API", description = "User Management")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin người dùng hiện tại")
    public ResponseEntity<AbstractResponse<UserResponse>> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<UserResponse>().withData(() -> userService.getCurrentUser(auth));
    }

    @PostMapping("/updateProfile")
    @Operation(summary = "Cập nhật thông tin người dùng hiện tại")
    public ResponseEntity<AbstractResponse<UserResponse>> updateProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth,
            @Valid @RequestBody ChangeNameRequest request) {
        return new AbstractResponse<UserResponse>().withData(() -> userService.updateProfile(auth, request));
    }

    @PostMapping("/changePassword")
    @Operation(summary = "Đổi mật khẩu người dùng hiện tại")
    public ResponseEntity<AbstractResponse<UserResponse>> changePassword(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth,
            @Valid @RequestBody ChangePasswordRequest request) {
        return new AbstractResponse<UserResponse>().withData(() -> userService.changePassword(auth, request));
    }

    @GetMapping("/listUser")
    @Operation(summary = "Lấy danh sách tất cả người dùng (chỉ dành cho admin)")
    public ResponseEntity<AbstractResponse<List<UserResponse>>> getAllUsers(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<List<UserResponse>>().withData(() -> userService.getAllUsers(auth));
    }

    @PostMapping("/changeStatus")
    @Operation(summary = "Thay đổi trạng thái người dùng (chỉ dành cho admin)")
    public ResponseEntity<AbstractResponse<UserResponse>> changeUserStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth,
            @RequestBody ChangeUserStatusRequest request) {
        return new AbstractResponse<UserResponse>().withData(() -> userService.updateStatusUser(auth, request));
    }

}
