package com.example.financial_management.services;

import com.example.financial_management.config.BankWebhookProperties;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.BankWebhookEvent;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.budget.BudgetCheckingResponse;
import com.example.financial_management.repository.UserRepository;
import com.example.financial_management.telegram.TelegramBotClient;
import com.example.financial_management.telegram.TelegramCommandHandler;
import com.example.financial_management.telegram.TelegramMessageParser;
import com.example.financial_management.telegram.TelegramParsedTransaction;
import com.example.financial_management.webhook.BankWebhookProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankWebhookNotifier {

    private static final Locale VI_LOCALE = Locale.of("vi", "VN");

    private final BankWebhookProperties properties;
    private final TelegramBotClient botClient;
    private final TelegramMessageParser messageParser;
    private final TelegramCommandHandler commandHandler;
    private final UserRepository userRepository;

    /**
     * Gửi thông báo Telegram ngay sau khi giao dịch Webhook đã commit thành công vào DB
     */
    public void notifyWebhookProcessed(BankWebhookProcessedEvent event) {
        if (event == null || !properties.isNotifyTelegram()) {
            return;
        }

        User user = event.getUser();
        Long chatId = (user != null) ? user.getTelegramChatId() : null;

        // Fallback: Nếu user mặc định chưa có telegramChatId, tìm user đang hoạt động đã liên kết Telegram
        if (chatId == null) {
            User linkedUser = userRepository.findAll().stream()
                    .filter(u -> u.getStatus() == Status.ACTIVE && u.getTelegramChatId() != null)
                    .findFirst()
                    .orElse(null);
            if (linkedUser != null) {
                chatId = linkedUser.getTelegramChatId();
                if (user == null) {
                    user = linkedUser;
                }
            }
        }

        if (chatId == null) {
            log.warn("[BankWebhookNotifier] Không tìm thấy telegram_chat_id nào đã liên kết để gửi thông báo webhook.");
            return;
        }

        try {
            switch (event.getNotificationType()) {
                case NORMAL_TRANSACTION -> sendNormalTransactionMessage(chatId, user, event);
                case SKIPPED_UNMAPPED -> sendSkippedUnmappedMessage(chatId, event);
            }
        } catch (Exception e) {
            log.warn("[BankWebhookNotifier] Lỗi khi gửi thông báo Telegram: {}", e.getMessage(), e);
        }
    }

    private void sendNormalTransactionMessage(long chatId, User user, BankWebhookProcessedEvent event) {
        Transaction tx = event.getTransaction();
        Account account = event.getPrimaryAccount();
        BankWebhookEvent wh = event.getWebhookEvent();
        if (tx == null || account == null) {
            return;
        }

        boolean isIncome = tx.getType() == TransactionType.INCOME;
        String header = isIncome
                ? "🟢 <b>BIẾN ĐỘNG SỐ DƯ: THU NHẬP MỚI</b>"
                : "🔴 <b>BIẾN ĐỘNG SỐ DƯ: CHI TIÊU MỚI</b>";
        String sign = isIncome ? "+" : "-";
        String categoryName = messageParser.getCategoryVietnameseName(tx.getCategory());
        String gatewayLabel = wh != null && wh.getGateway() != null ? " (" + wh.getGateway() + ")" : "";

        String budgetStatusInfo = "";
        List<BudgetCheckingResponse> affectedBudgets = Collections.emptyList();
        if (!isIncome && user != null && user.isTelegramBudgetAlertEnabled()) {
            Auth auth = toAuth(user);
            TelegramParsedTransaction parsed = TelegramParsedTransaction.builder()
                    .amount(tx.getAmount())
                    .type(tx.getType())
                    .category(tx.getCategory())
                    .categoryName(categoryName)
                    .description(tx.getDescription())
                    .tags(Collections.emptyList())
                    .success(true)
                    .build();

            affectedBudgets = commandHandler.findAffectedBudgets(user, parsed, auth);
            if (!affectedBudgets.isEmpty()) {
                BudgetCheckingResponse primaryBudget = affectedBudgets.get(0);
                if (primaryBudget.getAmount() != null && primaryBudget.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal spending = primaryBudget.getSpending() != null ? primaryBudget.getSpending() : BigDecimal.ZERO;
                    BigDecimal remaining = primaryBudget.getAmount().subtract(spending);
                    BigDecimal pct = primaryBudget.getUsedPercentage() != null ? primaryBudget.getUsedPercentage() : BigDecimal.ZERO;
                    String budgetDisplayName = commandHandler.getBudgetDisplayName(primaryBudget);

                    if (remaining.compareTo(BigDecimal.ZERO) >= 0) {
                        budgetStatusInfo = "\n📊 Ngân sách " + budgetDisplayName + ": Còn lại <code>"
                                + formatMoney(remaining) + "</code> (chiếm "
                                + String.format(Locale.US, "%.1f%%", pct.doubleValue()) + ")";
                    } else {
                        budgetStatusInfo = "\n📊 Ngân sách " + budgetDisplayName + ": Đã vượt <code>"
                                + formatMoney(remaining.abs()) + "</code> (chiếm "
                                + String.format(Locale.US, "%.1f%%", pct.doubleValue()) + ")";
                    }
                }
            }
        }

        String discrepancyLine = "";
        if (event.getBalanceDiscrepancy() != null && event.getBalanceDiscrepancy().compareTo(BigDecimal.ZERO) != 0
                && wh != null && wh.getBankBalance() != null) {
            String diffSign = event.getBalanceDiscrepancy().compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
            discrepancyLine = "\n⚠️ <i>Số dư báo về (" + formatMoney(wh.getBankBalance())
                    + ") lệch <code>" + diffSign + formatMoney(event.getBalanceDiscrepancy()) + "</code> so với sổ sách.</i>";
        }

        String msg = header + gatewayLabel + "\n\n"
                + "💵 Số tiền: <b>" + sign + formatMoney(tx.getAmount()) + "</b>\n"
                + "🏷️ Danh mục: <b>" + categoryName + "</b>\n"
                + "📝 Nội dung: <i>" + escapeHtml(tx.getDescription()) + "</i>\n"
                + "🏦 Ví: <b>" + escapeHtml(account.getName()) + "</b>\n"
                + "💰 Số dư ví: <code>" + formatMoney(account.getBalance()) + "</code>"
                + budgetStatusInfo
                + discrepancyLine
                + "\n\n<i>Gõ /huy nếu bạn muốn hoàn tác giao dịch này.</i>";

        boolean sent = botClient.sendMessage(chatId, msg);
        log.info("[BankWebhookNotifier] Sent normal transaction Telegram message to chatId={}: success={}", chatId, sent);

        if (!isIncome && user != null && user.isTelegramBudgetAlertEnabled() && !affectedBudgets.isEmpty()) {
            commandHandler.sendBudgetAlertsIfNeeded(chatId, user, affectedBudgets);
        }
    }

    private void sendSkippedUnmappedMessage(long chatId, BankWebhookProcessedEvent event) {
        BankWebhookEvent wh = event.getWebhookEvent();
        if (wh == null) {
            return;
        }
        String msg = "⚠️ <b>NHẬN ĐƯỢC WEBHOOK NHƯNG CHƯA ÁNH XẠ VÍ</b>\n\n"
                + "• Nguồn: <code>" + escapeHtml(wh.getProvider()) + "</code> (" + escapeHtml(wh.getGateway()) + ")\n"
                + "• Mã nguồn (SourceKey): <code>" + escapeHtml(wh.getSourceKey()) + "</code>\n"
                + "• Số tiền: <b>" + formatMoney(wh.getAmount()) + "</b>\n"
                + "• Nội dung: <i>" + escapeHtml(wh.getContent()) + "</i>\n\n"
                + "👉 Vui lòng tạo ví tương ứng hoặc cấu hình ánh xạ nguồn qua API <code>/webhooks/bank/mappings</code>.";
        botClient.sendMessage(chatId, msg);
    }

    private Auth toAuth(User user) {
        Auth auth = new Auth();
        auth.setId(user.getId().toString());
        auth.setName(user.getName());
        auth.setEmail(user.getEmail());
        auth.setRole(user.getRole());
        auth.setStatus(user.getStatus());
        return auth;
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0 đ";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(VI_LOCALE);
        nf.setMaximumFractionDigits(0);
        return nf.format(amount) + " đ";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
