package com.example.financial_management.services;

import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.auth.AuthAccount;
import com.example.financial_management.model.user.ChangeNameRequest;
import com.example.financial_management.model.user.ChangePasswordRequest;
import com.example.financial_management.model.user.ChangeUserStatusRequest;
import com.example.financial_management.model.user.LoginRequest;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
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

    public UserResponse signUp(UserSignUpRequest request) {
        validateSignUp(request);

        Map<String, String> hashAndSalt = generateHashAndSalt(request.getPassword());

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordSalt(hashAndSalt.get("salt"));
        user.setPasswordHash(hashAndSalt.get("hash"));
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

        Auth auth = createAuth(user, accounts);

        return jwtTokenUtil.generateToken(auth);
    }

    public UserResponse getCurrentUser(Auth auth) {
        return userRepository.findById(auth.getUUID())
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public UserResponse updateProfile(Auth auth, ChangeNameRequest request) {
        User user = userRepository.findById(auth.getUUID())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setName(request.getName());
        User saved = userRepository.saveAndFlush(user);
        return userMapper.toResponse(saved);
    }

    public UserResponse changePassword(Auth auth, ChangePasswordRequest request) {
        User user = userRepository.findById(auth.getUUID())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        validateUser(user);
        validatePassword(user, request.getOldPassword());

        validatePasswordAndConfirm(request.getNewPassword(), request.getConfirmPassword());

        Map<String, String> hashAndSalt = generateHashAndSalt(request.getNewPassword());

        user.setPasswordSalt(hashAndSalt.get("salt"));
        user.setPasswordHash(hashAndSalt.get("hash"));

        User saved = userRepository.saveAndFlush(user);

        return userMapper.toResponse(saved);
    }

    public List<UserResponse> getAllUsers(Auth auth) {
        validateAdmin(auth);
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(user -> userMapper.toResponse(user))
                .toList();
    }

    public UserResponse updateStatusUser(Auth auth, ChangeUserStatusRequest request) {
        validateAdmin(auth);
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getId().equals(auth.getUUID())) {
            // Admin không được phép thay đổi trạng thái của chính mình
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can't change status");
        } else {
            user.setStatus(request.getStatus());
            User saved = userRepository.saveAndFlush(user);
            return userMapper.toResponse(saved);
        }
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

    private void validateSignUp(UserSignUpRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }

        validatePasswordAndConfirm(request.getPassword(), request.getConfirmPassword());
    }

    private Map<String, String> generateHashAndSalt(String password) {
        String salt = BCrypt.gensalt();
        String hash = BCrypt.hashpw(password, salt);

        Map<String, String> result = new HashMap<>();
        result.put("salt", salt);
        result.put("hash", hash);
        return result;
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

    private Auth createAuth(User user, List<Account> accounts) {
        Auth auth = new Auth();
        auth.setId(user.getId().toString());
        auth.setRole(user.getRole());
        auth.setStatus(user.getStatus());
        auth.setEmail(user.getEmail());
        auth.setName(user.getName());

        // Chuyển List<Account> sang List<AuthAccount>
        if (accounts != null && !accounts.isEmpty()) {
            List<AuthAccount> authAccounts = accounts.stream().map(acc -> {
                AuthAccount authAcc = new AuthAccount();
                authAcc.setId(acc.getId());
                authAcc.setName(acc.getName());
                authAcc.setStatus(acc.getStatus());
                return authAcc;
            }).toList();
            auth.setAccounts(authAccounts);
        } else {
            auth.setAccounts(List.of());
        }

        return auth;
    }

}
