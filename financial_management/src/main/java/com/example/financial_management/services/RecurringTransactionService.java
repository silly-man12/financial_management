package com.example.financial_management.services;

import com.example.financial_management.constant.RecurrenceType;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.RecurringTransaction;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.mapper.RecurringTransactionMapper;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.recurring.RecurringTransactionRequest;
import com.example.financial_management.model.recurring.RecurringTransactionResponse;
import com.example.financial_management.repository.RecurringTransactionRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecurringTransactionService {

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionMapper recurringTransactionMapper;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final CurrencyExchangeService currencyExchangeService;

    private RecurringTransactionResponse toEnrichedResponse(RecurringTransaction entity) {
        RecurringTransactionResponse response = recurringTransactionMapper.toResponse(entity);
        if (response != null) {
            response.setAmountUsd(currencyExchangeService.calculateUsd(response.getAmount(), response.getCurrency()));
        }
        return response;
    }

    /**
     * Lấy danh sách giao dịch định kỳ của user hiện tại
     * Hỗ trợ lọc theo status (nếu có)
     */
    public List<RecurringTransactionResponse> getAll(Auth auth, Integer status) {
        User user = getUser(auth);

        List<RecurringTransaction> list;
        if (status != null) {
            list = recurringTransactionRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), status);
        } else {
            list = recurringTransactionRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
        }

        return list.stream()
                .map(this::toEnrichedResponse)
                .toList();
    }

    /**
     * Xem chi tiết 1 giao dịch định kỳ
     */
    public RecurringTransactionResponse getById(UUID id, Auth auth) {
        User user = getUser(auth);
        RecurringTransaction entity = recurringTransactionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy giao dịch định kỳ"));

        return toEnrichedResponse(entity);
    }

    /**
     * Tạo mới giao dịch định kỳ
     */
    @Transactional
    public RecurringTransactionResponse create(RecurringTransactionRequest request, Auth auth) {
        User user = getUser(auth);

        // Validate account
        accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);

        // Validate request
        validateRequest(request);

        // Tạo entity
        RecurringTransaction entity = recurringTransactionMapper.toEntity(request, user.getId());
        entity.setStatus(Status.ACTIVE);

        // Tính nextExecutionDate
        entity.setNextExecutionDate(calculateNextExecutionDate(
                request.getStartDate(),
                request.getRecurrenceType(),
                request.getRecurrenceInterval()));

        RecurringTransaction saved = recurringTransactionRepository.save(entity);
        return toEnrichedResponse(saved);
    }

    /**
     * Cập nhật giao dịch định kỳ
     */
    @Transactional
    public RecurringTransactionResponse update(UUID id, RecurringTransactionRequest request, Auth auth) {
        User user = getUser(auth);

        RecurringTransaction entity = recurringTransactionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy giao dịch định kỳ"));

        // Validate account
        accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);

        // Validate request
        validateRequest(request);

        // Cập nhật các trường từ request
        recurringTransactionMapper.updateEntity(request, entity);

        // Tính lại nextExecutionDate
        entity.setNextExecutionDate(calculateNextExecutionDate(
                request.getStartDate(),
                request.getRecurrenceType(),
                request.getRecurrenceInterval()));

        RecurringTransaction saved = recurringTransactionRepository.saveAndFlush(entity);
        return toEnrichedResponse(saved);
    }

    /**
     * Bật / Tạm dừng giao dịch định kỳ (ACTIVE = 1, INACTIVE = 2)
     */
    @Transactional
    public RecurringTransactionResponse updateStatus(UUID id, int newStatus, Auth auth) {
        User user = getUser(auth);

        RecurringTransaction entity = recurringTransactionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy giao dịch định kỳ"));

        if (newStatus != Status.ACTIVE && newStatus != Status.INACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ");
        }

        entity.setStatus(newStatus);
        RecurringTransaction saved = recurringTransactionRepository.saveAndFlush(entity);
        return toEnrichedResponse(saved);
    }

    /**
     * Xóa quy tắc lặp lại (không xóa các giao dịch lịch sử đã sinh ra trước đó)
     */
    @Transactional
    public boolean delete(UUID id, Auth auth) {
        User user = getUser(auth);

        RecurringTransaction entity = recurringTransactionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy giao dịch định kỳ"));

        recurringTransactionRepository.delete(entity);
        return true;
    }

    /**
     * Tự động quét và thực thi các giao dịch định kỳ đến hạn (dành cho Cronjob hàng ngày)
     */
    @Transactional
    public void executeAllDue() {
        LocalDate today = LocalDate.now();
        List<RecurringTransaction> dueList = recurringTransactionRepository
                .findAllByStatusAndNextExecutionDateLessThanEqual(Status.ACTIVE, today);

        log.info("Cronjob quét thấy {} giao dịch định kỳ đến hạn xử lý", dueList.size());
        for (RecurringTransaction recurring : dueList) {
            try {
                createTransactionFromRecurring(recurring);
                advanceNextExecutionDate(recurring);
                recurringTransactionRepository.saveAndFlush(recurring);
            } catch (Exception e) {
                log.error("Lỗi khi tự động thực thi recurring transaction id={}", recurring.getId(), e);
            }
        }
    }

    /**
     * Thực thi ngay lập tức 1 quy tắc cụ thể theo yêu cầu của user ("Ghi nhận ngay")
     */
    @Transactional
    public RecurringTransactionResponse executeNow(UUID id, Auth auth) {
        User user = getUser(auth);

        RecurringTransaction entity = recurringTransactionRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy giao dịch định kỳ"));

        if (entity.getStatus() != Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giao dịch định kỳ đang tạm dừng hoặc không hoạt động");
        }

        createTransactionFromRecurring(entity);
        advanceNextExecutionDate(entity);
        RecurringTransaction saved = recurringTransactionRepository.saveAndFlush(entity);

        return recurringTransactionMapper.toResponse(saved);
    }

    // ========================= HELPER METHODS =========================

    /**
     * Tạo Transaction thực tế từ RecurringTransaction
     */
    private void createTransactionFromRecurring(RecurringTransaction recurring) {
        Account account = accountService.getAccountById(recurring.getAccountId());

        Transaction transaction = new Transaction();
        transaction.setUserId(recurring.getUserId());
        transaction.setAccountId(recurring.getAccountId());
        transaction.setAmount(recurring.getAmount());
        transaction.setType(recurring.getType());
        transaction.setCategory(recurring.getCategory());
        transaction.setCurrency(recurring.getCurrency());
        transaction.setDescription(recurring.getDescription());

        // Tính delta và áp dụng vào account balance
        BigDecimal delta = recurring.getType() == TransactionType.INCOME
                ? recurring.getAmount()
                : recurring.getAmount().negate();
        accountService.applyDelta(account, delta);

        transactionRepository.save(transaction);

        log.info("Đã tạo giao dịch từ recurring_transaction id={}, amount={}, type={}",
                recurring.getId(), recurring.getAmount(), recurring.getType());
    }

    /**
     * Tính nextExecutionDate dựa trên startDate hiện tại
     * Nếu startDate chưa đến -> nextExecutionDate = startDate
     * Nếu startDate đã qua -> tính lần tiếp theo kể từ hôm nay
     */
    private LocalDate calculateNextExecutionDate(LocalDate startDate, int recurrenceType, int interval) {
        LocalDate today = LocalDate.now();

        if (!startDate.isBefore(today)) {
            return startDate;
        }

        // startDate đã qua, cần tính lần thực hiện tiếp theo
        LocalDate next = startDate;
        while (!next.isAfter(today)) {
            next = advanceDate(next, recurrenceType, interval);
        }
        return next;
    }

    /**
     * Cập nhật nextExecutionDate sang lần tiếp theo
     * Nếu vượt quá endDate -> tự động chuyển status = INACTIVE
     */
    private void advanceNextExecutionDate(RecurringTransaction recurring) {
        LocalDate next = advanceDate(
                recurring.getNextExecutionDate(),
                recurring.getRecurrenceType(),
                recurring.getRecurrenceInterval());

        // Kiểm tra nếu vượt quá endDate
        if (recurring.getEndDate() != null && next.isAfter(recurring.getEndDate())) {
            recurring.setStatus(Status.INACTIVE);
            log.info("Giao dịch định kỳ id={} đã hết hạn, chuyển sang INACTIVE", recurring.getId());
        }

        recurring.setNextExecutionDate(next);
    }

    /**
     * Tính ngày tiếp theo dựa trên loại chu kỳ
     */
    private LocalDate advanceDate(LocalDate current, int recurrenceType, int interval) {
        return switch (recurrenceType) {
            case RecurrenceType.DAILY -> current.plusDays(interval);
            case RecurrenceType.WEEKLY -> current.plusWeeks(interval);
            case RecurrenceType.MONTHLY -> current.plusMonths(interval);
            case RecurrenceType.YEARLY -> current.plusYears(interval);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Loại chu kỳ không hợp lệ: " + recurrenceType);
        };
    }

    /**
     * Validate request đầu vào
     */
    private void validateRequest(RecurringTransactionRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền phải lớn hơn 0");
        }

        if (request.getStartDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không được để trống");
        }

        if (request.getRecurrenceInterval() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng cách chu kỳ phải lớn hơn 0");
        }

        int rt = request.getRecurrenceType();
        if (rt < RecurrenceType.DAILY || rt > RecurrenceType.YEARLY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại chu kỳ không hợp lệ");
        }

        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        }
    }

    private User getUser(Auth auth) {
        return userRepository.findByIdAndStatus(UUID.fromString(auth.getId()), Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
