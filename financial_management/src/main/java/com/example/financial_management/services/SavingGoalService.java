package com.example.financial_management.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.financial_management.constant.Category;
import com.example.financial_management.constant.SavingGoalStatus;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.SavingGoal;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.mapper.SavingGoalMapper;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.saving_goal.SavingGoalDepositRequest;
import com.example.financial_management.model.saving_goal.SavingGoalRequest;
import com.example.financial_management.model.saving_goal.SavingGoalResponse;
import com.example.financial_management.model.saving_goal.SavingGoalUpdateRequest;
import com.example.financial_management.model.saving_goal.SavingGoalWithdrawRequest;
import com.example.financial_management.repository.SavingGoalRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SavingGoalService {

    private final SavingGoalRepository savingGoalRepository;
    private final SavingGoalMapper savingGoalMapper;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;

    /**
     * 1. Lấy danh sách mục tiêu của user (hỗ trợ lọc theo status)
     */
    public List<SavingGoalResponse> getAll(Auth auth, Integer status) {
        User user = getUser(auth);

        List<SavingGoal> list;
        if (status != null) {
            list = savingGoalRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), status);
        } else {
            list = savingGoalRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
        }

        return list.stream()
                .map(savingGoalMapper::toResponse)
                .toList();
    }

    /**
     * 2. Xem chi tiết 1 mục tiêu và tiến độ %
     */
    public SavingGoalResponse getById(UUID id, Auth auth) {
        User user = getUser(auth);

        SavingGoal savingGoal = savingGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

        return savingGoalMapper.toResponse(savingGoal);
    }

    /**
     * 3. Tạo mục tiêu mới (khởi tạo với số tiền ban đầu, có thể = 0)
     */
    @Transactional
    public SavingGoalResponse create(SavingGoalRequest request, Auth auth) {
        User user = getUser(auth);

        if (request.getTargetAmount() == null || request.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền mục tiêu phải lớn hơn 0");
        }

        BigDecimal initialAmount = request.getInitialAmount() != null ? request.getInitialAmount() : BigDecimal.ZERO;
        if (initialAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền ban đầu không được âm");
        }

        // Tạo entity SavingGoal
        SavingGoal savingGoal = savingGoalMapper.toEntity(request, user.getId());
        savingGoal.setCurrentAmount(initialAmount);

        // Tự động kiểm tra hoàn thành nếu số tiền ban đầu >= mục tiêu
        if (initialAmount.compareTo(request.getTargetAmount()) >= 0) {
            savingGoal.setStatus(SavingGoalStatus.COMPLETED);
        } else {
            savingGoal.setStatus(SavingGoalStatus.IN_PROGRESS);
        }

        SavingGoal saved = savingGoalRepository.save(savingGoal);

        // Nếu có số tiền ban đầu > 0 và có chọn tài khoản nguồn, thực hiện trích tiền & tạo transaction sao kê
        if (initialAmount.compareTo(BigDecimal.ZERO) > 0 && request.getAccountId() != null) {
            Account account = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);
            accountService.applyDelta(account, initialAmount.negate());

            // Lưu lịch sử biến động số dư tài khoản
            saveSavingTransaction(user.getId(), account.getId(), initialAmount, account.getCurrency(),
                    "Khởi tạo góp quỹ mục tiêu: " + saved.getName());
        }

        return savingGoalMapper.toResponse(saved);
    }

    /**
     * 4. Cập nhật mục tiêu (sửa tên, số tiền đích, hạn chót, màu sắc, mô tả)
     */
    @Transactional
    public SavingGoalResponse update(UUID id, SavingGoalUpdateRequest request, Auth auth) {
        User user = getUser(auth);

        SavingGoal savingGoal = savingGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

        if (request.getTargetAmount() == null || request.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền mục tiêu phải lớn hơn 0");
        }

        savingGoalMapper.updateEntity(request, savingGoal);

        // Tự động cập nhật status theo targetAmount mới
        if (savingGoal.getCurrentAmount().compareTo(savingGoal.getTargetAmount()) >= 0) {
            savingGoal.setStatus(SavingGoalStatus.COMPLETED);
        } else if (savingGoal.getStatus() == SavingGoalStatus.COMPLETED) {
            savingGoal.setStatus(SavingGoalStatus.IN_PROGRESS);
        }

        SavingGoal saved = savingGoalRepository.saveAndFlush(savingGoal);
        return savingGoalMapper.toResponse(saved);
    }

    /**
     * 5. Nạp tiền / Góp quỹ vào mục tiêu (tự động chuyển status = 2 nếu đạt >= 100%)
     */
    @Transactional
    public SavingGoalResponse deposit(UUID id, SavingGoalDepositRequest request, Auth auth) {
        User user = getUser(auth);

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền nạp phải lớn hơn 0");
        }

        SavingGoal savingGoal = savingGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

        // Trích trừ tiền từ tài khoản nguồn & lưu transaction sao kê nếu được chỉ định
        if (request.getAccountId() != null) {
            Account account = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);
            accountService.applyDelta(account, request.getAmount().negate());

            // Lưu lịch sử biến động số dư tài khoản
            saveSavingTransaction(user.getId(), account.getId(), request.getAmount(), account.getCurrency(),
                    "Góp quỹ mục tiêu: " + savingGoal.getName());
        }

        // Tăng số tiền hiện có trong mục tiêu
        savingGoal.setCurrentAmount(savingGoal.getCurrentAmount().add(request.getAmount()));

        // Tự động đổi status = 2 (COMPLETED) nếu đã đạt >= 100%
        if (savingGoal.getCurrentAmount().compareTo(savingGoal.getTargetAmount()) >= 0) {
            savingGoal.setStatus(SavingGoalStatus.COMPLETED);
            log.info("Mục tiêu tiết kiệm id={} đã hoàn thành (đạt >= 100%)", savingGoal.getId());
        }

        SavingGoal saved = savingGoalRepository.saveAndFlush(savingGoal);
        return savingGoalMapper.toResponse(saved);
    }

    /**
     * 6. Rút tiền từ mục tiêu về tài khoản ví / ngân hàng
     */
    @Transactional
    public SavingGoalResponse withdraw(UUID id, SavingGoalWithdrawRequest request, Auth auth) {
        User user = getUser(auth);

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền rút phải lớn hơn 0");
        }

        SavingGoal savingGoal = savingGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

        if (request.getAmount().compareTo(savingGoal.getCurrentAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền rút vượt quá số tiền hiện có trong mục tiêu");
        }

        // Hoàn tiền về tài khoản đích & lưu transaction sao kê nếu được chỉ định
        if (request.getAccountId() != null) {
            Account account = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);
            accountService.applyDelta(account, request.getAmount());

            // Lưu lịch sử biến động số dư tài khoản
            saveSavingTransaction(user.getId(), account.getId(), request.getAmount(), account.getCurrency(),
                    "Rút tiền từ mục tiêu: " + savingGoal.getName());
        }

        // Giảm số tiền hiện có trong mục tiêu
        savingGoal.setCurrentAmount(savingGoal.getCurrentAmount().subtract(request.getAmount()));

        // Nếu rút xong mà dưới mục tiêu, chuyển trạng thái về IN_PROGRESS (1)
        if (savingGoal.getCurrentAmount().compareTo(savingGoal.getTargetAmount()) < 0) {
            savingGoal.setStatus(SavingGoalStatus.IN_PROGRESS);
        }

        SavingGoal saved = savingGoalRepository.saveAndFlush(savingGoal);
        return savingGoalMapper.toResponse(saved);
    }

    /**
     * 7. Xóa mục tiêu tiết kiệm
     */
    @Transactional
    public boolean delete(UUID id, Auth auth) {
        User user = getUser(auth);

        SavingGoal savingGoal = savingGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

        savingGoalRepository.delete(savingGoal);
        return true;
    }

    /**
     * Helper lưu bản ghi Transaction chuyển tiền nội bộ phục vụ sao kê tài khoản
     */
    private void saveSavingTransaction(UUID userId, UUID accountId, BigDecimal amount, int currency, String description) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(accountId);
        transaction.setAmount(amount);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setCategory(Category.TRANSFER);
        transaction.setCurrency(currency);
        transaction.setDescription(description);
        transactionRepository.save(transaction);
    }

    private User getUser(Auth auth) {
        return userRepository.findByIdAndStatus(UUID.fromString(auth.getId()), Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
