package com.example.financial_management.services;

import com.example.financial_management.constant.Category;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.BankCategoryRule;
import com.example.financial_management.repository.BankCategoryRuleRepository;
import com.example.financial_management.telegram.TelegramMessageParser;
import com.example.financial_management.webhook.BankTransactionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankTransactionClassifier {

    private final BankCategoryRuleRepository ruleRepository;
    private final TelegramMessageParser telegramMessageParser;

    public int classify(UUID userId, BankTransactionEvent event, int transactionType) {
        String counterparty = event.counterparty() != null ? event.counterparty() : "";
        String content = event.content() != null ? event.content() : "";
        String combined = (counterparty + " " + content).trim();

        return classifyText(userId, combined, event.direction() != null ? event.direction().name() : null, transactionType);
    }

    public int classifyText(UUID userId, String rawText, String direction, int transactionType) {
        String safeText = rawText != null ? rawText.trim() : "";
        String normalizedText = telegramMessageParser.removeDiacritics(safeText).toLowerCase(Locale.ROOT);
        String lowerRaw = safeText.toLowerCase(Locale.ROOT);

        // 1. Ưu tiên kiểm tra quy tắc tự định nghĩa (BankCategoryRule) của người dùng
        if (userId != null && !safeText.isEmpty()) {
            List<BankCategoryRule> rules = ruleRepository
                    .findAllByUserIdAndActiveTrueOrderByPriorityDescCreatedAtAsc(userId);
            for (BankCategoryRule rule : rules) {
                if (rule.getDirection() != null && !rule.getDirection().isBlank() && direction != null) {
                    if (!rule.getDirection().trim().equalsIgnoreCase(direction)) {
                        continue;
                    }
                }
                String kwRaw = rule.getKeyword() != null ? rule.getKeyword().trim().toLowerCase(Locale.ROOT) : "";
                String kwNorm = telegramMessageParser.removeDiacritics(kwRaw).toLowerCase(Locale.ROOT);
                if (!kwRaw.isEmpty() && (lowerRaw.contains(kwRaw) || normalizedText.contains(kwNorm))) {
                    int ruleCat = rule.getCategory();
                    if (isCompatibleCategory(ruleCat, transactionType)) {
                        return ruleCat;
                    }
                }
            }
        }

        // 2. Tái sử dụng bộ phân loại NLP tiếng Việt của TelegramMessageParser
        if (!normalizedText.isEmpty()) {
            int detected = telegramMessageParser.detectCategory(normalizedText, transactionType);
            if (isCompatibleCategory(detected, transactionType)) {
                return detected;
            }
            // Nếu giao dịch CHI TIÊU (EXPENSE) mà có từ khóa tặng/biếu/lì xì bị rơi vào GIFTS (204) -> đổi sang PRESENT (111)
            if (transactionType == TransactionType.EXPENSE && detected == Category.GIFTS) {
                return Category.PRESENT;
            }
        }

        // 3. Fallback mặc định theo đúng chiều giao dịch
        return transactionType == TransactionType.INCOME
                ? Category.OTHER_INCOME
                : Category.OTHER_EXPENSE;
    }

    private boolean isCompatibleCategory(int categoryId, int transactionType) {
        if (transactionType == TransactionType.EXPENSE) {
            return Category.isExpense(categoryId);
        }
        if (transactionType == TransactionType.INCOME) {
            return Category.isIncome(categoryId);
        }
        if (transactionType == TransactionType.TRANSFER) {
            return categoryId == Category.TRANSFER;
        }
        return false;
    }
}
