package com.example.financial_management.telegram;

import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.budget.BudgetCheckingResponse;
import com.example.financial_management.model.budget.BudgetSummaryResponse;
import com.example.financial_management.model.telegram.TelegramLinkCodeResponse;
import com.example.financial_management.model.telegram.TelegramSettingsRequest;
import com.example.financial_management.model.telegram.TelegramStatusResponse;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;
import com.example.financial_management.services.BudgetService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramService {

    private final TelegramBotConfig config;
    private final TelegramBotClient botClient;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;

    private final SecureRandom random = new SecureRandom();
    private static final Locale VI_LOCALE = Locale.of("vi", "VN");
    private static final int CODE_EXPIRATION_MINUTES = 10;
    private static final String DEFAULT_REPORT_TIME = "21:30";

    @Data
    @AllArgsConstructor
    public static class LinkCodeEntry {
        private UUID userId;
        private String code;
        private LocalDateTime expiredAt;
    }

    // code -> entry
    private final Map<String, LinkCodeEntry> codeMap = new ConcurrentHashMap<>();
    // userId -> code
    private final Map<UUID, String> userCodeMap = new ConcurrentHashMap<>();

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        NumberFormat nf = NumberFormat.getNumberInstance(VI_LOCALE);
        return nf.format(amount) + " ₫";
    }

    /**
     * Lấy trạng thái kết nối Telegram của người dùng
     */
    public TelegramStatusResponse getStatus(UUID userId) {
        User user = userRepository.findByIdAndStatus(userId, Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin người dùng hợp lệ"));

        return getStatus(user);
    }

    public TelegramStatusResponse getStatus(User user) {
        return TelegramStatusResponse.builder()
                .isLinked(user.getTelegramChatId() != null)
                .telegramChatId(user.getTelegramChatId())
                .botUsername(config.getUsername())
                .dailyReportEnabled(user.isTelegramDailyReportEnabled())
                .budgetAlertEnabled(user.isTelegramBudgetAlertEnabled())
                .reportTime(DEFAULT_REPORT_TIME)
                .build();
    }

    /**
     * Tạo mã OTP 6 số kết nối an toàn (hết hạn trong 10 phút)
     */
    public TelegramLinkCodeResponse generateLinkCode(UUID userId) {
        userRepository.findByIdAndStatus(userId, Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng hợp lệ để tạo mã liên kết"));

        cleanExpiredEntries();

        // Xóa mã cũ của user nếu có
        String oldCode = userCodeMap.remove(userId);
        if (oldCode != null) {
            codeMap.remove(oldCode);
        }

        // Tạo mã 6 số ngẫu nhiên không trùng lặp
        String code;
        do {
            code = String.format("%06d", random.nextInt(1_000_000));
        } while (codeMap.containsKey(code));

        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(CODE_EXPIRATION_MINUTES);

        LinkCodeEntry entry = new LinkCodeEntry(userId, code, expiredAt);
        codeMap.put(code, entry);
        userCodeMap.put(userId, code);

        String botUsername = config.getUsername() != null ? config.getUsername().trim() : "";
        if (botUsername.startsWith("@")) {
            botUsername = botUsername.substring(1);
        }

        String deepLink = "https://t.me/" + botUsername + "?start=LINK_" + code;

        log.info("[TelegramLink] Generated link code for userId: {} (expires at {})", userId, expiredAt);

        return TelegramLinkCodeResponse.builder()
                .code(code)
                .expiredAt(expiredAt)
                .deepLink(deepLink)
                .build();
    }

    /**
     * Xác thực mã OTP và liên kết Chat ID Telegram với tài khoản User
     */
    @Transactional
    public Optional<User> linkUserByCode(String rawCode, long chatId) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }

        String cleaned = rawCode.trim();
        if (cleaned.toUpperCase().startsWith("LINK_")) {
            cleaned = cleaned.substring(5).trim();
        } else if (cleaned.toUpperCase().startsWith("LINK")) {
            cleaned = cleaned.substring(4).trim();
        }

        cleanExpiredEntries();

        LinkCodeEntry entry = codeMap.get(cleaned);
        if (entry == null) {
            log.warn("[TelegramLink] Link code not found: {}", cleaned);
            return Optional.empty();
        }

        if (LocalDateTime.now().isAfter(entry.getExpiredAt())) {
            log.warn("[TelegramLink] Link code expired: {}", cleaned);
            codeMap.remove(cleaned);
            userCodeMap.remove(entry.getUserId());
            return Optional.empty();
        }

        // Mã hợp lệ: xóa mã khỏi cache
        codeMap.remove(cleaned);
        userCodeMap.remove(entry.getUserId());

        Optional<User> userOpt = userRepository.findByIdAndStatus(entry.getUserId(), Status.ACTIVE);
        if (userOpt.isEmpty()) {
            log.warn("[TelegramLink] User {} not found or inactive", entry.getUserId());
            return Optional.empty();
        }

        User user = userOpt.get();

        // Nếu chatId này đang thuộc về user khác, gỡ liên kết khỏi user cũ
        userRepository.findByTelegramChatId(chatId).ifPresent(otherUser -> {
            if (!otherUser.getId().equals(user.getId())) {
                otherUser.setTelegramChatId(null);
                userRepository.save(otherUser);
                log.info("[TelegramLink] Unlinked previous user {} from chatId {}", otherUser.getEmail(), chatId);
            }
        });

        user.setTelegramChatId(chatId);
        userRepository.save(user);

        log.info("[TelegramLink] Successfully linked user {} to telegram chatId {}", user.getEmail(), chatId);
        return Optional.of(user);
    }

    /**
     * Hủy liên kết Telegram khỏi tài khoản User
     */
    @Transactional
    public void unlinkUser(UUID userId) {
        User user = userRepository.findByIdAndStatus(userId, Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng hợp lệ"));

        user.setTelegramChatId(null);
        userRepository.save(user);

        // Hủy luôn mã liên kết đang chờ nếu có
        String pendingCode = userCodeMap.remove(userId);
        if (pendingCode != null) {
            codeMap.remove(pendingCode);
        }

        log.info("[TelegramLink] User {} successfully unlinked from Telegram", user.getEmail());
    }

    /**
     * Cập nhật cài đặt nhận thông báo Telegram
     */
    @Transactional
    public TelegramStatusResponse updateSettings(UUID userId, TelegramSettingsRequest request) {
        User user = userRepository.findByIdAndStatus(userId, Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng hợp lệ"));

        if (request.getDailyReportEnabled() != null) {
            user.setTelegramDailyReportEnabled(request.getDailyReportEnabled());
        }
        if (request.getBudgetAlertEnabled() != null) {
            user.setTelegramBudgetAlertEnabled(request.getBudgetAlertEnabled());
        }

        userRepository.save(user);
        log.info("[TelegramSettings] Updated settings for user {}: dailyReport={}, budgetAlert={}",
                user.getEmail(), user.isTelegramDailyReportEnabled(), user.isTelegramBudgetAlertEnabled());

        return getStatus(user);
    }

    /**
     * Gửi tin nhắn tổng kết chi tiêu thử nghiệm về Telegram của user
     */
    public void sendTestDailySummary(Auth auth) {
        User user = userRepository.findByIdAndStatus(auth.getUUID(), Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin người dùng"));

        if (user.getTelegramChatId() == null) {
            throw new IllegalArgumentException("Tài khoản chưa được liên kết với Telegram Bot. Vui lòng liên kết trước!");
        }

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        List<Transaction> transactions = transactionRepository.findAllByUserIdAndCreatedAtBetween(user.getId(), startOfDay, endOfDay);

        BigDecimal totalExpense = BigDecimal.ZERO;
        int expenseCount = 0;
        for (Transaction t : transactions) {
            if (t.getType() == TransactionType.EXPENSE) {
                BigDecimal amt = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
                totalExpense = totalExpense.add(amt);
                expenseCount++;
            }
        }

        String budgetRemainingStr = "Chưa thiết lập";
        try {
            BudgetSummaryResponse summary = budgetService.getBudgetSummary(today.getMonthValue(), today.getYear(), auth);
            if (summary != null && summary.getRemainingAmount() != null) {
                budgetRemainingStr = formatMoney(summary.getRemainingAmount());
            }
        } catch (Exception e) {
            log.warn("[TelegramTest] Could not fetch budget summary: {}", e.getMessage());
        }

        String expenseDisplay = expenseCount > 0
                ? "<b>" + formatMoney(totalExpense) + "</b> (" + expenseCount + " giao dịch)"
                : "<b>0 ₫</b> (Hôm nay chưa có chi tiêu)";

        String dateStr = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String message =
                "🌙 <b>TỔNG KẾT CHI TIÊU HÔM NAY (" + dateStr + ") [THỬ NGHIỆM]</b>\n\n" +
                "💸 Hôm nay bạn đã chi: " + expenseDisplay + "\n" +
                "📊 Ngân sách tháng còn lại: <b>" + budgetRemainingStr + "</b>\n\n" +
                "<i>Chúc bạn một buổi tối thư thái và an lành! ✨</i>";

        boolean sent = botClient.sendMessage(user.getTelegramChatId(), message);
        if (!sent) {
            throw new IllegalStateException("Không thể gửi tin nhắn đến Telegram. Vui lòng kiểm tra lại bot và kết nối.");
        }
    }

    /**
     * Gửi tin nhắn cảnh báo ngân sách thử nghiệm về Telegram của user
     */
    public void sendTestBudgetAlert(Auth auth) {
        User user = userRepository.findByIdAndStatus(auth.getUUID(), Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin người dùng"));

        if (user.getTelegramChatId() == null) {
            throw new IllegalArgumentException("Tài khoản chưa được liên kết với Telegram Bot. Vui lòng liên kết trước!");
        }

        String categoryName = "Ăn uống 🍜";
        String usedPercentageStr = "92.5%";
        String spentStr = "4.625.000 ₫";
        String limitStr = "5.000.000 ₫";

        try {
            LocalDate today = LocalDate.now();
            List<BudgetCheckingResponse> list = budgetService.checkingBudget(today.getMonthValue(), today.getYear(), auth);
            if (list != null && !list.isEmpty()) {
                BudgetCheckingResponse highest = list.stream()
                        .filter(b -> b.getUsedPercentage() != null)
                        .max(Comparator.comparing(BudgetCheckingResponse::getUsedPercentage))
                        .orElse(null);

                if (highest != null) {
                    categoryName = highest.getDescription() != null ? highest.getDescription() : "Danh mục #" + highest.getCategory();
                    usedPercentageStr = String.format("%.1f%%", highest.getUsedPercentage().doubleValue());
                    spentStr = formatMoney(highest.getSpending());
                    limitStr = formatMoney(highest.getAmount());
                }
            }
        } catch (Exception e) {
            log.warn("[TelegramTest] Could not fetch budget checking details: {}", e.getMessage());
        }

        String message =
                "🚨 <b>CẢNH BÁO VƯỢT HẠN MỨC NGÂN SÁCH! [THỬ NGHIỆM]</b>\n\n" +
                "Danh mục: <b>" + categoryName + "</b>\n" +
                "Tiến độ hiện tại: <b>" + usedPercentageStr + "</b> (Đã chi " + spentStr + " / Hạn mức " + limitStr + ")\n" +
                "⚠️ Bạn sắp chạm trần ngân sách cho tháng này!";

        boolean sent = botClient.sendMessage(user.getTelegramChatId(), message);
        if (!sent) {
            throw new IllegalStateException("Không thể gửi tin nhắn đến Telegram. Vui lòng kiểm tra lại bot và kết nối.");
        }
    }

    private void cleanExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();
        codeMap.entrySet().removeIf(entry -> {
            boolean expired = now.isAfter(entry.getValue().getExpiredAt());
            if (expired) {
                userCodeMap.remove(entry.getValue().getUserId());
            }
            return expired;
        });
    }
}
