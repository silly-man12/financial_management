package com.example.financial_management.services;

import com.example.financial_management.constant.Category;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.Tag;
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
import com.example.financial_management.repository.DebtRepository;
import com.example.financial_management.repository.SavingGoalContributionRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.util.DateTimeUtils;

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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final UserService userService;
    private final AccountService accountService;
    private final DebtPaymentRepository debtPaymentRepository;
    private final DebtRepository debtRepository;
    private final SavingGoalContributionRepository savingGoalContributionRepository;
    private final CurrencyExchangeService currencyExchangeService;
    private final TagService tagService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private TransactionResponse toEnrichedResponse(Transaction transaction) {
        TransactionResponse response = transactionMapper.toResponse(transaction);
        if (response != null) {
            response.setExchangeRate(currencyExchangeService.getCurrentRate());
            response.setAmountUsd(currencyExchangeService.calculateUsd(response.getAmount(), response.getCurrency()));
        }
        return response;
    }

    private TransactionUpdateResponse toEnrichedUpdateResponse(Transaction transaction, BigDecimal finalDelta) {
        TransactionUpdateResponse response = transactionMapper.toUpdateResponse(transaction);
        if (response != null) {
            response.setExchangeRate(currencyExchangeService.getCurrentRate());
            response.setAmountUsd(currencyExchangeService.calculateUsd(response.getAmount(), response.getCurrency()));
            response.setDifference(finalDelta);
        }
        return response;
    }

    public List<TransactionResponse> getAllTransactions(Auth auth) {
        User user = getUser(auth);

        return transactionRepository
                .findByUserIdWithTagsOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toEnrichedResponse)
                .toList();
    }


    public PageResponse<TransactionResponse> getAllTransactionsWithPage(Auth auth, Pageable pageable) {
        User user = getUser(auth);

        Page<TransactionResponse> pageResult = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toEnrichedResponse);

        return PageResponse.of(pageResult);
    }

    public List<TransactionResponse> getByCategoryAndMonth(int category, String monthYear, Auth auth) {
        User user = getUser(auth);
        YearMonth ym = DateTimeUtils.parseYearMonth(monthYear);

        return transactionRepository
                .findAllByCategoryAndMonth(
                        user.getId(),
                        TransactionType.EXPENSE,
                        category,
                        ym.getMonthValue(),
                        ym.getYear())
                .stream()
                .map(this::toEnrichedResponse)
                .toList();
    }

    public TransactionResponse getById(UUID id, Auth auth) {
        User user = getUser(auth);
        Transaction transaction = transactionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        TransactionResponse response = toEnrichedResponse(transaction);
        if (transaction.getTransferId() != null) {
            List<Transaction> transferPair = transactionRepository.findAllByTransferId(transaction.getTransferId());
            for (Transaction partner : transferPair) {
                if (!partner.getId().equals(transaction.getId())) {
                    response.setTargetAccountId(partner.getAccountId());
                    response.setTargetTransactionId(partner.getId());
                    break;
                }
            }
        }
        return response;
    }

    public PageResponse<TransactionResponse> getTransactionByAccount(UUID accountId, Auth auth, Pageable pageable) {
        User user = getUser(auth);
        Account account = accountService.validateAccount(accountId, auth, Status.ACTIVE);
        Page<TransactionResponse> pageResult = transactionRepository
                .findByAccountIdAndUserId(account.getId(), user.getId(), pageable)
                .map(this::toEnrichedResponse);

        return PageResponse.of(pageResult);
    }

    public List<TransactionResponse> getRecentTransactionsByAccount(UUID accountId, Auth auth) {
        User user = getUser(auth);
        Account account = accountService.validateAccount(accountId, auth, Status.ACTIVE);

        return transactionRepository
                .findTop6ByAccountIdAndUserIdOrderByCreatedAtDesc(account.getId(), user.getId())
                .stream()
                .map(this::toEnrichedResponse)
                .toList();
    }

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request, Auth auth, MultipartFile file) {
        if (request.getType() == TransactionType.TRANSFER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng sử dụng API chuyển tiền riêng (/transactions/transfer) để thực hiện giao dịch chuyển khoản.");
        }

        Account account = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);

        validateCurrency(request.getCurrency(), account);
        validateCategory(request.getType(), request.getCategory());

        // Tạo transaction
        Transaction transaction = transactionMapper.toEntity(request, account.getUserId());
        if (request.getCreateAt() != null) {
            transaction.setCreatedAt(request.getCreateAt());
        }

        // Xử lý ảnh
        handleTransactionImage(transaction, request.isHaveImage(), file);

        // Xử lý tags
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            Set<Tag> resolvedTags = tagService.resolveTags(request.getTags(), account.getUserId());
            transaction.setTags(resolvedTags);
        }

        // Tính delta
        BigDecimal delta = accountService.calculateDelta(request);

        // Apply vào account
        accountService.applyDelta(account, delta);

        // Lưu transaction
        Transaction saved = transactionRepository.save(transaction);

        return toEnrichedResponse(saved);
    }

    @Transactional
    public TransactionUpdateResponse updateTransaction(TransactionRequest updated, Auth auth, UUID transactionId,
            MultipartFile file) {
        User user = getUser(auth);

        // Bảo mật: Kiểm tra cả transactionId và userId
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found or access denied"));

        // Ràng buộc toàn vẹn: Không cho phép chỉnh sửa trực tiếp giao dịch sinh ra từ Chuyển tiền, Quản lý nợ hoặc Mục tiêu tiết kiệm
        if (transaction.getTransferId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không thể chỉnh sửa trực tiếp giao dịch chuyển tiền. Vui lòng hủy giao dịch và tạo lại nếu cần.");
        }
        if (debtPaymentRepository.existsByTransactionId(transactionId) || debtRepository.existsByTransactionId(transactionId)) {
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
        transaction.setCategory(updated.getCategory());
        transaction.setCurrency(updated.getCurrency());
        transaction.setAccountId(updated.getAccountId());
        
        // Cập nhật ngày giờ giao dịch nếu có gửi lên
        if (updated.getCreateAt() != null) {
            transaction.setCreatedAt(updated.getCreateAt());
        }

        // Cập nhật tags
        if (updated.getTags() != null) {
            Set<Tag> resolvedTags = tagService.resolveTags(updated.getTags(), user.getId());
            transaction.setTags(resolvedTags);
        }

        validateCurrency(updated.getCurrency(), newAccount);
        validateCategory(updated.getType(), updated.getCategory());
        handleTransactionImage(transaction, updated.isHaveImage(), file);

        Transaction saved = transactionRepository.saveAndFlush(transaction);

        // Trả response có thêm finalDelta và quy đổi USD
        return toEnrichedUpdateResponse(saved, finalDelta);
    }


    @Transactional
    public boolean deleteTransaction(UUID id, Auth auth) {
        User user = getUser(auth);

        Transaction transaction = transactionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found or access denied"));

        // Ràng buộc toàn vẹn: Không cho phép xóa trực tiếp giao dịch sinh ra từ Quản lý nợ hoặc Mục tiêu tiết kiệm
        if (debtPaymentRepository.existsByTransactionId(transaction.getId()) || debtRepository.existsByTransactionId(transaction.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giao dịch này được tạo tự động từ Quản lý nợ. Vui lòng vào mục Quản lý nợ để hủy lần thanh toán hoặc khoản nợ này.");
        }
        if (savingGoalContributionRepository.existsByTransactionId(transaction.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giao dịch này được tạo tự động từ Mục tiêu tiết kiệm. Vui lòng vào mục Tiết kiệm để hủy lần đóng góp này.");
        }

        // Nếu là giao dịch chuyển tiền (có transferId) -> Hoàn tác đồng bộ cả 2 ví và xóa cả cặp giao dịch
        if (transaction.getTransferId() != null) {
            List<Transaction> transferPair = new ArrayList<>(transactionRepository.findAllByTransferId(transaction.getTransferId()));
            // Sắp xếp theo UUID của accountId để luôn áp dụng delta / khóa tài khoản theo thứ tự cố định, tránh deadlock
            transferPair.sort(Comparator.comparing(Transaction::getAccountId));
            for (Transaction t : transferPair) {
                if (t.getAccountId() != null) {
                    Account acc = accountService.validateAccount(t.getAccountId(), auth, Status.ACTIVE);
                    BigDecimal delta = t.getType() == TransactionType.INCOME
                            ? t.getAmount()
                            : t.getAmount().negate();
                    accountService.applyDelta(acc, delta.negate());
                }
                if (t.getImagePath() != null) {
                    deleteImage(t.getImagePath());
                }
                transactionRepository.delete(t);
            }
            return true;
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

        // Kiểm tra số dư tài khoản nguồn trước khi chuyển
        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance in source account");
        }

        UUID transferId = UUID.randomUUID();

        Transaction sourceTransaction = buildTransferTransaction(
                user.getId(),
                sourceAccount.getId(),
                request.getAmount(),
                TransactionType.EXPENSE,
                request.getDescription(),
                request.getCreateAt(),
                sourceAccount.getCurrency(),
                transferId);

        Transaction targetTransaction = buildTransferTransaction(
                user.getId(),
                targetAccount.getId(),
                request.getAmount(),
                TransactionType.INCOME,
                request.getDescription(),
                request.getCreateAt(),
                targetAccount.getCurrency(),
                transferId);

        // Chống Deadlock: Luôn cập nhật và khóa tài khoản theo thứ tự UUID cố định
        if (sourceAccount.getId().compareTo(targetAccount.getId()) < 0) {
            accountService.applyDelta(sourceAccount, request.getAmount().negate());
            accountService.applyDelta(targetAccount, request.getAmount());
        } else {
            accountService.applyDelta(targetAccount, request.getAmount());
            accountService.applyDelta(sourceAccount, request.getAmount().negate());
        }

        Transaction savedSourceTransaction = transactionRepository.save(sourceTransaction);
        Transaction savedTargetTransaction = transactionRepository.save(targetTransaction);

        TransactionResponse response = toEnrichedResponse(savedSourceTransaction);
        response.setTargetAccountId(targetAccount.getId());
        response.setTargetTransactionId(savedTargetTransaction.getId());
        response.setTransferId(transferId);

        return response;
    }

    private Transaction buildTransferTransaction(
            UUID userId,
            UUID accountId,
            BigDecimal amount,
            Integer transactionType,
            String description,
            LocalDateTime createdAt,
            int currency,
            UUID transferId) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(accountId);
        transaction.setAmount(amount);
        transaction.setType(transactionType);
        transaction.setCategory(Category.TRANSFER);
        transaction.setCurrency(currency);
        transaction.setDescription(description);
        transaction.setCreatedAt(createdAt);
        transaction.setTransferId(transferId);
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

        return PageResponse.of(result, this::toEnrichedResponse);
    }

    @Transactional
    public Transaction recordSystemTransaction(
            UUID userId,
            UUID accountId,
            BigDecimal amount,
            int type,
            int currency,
            int category,
            String description) {
        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setAccountId(accountId);
        tx.setAmount(amount);
        tx.setType(type);
        tx.setCurrency(currency);
        tx.setCategory(category);
        tx.setDescription(description);
        return transactionRepository.save(tx);
    }

    private User getUser(Auth auth) {
        return userService.getAuthenticatedUser(auth);
    }

    private void validateCurrency(int currency, Account account) {
        if (currency != account.getCurrency()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction currency does not match account currency");
        }
    }

    private void validateCategory(int type, int category) {
        if (type == TransactionType.EXPENSE) {
            if (!Category.isExpense(category)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category for EXPENSE transaction");
            }
        } else if (type == TransactionType.INCOME) {
            if (!Category.isIncome(category)) {
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
