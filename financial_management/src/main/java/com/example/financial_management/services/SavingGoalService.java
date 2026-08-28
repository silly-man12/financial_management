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
import com.example.financial_management.constant.SavingContributionType;
import com.example.financial_management.constant.SavingGoalStatus;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.SavingGoal;
import com.example.financial_management.entity.SavingGoalContribution;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.mapper.SavingGoalContributionMapper;
import com.example.financial_management.mapper.SavingGoalMapper;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.saving_goal.SavingGoalContributionRequest;
import com.example.financial_management.model.saving_goal.SavingGoalContributionResponse;
import com.example.financial_management.model.saving_goal.SavingGoalDepositRequest;
import com.example.financial_management.model.saving_goal.SavingGoalRequest;
import com.example.financial_management.model.saving_goal.SavingGoalResponse;
import com.example.financial_management.model.saving_goal.SavingGoalUpdateRequest;
import com.example.financial_management.model.saving_goal.SavingGoalWithdrawRequest;
import com.example.financial_management.repository.SavingGoalContributionRepository;
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
    private final SavingGoalContributionRepository savingGoalContributionRepository;
    private final SavingGoalMapper savingGoalMapper;
    private final SavingGoalContributionMapper savingGoalContributionMapper;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final CurrencyExchangeService currencyExchangeService;

    private SavingGoalResponse toEnrichedResponse(SavingGoal goal) {
        SavingGoalResponse response = savingGoalMapper.toResponse(goal);
        if (response != null) {
            response.setTargetAmountUsd(currencyExchangeService.toUsd(response.getTargetAmount()));
            response.setCurrentAmountUsd(currencyExchangeService.toUsd(response.getCurrentAmount()));
            if (response.getContributions() != null) {
                response.getContributions().forEach(this::enrichContribution);
            }
        }
        return response;
    }

    private SavingGoalContributionResponse enrichContribution(SavingGoalContributionResponse contribution) {
        if (contribution != null) {
            contribution.setAmountUsd(currencyExchangeService.toUsd(contribution.getAmount()));
        }
        return contribution;
    }

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
                .map(this::toEnrichedResponse)
                .toList();
    }

    /**
     * 2. Xem chi tiết 1 mục tiêu và tiến độ % + Lịch sử góp quỹ
     */
    public SavingGoalResponse getById(UUID id, Auth auth) {
        User user = getUser(auth);

        SavingGoal savingGoal = savingGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

        SavingGoalResponse response = toEnrichedResponse(savingGoal);

        // Lấy lịch sử góp quỹ
        List<SavingGoalContributionResponse> contributions = savingGoalContributionRepository
                .findAllBySavingGoalIdOrderByContributionDateDesc(id)
                .stream()
                .map(savingGoalContributionMapper::toResponse)
                .map(this::enrichContribution)
                .toList();
        response.setContributions(contributions);

        return response;
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

        // Nếu có số tiền ban đầu > 0: lưu contribution & xử lý tài khoản nguồn
        if (initialAmount.compareTo(BigDecimal.ZERO) > 0) {
            UUID txId = null;
            if (request.getAccountId() != null) {
                Account account = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);
                accountService.applyDelta(account, initialAmount.negate());

                // Lưu lịch sử biến động số dư tài khoản
                Transaction tx = saveSavingTransaction(user.getId(), account.getId(), initialAmount,
                        TransactionType.EXPENSE, account.getCurrency(),
                        "Khởi tạo góp quỹ mục tiêu: " + saved.getName());
                txId = tx.getId();
            }

            // Lưu bản ghi lịch sử góp quỹ ban đầu
            SavingGoalContribution contribution = new SavingGoalContribution();
            contribution.setSavingGoalId(saved.getId());
            contribution.setAmount(initialAmount);
            contribution.setContributionDate(LocalDate.now());
            contribution.setType(SavingContributionType.DEPOSIT);
            contribution.setAccountId(request.getAccountId());
            contribution.setTransactionId(txId);
            contribution.setNote("Khởi tạo số dư ban đầu cho mục tiêu: " + saved.getName());
            savingGoalContributionRepository.save(contribution);
        }

        return getById(saved.getId(), auth);
    }

    /**
     * 4. Cập nhật mục tiêu (sửa tên, số tiền đích, hạn chót, màu sắc, mô tả)
     */
    @Transactional
    public SavingGoalResponse update(UUID id, SavingGoalUpdateRequest request, Auth auth) {
        User user = getUser(auth);

        SavingGoal savingGoal = savingGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

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
        return getById(saved.getId(), auth);
    }

    /**
     * 5. Nạp tiền / Góp quỹ vào mục tiêu (tự động chuyển status = 2 nếu đạt >=
     * 100%)
     */
    @Transactional
    public SavingGoalResponse deposit(UUID id, SavingGoalDepositRequest request, Auth auth) {
        User user = getUser(auth);

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền nạp phải lớn hơn 0");
        }

        SavingGoal savingGoal = savingGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

        UUID txId = null;
        // Trích trừ tiền từ tài khoản nguồn & lưu transaction sao kê nếu được chỉ định
        if (request.getAccountId() != null) {
            Account account = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);
            accountService.applyDelta(account, request.getAmount().negate());

            Transaction tx = saveSavingTransaction(user.getId(), account.getId(), request.getAmount(),
                    TransactionType.EXPENSE, account.getCurrency(),
                    request.getNote() != null && !request.getNote().isBlank() ? request.getNote()
                            : "Góp quỹ mục tiêu: " + savingGoal.getName());
            txId = tx.getId();
        }

        // Tăng số tiền hiện có trong mục tiêu
        savingGoal.setCurrentAmount(savingGoal.getCurrentAmount().add(request.getAmount()));

        // Tự động đổi status = 2 (COMPLETED) nếu đã đạt >= 100%
        if (savingGoal.getCurrentAmount().compareTo(savingGoal.getTargetAmount()) >= 0) {
            savingGoal.setStatus(SavingGoalStatus.COMPLETED);
            log.info("Mục tiêu tiết kiệm id={} đã hoàn thành (đạt >= 100%)", savingGoal.getId());
        }

        savingGoalRepository.saveAndFlush(savingGoal);

        // Lưu bản ghi lịch sử góp quỹ
        SavingGoalContribution contribution = new SavingGoalContribution();
        contribution.setSavingGoalId(savingGoal.getId());
        contribution.setAmount(request.getAmount());
        contribution.setContributionDate(
                request.getContributionDate() != null ? request.getContributionDate() : LocalDate.now());
        contribution.setType(SavingContributionType.DEPOSIT);
        contribution.setAccountId(request.getAccountId());
        contribution.setTransactionId(txId);
        contribution.setNote(request.getNote() != null && !request.getNote().isBlank() ? request.getNote()
                : "Góp quỹ mục tiêu: " + savingGoal.getName());
        savingGoalContributionRepository.save(contribution);

        return getById(savingGoal.getId(), auth);
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
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

        if (request.getAmount().compareTo(savingGoal.getCurrentAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số tiền rút vượt quá số tiền hiện có trong mục tiêu");
        }

        UUID txId = null;
        // Hoàn tiền về tài khoản đích & lưu transaction sao kê nếu được chỉ định
        if (request.getAccountId() != null) {
            Account account = accountService.validateAccount(request.getAccountId(), auth, Status.ACTIVE);
            accountService.applyDelta(account, request.getAmount());

            Transaction tx = saveSavingTransaction(user.getId(), account.getId(), request.getAmount(),
                    TransactionType.INCOME, account.getCurrency(),
                    request.getNote() != null && !request.getNote().isBlank() ? request.getNote()
                            : "Rút tiền từ mục tiêu: " + savingGoal.getName());
            txId = tx.getId();
        }

        // Giảm số tiền hiện có trong mục tiêu
        savingGoal.setCurrentAmount(savingGoal.getCurrentAmount().subtract(request.getAmount()));

        // Nếu rút xong mà dưới mục tiêu, chuyển trạng thái về IN_PROGRESS (1)
        if (savingGoal.getCurrentAmount().compareTo(savingGoal.getTargetAmount()) < 0) {
            savingGoal.setStatus(SavingGoalStatus.IN_PROGRESS);
        }

        savingGoalRepository.saveAndFlush(savingGoal);

        // Lưu bản ghi lịch sử rút tiền
        SavingGoalContribution contribution = new SavingGoalContribution();
        contribution.setSavingGoalId(savingGoal.getId());
        contribution.setAmount(request.getAmount());
        contribution.setContributionDate(
                request.getContributionDate() != null ? request.getContributionDate() : LocalDate.now());
        contribution.setType(SavingContributionType.WITHDRAW);
        contribution.setAccountId(request.getAccountId());
        contribution.setTransactionId(txId);
        contribution.setNote(request.getNote() != null && !request.getNote().isBlank() ? request.getNote()
                : "Rút tiền từ mục tiêu: " + savingGoal.getName());
        savingGoalContributionRepository.save(contribution);

        return getById(savingGoal.getId(), auth);
    }

    /**
     * 7. Lấy danh sách lịch sử đóng góp của mục tiêu
     */
    public List<SavingGoalContributionResponse> getContributions(UUID id, Auth auth) {
        User user = getUser(auth);

        savingGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

        return savingGoalContributionRepository.findAllBySavingGoalIdOrderByContributionDateDesc(id)
                .stream()
                .map(savingGoalContributionMapper::toResponse)
                .toList();
    }

    /**
     * 8. Thêm bản ghi đóng góp / rút quỹ trực tiếp
     */
    @Transactional
    public SavingGoalResponse addContribution(UUID id, SavingGoalContributionRequest request, Auth auth) {
        int type = request.getType() != null ? request.getType() : SavingContributionType.DEPOSIT;
        if (type == SavingContributionType.WITHDRAW) {
            SavingGoalWithdrawRequest withdrawReq = new SavingGoalWithdrawRequest();
            withdrawReq.setAmount(request.getAmount());
            withdrawReq.setAccountId(request.getAccountId());
            withdrawReq.setContributionDate(request.getContributionDate());
            withdrawReq.setNote(request.getNote());
            return withdraw(id, withdrawReq, auth);
        } else {
            SavingGoalDepositRequest depositReq = new SavingGoalDepositRequest();
            depositReq.setAmount(request.getAmount());
            depositReq.setAccountId(request.getAccountId());
            depositReq.setContributionDate(request.getContributionDate());
            depositReq.setNote(request.getNote());
            return deposit(id, depositReq, auth);
        }
    }

    /**
     * 9. Xóa 1 bản ghi đóng góp (hoàn tác số dư mục tiêu & tài khoản liên quan)
     */
    @Transactional
    public SavingGoalResponse deleteContribution(UUID id, UUID contributionId, Auth auth) {
        User user = getUser(auth);

        SavingGoal savingGoal = savingGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

        SavingGoalContribution contribution = savingGoalContributionRepository
                .findByIdAndSavingGoalId(contributionId, id)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi đóng góp"));

        // Hoàn tác số tiền trong SavingGoal
        if (contribution.getType() == SavingContributionType.DEPOSIT) {
            // Đã nạp -> giờ xóa nạp thì trừ đi
            if (savingGoal.getCurrentAmount().compareTo(contribution.getAmount()) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Số dư quỹ hiện tại không đủ để hoàn tác lần nạp này");
            }
            savingGoal.setCurrentAmount(savingGoal.getCurrentAmount().subtract(contribution.getAmount()));
            // Hoàn lại tiền vào tài khoản nguồn nếu có
            if (contribution.getAccountId() != null) {
                Account account = accountService.validateAccount(contribution.getAccountId(), auth, Status.ACTIVE);
                accountService.applyDelta(account, contribution.getAmount());
            }
        } else if (contribution.getType() == SavingContributionType.WITHDRAW) {
            // Đã rút -> giờ xóa rút thì cộng lại vào quỹ
            savingGoal.setCurrentAmount(savingGoal.getCurrentAmount().add(contribution.getAmount()));
            // Trừ lại tiền từ tài khoản đã nhận nếu có
            if (contribution.getAccountId() != null) {
                Account account = accountService.validateAccount(contribution.getAccountId(), auth, Status.ACTIVE);
                accountService.applyDelta(account, contribution.getAmount().negate());
            }
        }

        // Cập nhật lại status của mục tiêu
        if (savingGoal.getCurrentAmount().compareTo(savingGoal.getTargetAmount()) >= 0) {
            savingGoal.setStatus(SavingGoalStatus.COMPLETED);
        } else {
            savingGoal.setStatus(SavingGoalStatus.IN_PROGRESS);
        }
        savingGoalRepository.saveAndFlush(savingGoal);

        // Xóa transaction sao kê tương ứng nếu có
        if (contribution.getTransactionId() != null) {
            transactionRepository.deleteById(contribution.getTransactionId());
        }

        // Xóa bản ghi contribution
        savingGoalContributionRepository.delete(contribution);

        return getById(id, auth);
    }

    /**
     * 10. Xóa mục tiêu tiết kiệm (xóa toàn bộ lịch sử contribution liên quan)
     */
    @Transactional
    public boolean delete(UUID id, Auth auth) {
        User user = getUser(auth);

        SavingGoal savingGoal = savingGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mục tiêu tiết kiệm"));

        savingGoalContributionRepository.deleteAllBySavingGoalId(id);
        savingGoalRepository.delete(savingGoal);
        return true;
    }

    /**
     * Helper lưu bản ghi Transaction chuyển tiền nội bộ phục vụ sao kê tài khoản
     */
    private Transaction saveSavingTransaction(UUID userId, UUID accountId, BigDecimal amount, int type, int currency,
            String description) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(accountId);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setCategory(Category.SAVINGS);
        transaction.setCurrency(currency);
        transaction.setDescription(description);
        return transactionRepository.save(transaction);
    }

    private User getUser(Auth auth) {
        return userRepository.findByIdAndStatus(UUID.fromString(auth.getId()), Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

}
