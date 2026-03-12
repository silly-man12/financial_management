package com.example.financial_management.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.financial_management.constant.Status;
import com.example.financial_management.entity.Budget;
import com.example.financial_management.entity.User;
import com.example.financial_management.mapper.BudgetMapper;
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

    public List<BudgetResponse> getBudgets(Auth auth) {
        User user = validateUser(auth);
        List<Budget> budgets = budgetRepository.findAllByUserId(user.getId());
        return budgets.stream().map(budgetMapper::toResponse).toList();
    }

    public List<BudgetCheckingResponse> checkingBudget(Auth auth) {
        User user = validateUser(auth);
        List<Budget> budgets = budgetRepository.findAllByUserId(user.getId());
        List<BudgetCheckingResponse> responses = new ArrayList<>();
        for (Budget budget : budgets) {
            if (budget.getCategory() < 0 && budget.getCategory() > 10) {
                log.warn("Invalid category {} for budget {}", budget.getCategory(), budget.getId());
                continue;
            }
            BudgetCheckingResponse response = new BudgetCheckingResponse();
            response.setId(budget.getId().toString());
            response.setCategory(budget.getCategory());
            response.setAmount(budget.getAmount());

            BigDecimal spending = transactionRepository.sumSpendingByCategoryAndMonth(
                    user.getId(), budget.getCategory(), budget.getMonth(), budget.getYear());

            response.setSpending(spending);

            BigDecimal overSpending = budget.getAmount().subtract(spending);
            response.setOverSpending(overSpending);

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

        return budgetMapper.toResponse(budget);
    }

    public BudgetResponse createBudget(BudgetRequest request, Auth auth) {
        User user = validateUser(auth);
        validateBudgetRequest(request);

        Budget budget = new Budget();
        budget.setUserId(user.getId());
        budget.setCategory(request.getCategory());
        budget.setDescription(request.getDescription().isBlank() ? "" : request.getDescription());
        budget.setAmount(request.getAmount());
        budget.setMonth(request.getMonth());
        budget.setYear(request.getYear());

        Budget saved = budgetRepository.save(budget);
        BudgetResponse response = budgetMapper.toResponse(saved);
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
        budget.setDescription(request.getDescription().isBlank() ? "" : request.getDescription());
        budget.setAmount(request.getAmount());
        budget.setMonth(request.getMonth());
        budget.setYear(request.getYear());

        Budget updated = budgetRepository.save(budget);
        BudgetResponse response = budgetMapper.toResponse(updated);
        log.info("Budget updated: {}", response);
        return response;
    }

    private void validateBudgetRequest(BudgetRequest request) {
        if (request.getCategory() < 0) {
            throw new IllegalArgumentException("Category must be a positive integer");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be a positive number");
        }
    }

    private User validateUser(Auth auth) {
        return userRepository.findByIdAndStatus(UUID.fromString(auth.getId()), Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
