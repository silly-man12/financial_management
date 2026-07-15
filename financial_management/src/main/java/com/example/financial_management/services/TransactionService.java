package com.example.financial_management.services;

import com.example.financial_management.constant.Category;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.mapper.TransactionMapper;
import com.example.financial_management.model.PageResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.transaction.TransactionFilterRequest;
import com.example.financial_management.model.transaction.TransactionRequest;
import com.example.financial_management.model.transaction.TransactionResponse;
import com.example.financial_management.model.transaction.TransactionSpecification;
import com.example.financial_management.model.transaction.TransactionUpdateResponse;
import com.example.financial_management.model.transaction.TransferRequest;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final UserRepository userRepository;
    private final AccountService accountService;
    @Value("${app.upload.dir}")
    private String uploadDir;

    public PageResponse<TransactionResponse> getAllTransactions(Auth auth, Pageable pageable) {
        User user = getUser(auth);

        Page<TransactionResponse> pageResult = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(transactionMapper::toResponse);

        PageResponse<TransactionResponse> response = new PageResponse<>(
                pageResult.getContent(),
                pageResult.getNumber() + 1, // cộng 1 vì Page mặc định bắt đầu từ 0
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages());

        return response;
    }

    public TransactionResponse getById(UUID id, Auth auth) {
        User user = getUser(auth);
        return transactionRepository.findByIdAndUserId(id, user.getId())
                .map(transactionMapper::toResponse)
                .orElse(null);
    }

    public PageResponse<TransactionResponse> getTransactionByAccount(UUID accountId, Auth auth, Pageable pageable) {
        User user = getUser(auth);
        Account account = accountService.validateAccount(accountId, auth, Status.ACTIVE);
        Page<TransactionResponse> pageResult = transactionRepository
                .findByAccountIdAndUserId(account.getId(), user.getId(), pageable)
                .map(transactionMapper::toResponse);

        PageResponse<TransactionResponse> response = new PageResponse<>(
                pageResult.getContent(),
                pageResult.getNumber() + 1, // cộng 1 vì Page mặc định bắt đầu từ 0
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages());

        return response;
    }

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request, Auth auth, MultipartFile file) {
        Account account = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);

        validateCurrency(request.getCurrency(), account);
        validateCategory(request.getType(), request.getCategory());

        // Tạo transaction
        Transaction transaction = transactionMapper.toEntity(request, account.getUserId());
        if (request.getCreateAt() != null) {
            transaction.setCreatedAt(request.getCreateAt().toLocalDateTime());
        }

        // Xử lý ảnh
        handleTransactionImage(transaction, request.isHaveImage(), file);

        // Tính delta
        BigDecimal delta = accountService.calculateDelta(request);

        // Apply vào account
        accountService.applyDelta(account, delta);

        // Lưu transaction
        Transaction saved = transactionRepository.save(transaction);

        return transactionMapper.toResponse(saved);
    }

    @Transactional
    public TransactionUpdateResponse updateTransaction(TransactionRequest updated, Auth auth, UUID transactionId,
            MultipartFile file) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found or access denied"));

        Account oldAccount = accountService.validateAccount(
                transaction.getAccountId(),
                auth,
                Status.ACTIVE);

        Account newAccount = accountService.validateAccount(
                updated.getAccountId(),
                auth,
                Status.ACTIVE);

        BigDecimal finalDelta = accountService.calculateFinalDelta(transaction, updated);

        // validate và áp dụng
        accountService.updateBalanceForTransactionUpdate(
                oldAccount,
                newAccount,
                transaction,
                updated);

        // Cập nhật transaction
        transaction.setAmount(updated.getAmount());
        transaction.setDescription(updated.getDescription());
        transaction.setType(updated.getType());
        transaction.setAccountId(updated.getAccountId());
        transaction.setUserId(UUID.fromString(auth.getId()));
        transaction.setCreatedAt(updated.getCreateAt().toLocalDateTime());

        validateCurrency(updated.getCurrency(), newAccount);
        validateCategory(updated.getType(), updated.getCategory());
        handleTransactionImage(transaction, updated.isHaveImage(), file);

        Transaction saved = transactionRepository.save(transaction);

        // Trả response có thêm finalDelta
        TransactionUpdateResponse response = transactionMapper.toUpdateResponse(saved);
        response.setDifference(finalDelta);
        return response;
    }

    @Transactional
    public boolean deleteTransaction(UUID id, Auth auth) {
        User user = getUser(auth);

        Transaction transaction = transactionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new RuntimeException("Transaction not found or access denied"));

        Account account = accountService.validateAccount(transaction.getAccountId(), auth, Status.ACTIVE);

        // Delta của transaction cũ
        BigDecimal oldDelta = transaction.getType() == TransactionType.INCOME
                ? transaction.getAmount()
                : transaction.getAmount().negate();

        // Rollback balance (ngược lại delta cũ)
        accountService.applyDelta(account, oldDelta.negate());

        // Xoá transaction
        transactionRepository.delete(transaction);
        return true;
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request, Auth auth) {
        // Lấy user
        User user = getUser(auth);

        // Lấy transaction cũ
        Transaction transaction = transactionRepository.findByIdAndUserId(request.getTransactionId(), user.getId())
                .orElseThrow(() -> new RuntimeException("Transaction not found or access denied"));

        // Lấy account cũ và account mới
        Account oldAccount = accountService.validateAccount(transaction.getAccountId(), auth, Status.ACTIVE);
        Account newAccount = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);

        // Validate currency
        if (oldAccount.getCurrency() != newAccount.getCurrency()) {
            throw new RuntimeException("Currency mismatch between accounts");
        }

        if (oldAccount.getId().equals(newAccount.getId())) {
            throw new RuntimeException("Cannot transfer to the same account");
        }

        // Rollback delta ở account cũ
        BigDecimal oldDelta = transaction.getType() == TransactionType.INCOME
                ? transaction.getAmount()
                : transaction.getAmount().negate();
        accountService.applyDelta(oldAccount, oldDelta.negate());

        // Apply delta cho account mới
        accountService.applyDelta(newAccount, oldDelta);

        // Cập nhật transaction sang account mới
        transaction.setAccountId(newAccount.getId());
        Transaction saved = transactionRepository.save(transaction);

        return transactionMapper.toResponse(saved);
    }

    public PageResponse<TransactionResponse> filterTransactions(Auth auth, TransactionFilterRequest filter) {
        User user = getUser(auth);

        Pageable pageable = PageRequest.of(
                filter.getPage() - 1,
                filter.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Transaction> result = transactionRepository.findAll(
                TransactionSpecification.filter(
                        user.getId(),
                        filter),
                pageable);

        PageResponse<TransactionResponse> response = new PageResponse<>(
                result.getContent().stream().map(transactionMapper::toResponse).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());

        return response;
    }

    private User getUser(Auth auth) {
        return userRepository.findByIdAndStatus(UUID.fromString(auth.getId()), Status.ACTIVE)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void validateCurrency(int currency, Account account) {
        if (currency != account.getCurrency()) {
            throw new RuntimeException("Transaction currency does not match account currency");
        }
    }

    private void validateCategory(int type, int category) {

        if (type == TransactionType.EXPENSE) {
            if (category < Category.FOOD || category > Category.OTHER_EXPENSE) {
                throw new RuntimeException("Invalid category for EXPENSE transaction");
            }
        } else if (type == TransactionType.INCOME) {
            if (category < Category.SALARY || category > Category.OTHER_INCOME) {
                throw new RuntimeException("Invalid category for INCOME transaction");
            }
        } else if (type == TransactionType.TRANSFER) {
            if (category != Category.TRANSFER) {
                throw new RuntimeException("Invalid category for TRANSFER transaction");
            }
        } else {
            throw new RuntimeException("Unknown transaction type: " + type);
        }
    }

    private void handleTransactionImage(
            Transaction transaction,
            boolean haveImage,
            MultipartFile file) {

        // Người dùng bỏ ảnh
        if (!haveImage) {

            if (transaction.getImagePath() != null) {
                deleteImage(transaction.getImagePath());
            }

            transaction.setImagePath(null);
            return;
        }

        // Có ảnh nhưng không upload ảnh mới
        if (file == null || file.isEmpty()) {
            return;
        }

        // Có upload ảnh mới
        if (transaction.getImagePath() != null) {
            deleteImage(transaction.getImagePath());
        }

        String newPath = saveImage(file);

        transaction.setHaveImage(true);
        transaction.setImagePath(newPath);
    }

    private void deleteImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return;
        }

        try {
            Path filePath = Paths.get(uploadDir)
                    .resolve(Paths.get(imagePath).getFileName());

            Files.deleteIfExists(filePath);

        } catch (IOException e) {
            throw new RuntimeException("Xóa ảnh thất bại", e);
        }
    }

    private String saveImage(MultipartFile file) {
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);

            Path filePath = uploadPath.resolve(fileName);

            Files.write(filePath, file.getBytes());

            // Chỉ lưu đường dẫn public
            return "images/" + fileName;

        } catch (Exception e) {
            throw new RuntimeException("Upload file thất bại", e);
        }
    }
}
