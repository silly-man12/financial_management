package com.example.financial_management.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.mapper.AccountMapper;
import com.example.financial_management.model.account.AccountRequest;
import com.example.financial_management.model.account.AccountResponse;
import com.example.financial_management.model.account.AccountStatus;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.transaction.TransactionRequest;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountMapper accountMapper;
    private final TransactionRepository transactionRepository;

    @Transactional
    public AccountResponse createAccount(AccountRequest request, Auth auth) {
        User user = validateUser(auth);

        Account account = accountMapper.toEntity(request, user.getId());

        // đảm bảo initialBalance không null
        BigDecimal initial = request.getInitialBalance() != null
                ? request.getInitialBalance()
                : BigDecimal.ZERO;

        account.setBalance(initial);

        Account saved = accountRepository.save(account);

        return accountMapper.toResponse(saved);
    }

    @Transactional
    public AccountResponse updateAccount(UUID accountId, AccountRequest request, Auth auth) {
        Account account = validateAccount(accountId, auth, Status.ACTIVE);

        // Nếu user muốn đổi currency
        if (account.getCurrency() != request.getCurrency()) {
            boolean existsMismatch = transactionRepository.existsByAccountIdAndCurrencyNot(
                    accountId, request.getCurrency());

            if (existsMismatch) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot change currency because account already has transaction with another currency.");
            }
            account.setCurrency(request.getCurrency());
        }

        // Cập nhật các field từ request
        account.setName(request.getName());
        account.setType(request.getType());
        account.setDescription(request.getDescription());
        // account.setBalance(request.getInitialBalance());

        Account saved = accountRepository.saveAndFlush(account);

        return accountMapper.toResponse(saved);
    }

    @Transactional
    public AccountResponse updateStatusAccount(AccountStatus accountStatus, Auth auth) {
        User user = validateUser(auth);
        Account account = accountRepository.findByIdAndUserId(accountStatus.getId(), user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        // Chỉ cho phép chuyển trạng thái ACTIVE hoặc INACTIVE
        if (accountStatus.getStatus() != Status.ACTIVE && accountStatus.getStatus() != Status.INACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value");
        }

        account.setStatus(accountStatus.getStatus());
        Account saved = accountRepository.saveAndFlush(account);

        return accountMapper.toResponse(saved);
    }

    @Transactional
    public boolean removeAccount(UUID accountId, Auth auth) {
        User user = validateUser(auth);

        Account account = accountRepository.findByIdAndUserId(accountId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        accountRepository.delete(account);
        return true;
    }

    @Transactional
    public void updateBalanceForTransactionUpdate(
            Account oldAccount,
            Account newAccount,
            Transaction oldTransaction,
            TransactionRequest newTransaction) {

        // Không đổi tài khoản
        if (oldAccount.getId().equals(newAccount.getId())) {
            BigDecimal delta = calculateFinalDelta(oldTransaction, newTransaction);
            applyDelta(newAccount, delta);
            return;
        }

        // Hoàn tác giao dịch cũ trên tài khoản cũ
        BigDecimal oldBalance = oldAccount.getBalance();

        switch (oldTransaction.getType()) {
            case TransactionType.INCOME:
                oldBalance = oldBalance.subtract(oldTransaction.getAmount());
                break;

            case TransactionType.EXPENSE:
            case TransactionType.TRANSFER:
                oldBalance = oldBalance.add(oldTransaction.getAmount());
                break;
        }

        if (oldBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }

        oldAccount.setBalance(oldBalance);

        // Áp dụng giao dịch mới lên tài khoản mới
        BigDecimal newBalance = newAccount.getBalance();

        switch (newTransaction.getType()) {
            case TransactionType.INCOME:
                newBalance = newBalance.add(newTransaction.getAmount());
                break;

            case TransactionType.EXPENSE:
            case TransactionType.TRANSFER:
                newBalance = newBalance.subtract(newTransaction.getAmount());
                break;
        }

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }

        newAccount.setBalance(newBalance);

        accountRepository.save(oldAccount);
        accountRepository.save(newAccount);
    }

    public AccountResponse getAccountById(UUID accountId, Auth auth) {
        Account account = validateAccount(accountId, auth, Status.ACTIVE);

        return accountMapper.toResponse(account);
    }

    public List<AccountResponse> getAllAccounts(Auth auth) {
        User user = validateUser(auth);
        List<Account> accounts = accountRepository.findAllByUserIdAndStatus(user.getId(), Status.ACTIVE);
        return accounts.stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    private User validateUser(Auth auth) {
        return userRepository.findByIdAndStatus(UUID.fromString(auth.getId()), Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public Account validateAccount(UUID accountId, Auth auth, int status) {
        User user = validateUser(auth);

        return accountRepository.findByIdAndUserIdAndStatus(accountId, user.getId(), status)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found or status mismatch"));
    }

    public BigDecimal calculateDelta(TransactionRequest request) {
        BigDecimal amount = request.getAmount();

        if (request.getType() == TransactionType.INCOME) {
            return amount; // dương
        } else {
            return amount.negate(); // âm
        }
    }

    public void applyDelta(Account account, BigDecimal delta) {
        BigDecimal newBalance = account.getBalance().add(delta);

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }

        account.setBalance(newBalance);
        accountRepository.saveAndFlush(account);
    }

    public BigDecimal calculateFinalDelta(Transaction transaction, TransactionRequest request) {
        // Delta cũ (dựa trên transaction hiện tại trong DB)
        BigDecimal oldDelta = transaction.getType() == TransactionType.INCOME
                ? transaction.getAmount()
                : transaction.getAmount().negate();

        // Delta mới (dựa trên request update)
        BigDecimal newDelta = calculateDelta(request);

        // rollback oldDelta + apply newDelta
        return newDelta.subtract(oldDelta);
    }

}