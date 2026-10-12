package com.example.financial_management.services;

import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.auth.AuthAccount;
import com.example.financial_management.model.user.ChangeNameRequest;
import com.example.financial_management.model.user.ChangePasswordRequest;
import com.example.financial_management.model.user.ChangeUserStatusRequest;
import com.example.financial_management.model.user.ForgotPasswordRequest;
import com.example.financial_management.model.user.LoginRequest;
import com.example.financial_management.model.user.ResetPasswordRequest;
import com.example.financial_management.model.user.UserResponse;
import com.example.financial_management.model.user.UserSignUpRequest;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.User;
import com.example.financial_management.mapper.UserMapper;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.UserRepository;
import com.example.financial_management.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.financial_management.constant.Role;
import com.example.financial_management.constant.Status;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserMapper userMapper;
    @Value("${email_admin}")
    private String emailAdmin;
    @Value("${app.verify-reset-password-url}")
    private String verifyResetPasswordUrl;
    private final EmailService emailService;

    public UserResponse signUp(UserSignUpRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }

        validatePasswordAndConfirm(request.getPassword(), request.getConfirmPassword());

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        setUserPassword(user, request.getPassword());
        user.setStatus(Status.ACTIVE);

        if (request.getEmail().equals(emailAdmin)) {
            user.setRole(Role.ADMIN);
        } else {
            user.setRole(Role.USER);
        }

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        validateUser(user);
        validatePassword(user, request.getPassword());

        List<Account> accounts = accountRepository.findAllByUserIdAndStatus(user.getId(), Status.ACTIVE);

        return jwtTokenUtil.generateToken(user, accounts);
    }

    @Cacheable(value = "authenticatedUser", key = "#auth.id")
    public User getAuthenticatedUser(Auth auth) {
        if (auth == null || auth.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Người dùng chưa xác thực");
        }
        User user = userRepository.findById(auth.getUUID())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        validateUser(user);
        return user;
    }

    public UserResponse getCurrentUser(Auth auth) {
        User user = getAuthenticatedUser(auth);
        return userMapper.toResponse(user);
    }

    @CacheEvict(value = "authenticatedUser", key = "#auth.id")
    public UserResponse updateProfile(Auth auth, ChangeNameRequest request) {
        User user = userRepository.findById(auth.getUUID())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (applyProfileUpdates(user, request)) {
            user = userRepository.save(user);
        }

        return userMapper.toResponse(user);
    }

    @CacheEvict(value = "authenticatedUser", key = "#auth.id")
    public UserResponse changePassword(Auth auth, ChangePasswordRequest request) {
        User user = userRepository.findById(auth.getUUID())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        validateUser(user);
        validatePassword(user, request.getOldPassword());

        validatePasswordAndConfirm(request.getNewPassword(), request.getConfirmPassword());

        setUserPassword(user, request.getNewPassword());

        User saved = userRepository.save(user);

        return userMapper.toResponse(saved);
    }

    public List<UserResponse> getAllUsers(Auth auth) {
        validateAdmin(auth);
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(user -> userMapper.toResponse(user))
                .toList();
    }

    @CacheEvict(value = "authenticatedUser", key = "#request.userId")
    public UserResponse updateStatusUser(Auth auth, ChangeUserStatusRequest request) {
        validateAdmin(auth);
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getId().equals(auth.getUUID())) {
            // Admin không được phép thay đổi trạng thái của chính mình
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can't change status");
        } else {
            user.setStatus(request.getStatus());
            User saved = userRepository.save(user);
            return userMapper.toResponse(saved);
        }
    }

    /**
     * 1. API Quên mật khẩu: Tạo token và gửi email đặt lại mật khẩu
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không được để trống");
        }

        User user = userRepository.findByEmail(request.getEmail().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy người dùng với email này"));

        validateUser(user);

        // Tạo reset token (15 phút)
        String resetToken = jwtTokenUtil.generateResetPasswordToken(user.getEmail());

        // Tạo đường link gửi kèm token về client
        String resetLink = verifyResetPasswordUrl + "?token=" + resetToken;

        // Gửi email
        emailService.sendResetPasswordEmail(user.getEmail(), user.getName(), resetLink);
    }

    /**
     * 2. API Xác nhận token trước khi đưa đến trình duyệt Reset Password
     */
    public String verifyResetPasswordToken(String token) {
        if (token == null || token.trim().isEmpty() || !jwtTokenUtil.validateResetPasswordToken(token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token không hợp lệ hoặc đã hết hạn");
        }

        String email = jwtTokenUtil.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Người dùng không tồn tại"));

        validateUser(user);

        return email; // Trả về email để frontend hiển thị xác thực
    }

    /**
     * 3. API Đặt lại mật khẩu mới
     */
    @CacheEvict(value = "authenticatedUser", allEntries = true)
    public UserResponse resetPassword(ResetPasswordRequest request) {
        // Validate Token
        if (request.getToken() == null || !jwtTokenUtil.validateResetPasswordToken(request.getToken())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token không hợp lệ hoặc đã hết hạn");
        }

        // Validate mật khẩu mới & xác nhận
        validatePasswordAndConfirm(request.getNewPassword(), request.getConfirmPassword());

        String email = jwtTokenUtil.extractEmail(request.getToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Người dùng không tồn tại"));

        validateUser(user);

        // Cập nhật mật khẩu mới
        setUserPassword(user, request.getNewPassword());

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    private void validateAdmin(Auth auth) {
        User user = userRepository.findById(auth.getUUID())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        log.debug("Is Admin? {}", auth.isAdmin());

        if (!auth.isAdmin() || user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied. Admins only.");
        }
    }

    private void validateUser(User user) {
        if (user.getStatus() != Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not active");
        }
    }

    private void validatePassword(User user, String password) {
        if (password == null || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }

        String hashedPassword = BCrypt.hashpw(password, user.getPasswordSalt());
        if (!hashedPassword.equals(user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password");
        }
    }

    private void setUserPassword(User user, String rawPassword) {
        String salt = BCrypt.gensalt();
        user.setPasswordSalt(salt);
        user.setPasswordHash(BCrypt.hashpw(rawPassword, salt));
    }

    private void validatePasswordAndConfirm(String password, String confirmPassword) {
        if (password == null || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }

        if (confirmPassword == null || confirmPassword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirm password is required");
        }

        if (!password.equals(confirmPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password and confirm password do not match");
        }
    }

    public Auth createAuth(User user, List<Account> accounts) {
        return jwtTokenUtil.createAuth(user, accounts);
    }

    private boolean applyProfileUpdates(User user, ChangeNameRequest request) {
        boolean updated = false;

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String trimmedName = request.getName().trim();
            if (!trimmedName.equals(user.getName())) {
                user.setName(trimmedName);
                updated = true;
            }
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String trimmedEmail = request.getEmail().trim();
            if (!trimmedEmail.equalsIgnoreCase(user.getEmail())) {
                userRepository.findByEmail(trimmedEmail).ifPresent(existingUser -> {
                    if (!existingUser.getId().equals(user.getId())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
                    }
                });
                user.setEmail(trimmedEmail);
                updated = true;
            }
        }

        return updated;
    }

    public boolean existsByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return userRepository.existsByEmail(email.trim().toLowerCase())
                || userRepository.findByEmail(email.trim()).isPresent();
    }

    public String authenticate(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không hợp lệ");
        }
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .or(() -> userRepository.findByEmail(email.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        validateUser(user);

        List<Account> accounts = accountRepository.findAllByUserIdAndStatus(user.getId(), Status.ACTIVE);
        return jwtTokenUtil.generateToken(user, accounts);
    }

}
