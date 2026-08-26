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
import com.example.financial_management.repository.DebtPaymentRepository;
import com.example.financial_management.repository.SavingGoalContributionRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final DebtPaymentRepository debtPaymentRepository;
    private final SavingGoalContributionRepository savingGoalContributionRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    public List<TransactionResponse> getAllTransactions(Auth auth) {
        User user = getUser(auth);

        return transactionRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    public PageResponse<TransactionResponse> getAllTransactionsWithPage(Auth auth, Pageable pageable) {
        User user = getUser(auth);

        Page<TransactionResponse> pageResult = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(transactionMapper::toResponse);

        return new PageResponse<>(
                pageResult.getContent(),
                pageResult.getNumber() + 1,
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages());
    }

    public List<TransactionResponse> getByCategoryAndMonth(int category, String monthYear, Auth auth) {
        User user = getUser(auth);

        String[] parts = monthYear.split("/");
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid monthYear format. Expected format: MM/yyyy");
        }

        try {
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);

            return transactionRepository
                    .findAllByCategoryAndMonth(
                            user.getId(),
                            TransactionType.EXPENSE,
                            category,
                            month,
                            year)
                    .stream()
                    .map(transactionMapper::toResponse)
                    .toList();
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month and Year must be numbers");
        }
    }

    public TransactionResponse getById(UUID id, Auth auth) {
        User user = getUser(auth);
        return transactionRepository.findByIdAndUserId(id, user.getId())
                .map(transactionMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    public PageResponse<TransactionResponse> getTransactionByAccount(UUID accountId, Auth auth, Pageable pageable) {
        User user = getUser(auth);
        Account account = accountService.validateAccount(accountId, auth, Status.ACTIVE);
        Page<TransactionResponse> pageResult = transactionRepository
                .findByAccountIdAndUserId(account.getId(), user.getId(), pageable)
                .map(transactionMapper::toResponse);

        return new PageResponse<>(
                pageResult.getContent(),
                pageResult.getNumber() + 1,
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages());
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
        User user = getUser(auth);

        // Bảo mật: Kiểm tra cả transactionId và userId
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found or access denied"));

        // Ràng buộc toàn vẹn: Không cho phép chỉnh sửa trực tiếp giao dịch sinh ra từ Quản lý nợ hoặc Mục tiêu tiết kiệm
        if (debtPaymentRepository.existsByTransactionId(transactionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giao dịch này được tạo tự động từ Quản lý nợ. Vui lòng vào mục Quản lý nợ để cập nhật hoặc hủy.");
        }
        if (savingGoalContributionRepository.existsByTransactionId(transactionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giao dịch này được tạo tự động từ Mục tiêu tiết kiệm. Vui lòng vào mục Tiết kiệm để chỉnh sửa hoặc hủy giao dịch.");
        }

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
        
        // Tránh NullPointerException nếu createAt không được gửi lên
        if (updated.getCreateAt() != null) {
            transaction.setCreatedAt(updated.getCreateAt().toLocalDateTime());
        }

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found or access denied"));

        // Ràng buộc toàn vẹn: Không cho phép xóa trực tiếp giao dịch sinh ra từ Quản lý nợ hoặc Mục tiêu tiết kiệm
        if (debtPaymentRepository.existsByTransactionId(transaction.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giao dịch này được tạo tự động từ Quản lý nợ. Vui lòng vào mục Quản lý nợ để hủy lần thanh toán này.");
        }
        if (savingGoalContributionRepository.existsByTransactionId(transaction.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giao dịch này được tạo tự động từ Mục tiêu tiết kiệm. Vui lòng vào mục Tiết kiệm để hủy lần đóng góp này.");
        }

        Account account = accountService.validateAccount(transaction.getAccountId(), auth, Status.ACTIVE);

        // Delta của transaction cũ
        BigDecimal oldDelta = transaction.getType() == TransactionType.INCOME
                ? transaction.getAmount()
                : transaction.getAmount().negate();

        // Rollback balance (ngược lại delta cũ)
        accountService.applyDelta(account, oldDelta.negate());

        // Xóa file ảnh vật lý trên ổ cứng nếu có
        if (transaction.getImagePath() != null) {
            deleteImage(transaction.getImagePath());
        }

        // Xoá transaction
        transactionRepository.delete(transaction);
        return true;
    }

    @Transactional
    public TransactionResponse createTransfer(TransferRequest request, Auth auth) {
        User user = getUser(auth);

        if (request.getAccountId() == null || request.getTargetAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and target account IDs are required");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer amount must be greater than zero");
        }

        if (request.getAccountId().equals(request.getTargetAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and target accounts must be different");
        }

        Account sourceAccount = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);
        Account targetAccount = accountService.validateAccount(request.getTargetAccountId(), auth, Status.ACTIVE);

        if (sourceAccount.getCurrency() != targetAccount.getCurrency()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and target accounts must have the same currency");
        }

        Transaction sourceTransaction = buildTransferTransaction(
                user.getId(),
                sourceAccount.getId(),
                request.getAmount(),
                TransactionType.EXPENSE,
                request.getDescription(),
                request.getCreateAt(),
                sourceAccount.getCurrency());

        Transaction targetTransaction = buildTransferTransaction(
                user.getId(),
                targetAccount.getId(),
                request.getAmount(),
                TransactionType.INCOME,
                request.getDescription(),
                request.getCreateAt(),
                targetAccount.getCurrency());

        accountService.applyDelta(sourceAccount, request.getAmount().negate());
        accountService.applyDelta(targetAccount, request.getAmount());

        Transaction savedSourceTransaction = transactionRepository.save(sourceTransaction);
        transactionRepository.save(targetTransaction);

        return transactionMapper.toResponse(savedSourceTransaction);
    }

    private Transaction buildTransferTransaction(
            UUID userId,
            UUID accountId,
            BigDecimal amount,
            Integer transactionType,
            String description,
            OffsetDateTime createdAt,
            int currency) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(accountId);
        transaction.setAmount(amount);
        transaction.setType(transactionType);
        transaction.setCategory(Category.TRANSFER);
        transaction.setCurrency(currency);
        transaction.setDescription(description);
        transaction.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return transaction;
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

        return new PageResponse<>(
                result.getContent().stream().map(transactionMapper::toResponse).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private User getUser(Auth auth) {
        return userRepository.findByIdAndStatus(UUID.fromString(auth.getId()), Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void validateCurrency(int currency, Account account) {
        if (currency != account.getCurrency()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction currency does not match account currency");
        }
    }

    private void validateCategory(int type, int category) {
        if (type == TransactionType.EXPENSE) {
            if (category < Category.FOOD || category > Category.OTHER_EXPENSE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category for EXPENSE transaction");
            }
        } else if (type == TransactionType.INCOME) {
            if (category < Category.SALARY || category > Category.OTHER_INCOME) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category for INCOME transaction");
            }
        } else if (type == TransactionType.TRANSFER) {
            if (category != Category.TRANSFER) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category for TRANSFER transaction");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown transaction type: " + type);
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
            transaction.setHaveImage(false);
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
            log.error("Xóa ảnh thất bại: {}", imagePath, e);
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
            log.error("Upload file thất bại", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload file thất bại");
        }
    }
}
