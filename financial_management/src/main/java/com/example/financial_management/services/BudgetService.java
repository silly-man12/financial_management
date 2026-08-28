package com.example.financial_management.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.financial_management.constant.Status;
import com.example.financial_management.entity.Budget;
import com.example.financial_management.entity.Tag;
import com.example.financial_management.entity.User;
import com.example.financial_management.mapper.BudgetMapper;
import com.example.financial_management.mapper.TagMapper;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.budget.BudgetCheckingResponse;
import com.example.financial_management.model.budget.BudgetRequest;
import com.example.financial_management.model.budget.BudgetResponse;
import com.example.financial_management.repository.BudgetRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final BudgetMapper budgetMapper;
    private final TagMapper tagMapper;
    private final TagService tagService;
    private final CurrencyExchangeService currencyExchangeService;

    private BudgetResponse toEnrichedResponse(Budget budget) {
        BudgetResponse response = budgetMapper.toResponse(budget);
        if (response != null) {
            response.setAmountUsd(currencyExchangeService.toUsd(response.getAmount()));
            if (budget.getTags() != null && !budget.getTags().isEmpty()) {
                Tag firstTag = budget.getTags().iterator().next();
                response.setTagId(firstTag.getId());
                response.setTagName(firstTag.getName());
                response.setTagColor(firstTag.getColor());
                response.setTags(budget.getTags().stream().map(tagMapper::toResponse).toList());
            }
        }
        return response;
    }

    public List<BudgetResponse> getBudgets(Auth auth) {
        User user = validateUser(auth);
        List<Budget> budgets = budgetRepository.findAllWithTagsByUserId(user.getId());
        return budgets.stream().map(this::toEnrichedResponse).toList();
    }

    public List<BudgetCheckingResponse> checkingBudget(int month, int year, Auth auth) {
        User user = validateUser(auth);
        List<Budget> budgets = budgetRepository.findAllWithTagsByUserIdAndMonthAndYear(
                user.getId(),
                month,
                year);
        List<BudgetCheckingResponse> responses = new ArrayList<>();
        for (Budget budget : budgets) {
            BudgetCheckingResponse response = new BudgetCheckingResponse();
            response.setId(budget.getId().toString());
            response.setCategory(budget.getCategory());
            response.setAmount(budget.getAmount());
            response.setAmountUsd(currencyExchangeService.toUsd(budget.getAmount()));

            BigDecimal spending = BigDecimal.ZERO;
            if (budget.getTags() != null && !budget.getTags().isEmpty()) {
                Tag firstTag = budget.getTags().iterator().next();
                response.setTagId(firstTag.getId());
                response.setTagName(firstTag.getName());
                response.setTagColor(firstTag.getColor());
                response.setTags(budget.getTags().stream().map(tagMapper::toResponse).toList());

                // Tính tổng chi tiêu theo Tag trong tháng
                spending = transactionRepository.sumSpendingByTagAndMonth(
                        user.getId(), firstTag.getId(), budget.getMonth(), budget.getYear());
            } else if (budget.getTagId() != null) {
                response.setTagId(budget.getTagId());
                spending = transactionRepository.sumSpendingByTagAndMonth(
                        user.getId(), budget.getTagId(), budget.getMonth(), budget.getYear());
            } else {
                if (budget.getCategory() >= 0) {
                    spending = transactionRepository.sumSpendingByCategoryAndMonth(
                            user.getId(), budget.getCategory(), budget.getMonth(), budget.getYear());
                }
            }

            if (spending == null) {
                spending = BigDecimal.ZERO;
            }

            response.setSpending(spending);
            response.setSpendingUsd(currencyExchangeService.toUsd(spending));

            BigDecimal overSpending = budget.getAmount().subtract(spending);
            response.setOverSpending(overSpending);
            response.setOverSpendingUsd(currencyExchangeService.toUsd(overSpending));

            if (overSpending.compareTo(BigDecimal.ZERO) < 0) {
                response.setDescription("Vượt quá chi tiêu");
            } else {
                response.setDescription("Trong giới hạn");
            }

            responses.add(response);
        }
        return responses;
    }

    public BudgetResponse getBudgetById(UUID budgetId, Auth auth) {
        User user = validateUser(auth);
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));

        if (!budget.getUserId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        return toEnrichedResponse(budget);
    }

    public BudgetResponse createBudget(BudgetRequest request, Auth auth) {
        User user = validateUser(auth);
        validateBudgetRequest(request);

        Budget budget = new Budget();
        budget.setUserId(user.getId());
        budget.setCategory(request.getCategory());
        budget.setDescription(request.getDescription() != null && !request.getDescription().isBlank() ? request.getDescription() : "");
        budget.setAmount(request.getAmount());
        budget.setMonth(request.getMonth());
        budget.setYear(request.getYear());

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            Set<Tag> resolvedTags = tagService.resolveTags(request.getTags(), user.getId());
            budget.setTags(resolvedTags);
            if (!resolvedTags.isEmpty()) {
                budget.setTagId(resolvedTags.iterator().next().getId());
            }
        } else if (request.getTagId() != null) {
            budget.setTagId(request.getTagId());
        }

        Budget saved = budgetRepository.save(budget);
        BudgetResponse response = toEnrichedResponse(saved);
        log.info("Budget created: {}", response);
        return response;
    }

    public BudgetResponse updateBudget(UUID budgetId, BudgetRequest request, Auth auth) {
        User user = validateUser(auth);
        validateBudgetRequest(request);

        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));

        if (!budget.getUserId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        budget.setCategory(request.getCategory());
        budget.setDescription(request.getDescription() != null && !request.getDescription().isBlank() ? request.getDescription() : "");
        budget.setAmount(request.getAmount());
        budget.setMonth(request.getMonth());
        budget.setYear(request.getYear());

        if (request.getTags() != null) {
            Set<Tag> resolvedTags = tagService.resolveTags(request.getTags(), user.getId());
            budget.setTags(resolvedTags);
            if (!resolvedTags.isEmpty()) {
                budget.setTagId(resolvedTags.iterator().next().getId());
            } else {
                budget.setTagId(null);
            }
        } else if (request.getTagId() != null) {
            budget.setTagId(request.getTagId());
        }

        Budget updated = budgetRepository.save(budget);
        BudgetResponse response = toEnrichedResponse(updated);
        log.info("Budget updated: {}", response);
        return response;
    }

    public String deleteBudget(UUID budgetId, Auth auth) {
        User user = validateUser(auth);
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));

        if (!budget.getUserId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        budgetRepository.delete(budget);
        log.info("Budget deleted: {}", budgetId);
        return "Budget deleted successfully";
    }

    private void validateBudgetRequest(BudgetRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be a positive number");
        }
    }

    private User validateUser(Auth auth) {
        return userRepository.findByIdAndStatus(UUID.fromString(auth.getId()), Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}

