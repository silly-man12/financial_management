package com.example.financial_management.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.financial_management.constant.Category;
import com.example.financial_management.constant.DebtStatus;
import com.example.financial_management.constant.DebtType;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.Debt;
import com.example.financial_management.entity.DebtPayment;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.mapper.DebtMapper;
import com.example.financial_management.mapper.DebtPaymentMapper;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.debt.DebtPaymentRequest;
import com.example.financial_management.model.debt.DebtPaymentResponse;
import com.example.financial_management.model.debt.DebtRequest;
import com.example.financial_management.model.debt.DebtResponse;
import com.example.financial_management.model.debt.DebtUpdateRequest;
import com.example.financial_management.repository.DebtPaymentRepository;
import com.example.financial_management.repository.DebtRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DebtService {

    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final DebtMapper debtMapper;
    private final DebtPaymentMapper debtPaymentMapper;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;

    /**
     * 1. Lấy danh sách khoản nợ (hỗ trợ lọc theo type và/hoặc status)
     */
    public List<DebtResponse> getAll(Auth auth, Integer type, Integer status) {
        User user = getUser(auth);

        List<Debt> list;
        if (type != null && status != null) {
            list = debtRepository.findAllByUserIdAndTypeAndStatusOrderByCreatedAtDesc(user.getId(), type, status);
        } else if (type != null) {
            list = debtRepository.findAllByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), type);
        } else if (status != null) {
            list = debtRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), status);
        } else {
            list = debtRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
        }

        // Tự động kiểm tra cập nhật quá hạn nếu cần
        return list.stream()
                .map(this::checkAndMapOverdue)
                .map(debtMapper::toResponse)
                .toList();
    }

    /**
     * 2. Xem chi tiết 1 khoản nợ + lịch sử các lần trả
     */
    public DebtResponse getById(UUID id, Auth auth) {
        User user = getUser(auth);

        Debt debt = debtRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản nợ"));

        debt = checkAndMapOverdue(debt);

        DebtResponse response = debtMapper.toResponse(debt);

        // Lấy lịch sử các lần trả nợ / thu nợ
        List<DebtPaymentResponse> payments = debtPaymentRepository.findAllByDebtIdOrderByPaymentDateDesc(id)
                .stream()
                .map(debtPaymentMapper::toResponse)
                .toList();

        response.setPayments(payments);
        return response;
    }

    /**
     * 3. Tạo khoản nợ mới (Khởi tạo khoản vay/cho vay)
     */
    @Transactional
    public DebtResponse create(DebtRequest request, Auth auth) {
        User user = getUser(auth);

        validateDebtRequest(request);

        Debt debt = debtMapper.toEntity(request, user.getId());
        debt.setRemainingAmount(request.getInitialAmount());
        debt.setStatus(DebtStatus.IN_PROGRESS);

        Debt saved = debtRepository.save(debt);

        // Xử lý dòng tiền nếu có chọn tài khoản nguồn/đích
        if (request.getAccountId() != null) {
            Account account = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);

            if (request.getType() == DebtType.BORROW) {
                // Đi vay -> Nhận tiền vào ví (tăng số dư) -> INCOME
                accountService.applyDelta(account, request.getInitialAmount());
                recordDebtTransaction(user.getId(), account.getId(), request.getInitialAmount(),
                        TransactionType.INCOME, account.getCurrency(),
                        "Đi vay từ: " + request.getPersonName());
            } else if (request.getType() == DebtType.LEND) {
                // Cho vay -> Xuất tiền từ ví (giảm số dư) -> EXPENSE
                accountService.applyDelta(account, request.getInitialAmount().negate());
                recordDebtTransaction(user.getId(), account.getId(), request.getInitialAmount(),
                        TransactionType.EXPENSE, account.getCurrency(),
                        "Cho vay: " + request.getPersonName());
            }
        }

        return debtMapper.toResponse(saved);
    }

    /**
     * 4. Sửa thông tin khoản nợ
     */
    @Transactional
    public DebtResponse update(UUID id, DebtUpdateRequest request, Auth auth) {
        User user = getUser(auth);

        Debt debt = debtRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản nợ"));

        debtMapper.updateEntity(request, debt);

        // Kiểm tra lại trạng thái quá hạn
        debt = checkAndMapOverdue(debt);

        Debt saved = debtRepository.saveAndFlush(debt);
        return debtMapper.toResponse(saved);
    }

    /**
     * 5. Xóa khoản nợ (Hard delete khỏi DB):
     * Điều kiện:
     * - Khoản nợ phải đã ở trạng thái PAID (đã trả xong hoặc đã xóa nợ).
     * - Nếu khoản nợ được PAID do "xóa nợ / miễn nợ" (settle) -> KHÔNG CHO XÓA (cần
     * giữ lại lịch sử truy vấn).
     * - Chỉ cho xóa khi khoản nợ được PAID do người dùng tự thanh toán hết.
     */
    @Transactional
    public boolean delete(UUID id, Auth auth) {
        User user = getUser(auth);

        Debt debt = debtRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản nợ"));

        // 1. Chưa tất toán -> Không cho xóa
        if (debt.getStatus() != DebtStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Khoản nợ chưa được tất toán (còn nợ " + debt.getRemainingAmount() + " đ). "
                            + "Vui lòng thanh toán hoặc thực hiện xóa nợ (settle) trước.");
        }

        // 2. Đã tất toán nhưng do "xóa nợ / miễn nợ" -> Không cho xóa (giữ lại lịch sử)
        String note = debt.getNote() != null ? debt.getNote() : "";
        if (note.contains("[Đã xóa nợ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Khoản nợ này đã được xóa nợ / miễn nợ trước đó. "
                            + "Không thể xóa vĩnh viễn vì cần giữ lại lịch sử để truy vấn.");
        }

        // 3. Đã tất toán do tự thanh toán hết -> Cho phép xóa
        debtPaymentRepository.deleteAllByDebtId(id);
        debtRepository.delete(debt);
        log.info("Đã xóa vĩnh viễn khoản nợ id={} khỏi hệ thống (khoản nợ đã tự thanh toán xong)", id);
        return true;
    }

    /**
     * 6. Xóa nợ / Tất toán khoản nợ (Settle / Forgive):
     * Xóa nợ nhưng KHÔNG xóa khoản nợ khỏi DB:
     * -> Đưa số nợ còn lại về 0 đ.
     * -> Chuyển trạng thái sang PAID (2 - Đã trả xong).
     * -> Tạo 1 bản ghi DebtPayment (accountId = null, không tạo Transaction) để lưu
     * lịch sử miễn nợ.
     * -> Giữ nguyên 100% số dư tài khoản ngân hàng và sao kê giao dịch.
     */
    @Transactional
    public DebtResponse settle(UUID id, String reason, Auth auth) {
        User user = getUser(auth);

        Debt debt = debtRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản nợ"));

        if (debt.getStatus() == DebtStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoản nợ này đã được tất toán trước đó");
        }

        BigDecimal remaining = debt.getRemainingAmount();

        // 1. Tạo bản ghi lịch sử xóa nợ trong DebtPayment (accountId = null -> không
        // tạo Transaction, không động tới ví)
        DebtPayment settlePayment = new DebtPayment();
        settlePayment.setDebtId(debt.getId());
        settlePayment.setAmount(remaining);
        settlePayment.setPaymentDate(LocalDate.now());
        settlePayment.setAccountId(null);
        settlePayment.setTransactionId(null);
        settlePayment.setNote("Được xóa nợ / Miễn nợ" + (reason != null && !reason.isBlank() ? ": " + reason : ""));
        debtPaymentRepository.save(settlePayment);

        // 2. Cập nhật trạng thái khoản nợ
        debt.setRemainingAmount(BigDecimal.ZERO);
        debt.setStatus(DebtStatus.PAID);

        String existingNote = debt.getNote() != null ? debt.getNote() : "";
        String settleInfo = "[Đã xóa nợ / Miễn " + remaining + " đ còn lại"
                + (reason != null && !reason.isBlank() ? ": " + reason : "") + "]";
        debt.setNote((existingNote.isEmpty() ? "" : existingNote + " | ") + settleInfo);

        debtRepository.saveAndFlush(debt);
        log.info("Đã xóa nợ cho khoản nợ id={} (miễn {} đ, giữ nguyên số dư tài khoản)", id, remaining);

        return getById(id, auth);
    }

    /**
     * 6. Ghi nhận 1 lần trả nợ (trả bớt / thu nợ)
     * Backend tự trừ remainingAmount. Nếu còn <= 0 đ thì tự đổi status sang PAID
     * (Đã tất toán).
     */
    @Transactional
    public DebtResponse addPayment(UUID id, DebtPaymentRequest request, Auth auth) {
        User user = getUser(auth);

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền trả phải lớn hơn 0");
        }

        Debt debt = debtRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản nợ"));

        if (debt.getStatus() == DebtStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoản nợ này đã được thanh toán xong");
        }

        if (request.getAmount().compareTo(debt.getRemainingAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số tiền trả vượt quá số nợ còn lại (" + debt.getRemainingAmount() + " đ)");
        }

        // Xử lý dòng tiền ví/tài khoản và tạo transaction nếu có chọn accountId
        UUID transactionId = null;
        if (request.getAccountId() != null) {
            Account account = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);

            if (debt.getType() == DebtType.BORROW) {
                // Mình đi vay -> Giờ trả bớt nợ -> Trừ tiền từ ví -> EXPENSE
                accountService.applyDelta(account, request.getAmount().negate());
                Transaction tx = recordDebtTransaction(user.getId(), account.getId(), request.getAmount(),
                        TransactionType.EXPENSE, account.getCurrency(),
                        "Trả nợ cho: " + debt.getPersonName());
                transactionId = tx.getId();
            } else if (debt.getType() == DebtType.LEND) {
                // Mình cho vay -> Giờ thu bớt nợ -> Cộng tiền vào ví -> INCOME
                accountService.applyDelta(account, request.getAmount());
                Transaction tx = recordDebtTransaction(user.getId(), account.getId(), request.getAmount(),
                        TransactionType.INCOME, account.getCurrency(),
                        "Thu nợ từ: " + debt.getPersonName());
                transactionId = tx.getId();
            }
        }

        // Tạo bản ghi DebtPayment có lưu transactionId
        DebtPayment payment = debtPaymentMapper.toEntity(request, debt.getId());
        payment.setTransactionId(transactionId);
        debtPaymentRepository.save(payment);

        // Cập nhật số tiền nợ còn lại
        BigDecimal newRemaining = debt.getRemainingAmount().subtract(request.getAmount());
        debt.setRemainingAmount(newRemaining);

        // Nếu đã trả hết (remainingAmount <= 0) -> Tự động đổi status sang PAID (2)
        if (newRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            debt.setRemainingAmount(BigDecimal.ZERO);
            debt.setStatus(DebtStatus.PAID);
            log.info("Khoản nợ id={} đã tất toán xong (PAID)", debt.getId());
        } else {
            debt = checkAndMapOverdue(debt);
        }

        debtRepository.saveAndFlush(debt);

        return getById(id, auth);
    }

    /**
     * 7. Hủy 1 lần trả tiền (hoàn tác số dư nợ, số dư ví và xóa Transaction)
     */
    @Transactional
    public DebtResponse deletePayment(UUID debtId, UUID paymentId, Auth auth) {
        User user = getUser(auth);

        Debt debt = debtRepository.findByIdAndUserId(debtId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khoản nợ"));

        DebtPayment payment = debtPaymentRepository.findByIdAndDebtId(paymentId, debtId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lần thanh toán này"));

        // 1. Hoàn tác số tiền nợ còn lại
        BigDecimal restoredRemaining = debt.getRemainingAmount().add(payment.getAmount());
        debt.setRemainingAmount(restoredRemaining);

        // 2. Cập nhật lại status nếu trước đó đã PAID
        if (restoredRemaining.compareTo(BigDecimal.ZERO) > 0) {
            debt.setStatus(DebtStatus.IN_PROGRESS);
            debt = checkAndMapOverdue(debt);
        }

        // 3. Hoàn tác số dư tài khoản nếu có
        if (payment.getAccountId() != null) {
            Account account = accountService.validateAccount(payment.getAccountId(), auth, Status.ACTIVE);

            if (debt.getType() == DebtType.BORROW) {
                // Đi vay lúc trước trả tiền (bị trừ ví) -> Giờ hủy trả thì cộng lại tiền vào ví
                accountService.applyDelta(account, payment.getAmount());
            } else if (debt.getType() == DebtType.LEND) {
                // Cho vay lúc trước thu tiền (được cộng ví) -> Giờ hủy thì trừ lại tiền từ ví
                accountService.applyDelta(account, payment.getAmount().negate());
            }
        }

        // 4. XÓA BẢN GHI TRANSACTION TƯƠNG ỨNG TRONG SAO KÊ
        if (payment.getTransactionId() != null) {
            transactionRepository.deleteById(payment.getTransactionId());
        }

        // 5. Xóa bản ghi payment
        debtPaymentRepository.delete(payment);
        debtRepository.saveAndFlush(debt);

        return getById(debtId, auth);
    }

    // ========================= HELPER METHODS =========================

    private Debt checkAndMapOverdue(Debt debt) {
        if (debt.getStatus() == DebtStatus.PAID) {
            return debt;
        }

        if (debt.getDueDate() != null && debt.getDueDate().isBefore(LocalDate.now())) {
            debt.setStatus(DebtStatus.OVERDUE);
        } else if (debt.getStatus() == DebtStatus.OVERDUE
                && (debt.getDueDate() == null || !debt.getDueDate().isBefore(LocalDate.now()))) {
            debt.setStatus(DebtStatus.IN_PROGRESS);
        }
        return debt;
    }

    private void validateDebtRequest(DebtRequest request) {
        if (request.getInitialAmount() == null || request.getInitialAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền nợ ban đầu phải lớn hơn 0");
        }

        if (request.getType() == null || (request.getType() != DebtType.BORROW && request.getType() != DebtType.LEND)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Loại khoản nợ không hợp lệ (1: Đi vay, 2: Cho vay)");
        }

        if (request.getStartDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không được để trống");
        }

        if (request.getDueDate() != null && request.getDueDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày hẹn trả không thể trước ngày mượn");
        }
    }

    private Transaction recordDebtTransaction(UUID userId, UUID accountId, BigDecimal amount, int type, int currency,
            String description) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(accountId);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setCategory(Category.DEBT);
        transaction.setCurrency(currency);
        transaction.setDescription(description);
        return transactionRepository.save(transaction);
    }

    private User getUser(Auth auth) {
        return userRepository.findByIdAndStatus(UUID.fromString(auth.getId()), Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
