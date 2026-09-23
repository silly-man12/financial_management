package com.example.financial_management.telegram;

import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.transaction.TransactionRequest;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;
import com.example.financial_management.services.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramCommandHandler {

    private final TelegramBotConfig config;
    private final TelegramBotClient botClient;
    private final TelegramMessageParser messageParser;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    private static final Locale VI_LOCALE = Locale.of("vi", "VN");

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        NumberFormat nf = NumberFormat.getNumberInstance(VI_LOCALE);
        return nf.format(amount) + " ₫";
    }

    /**
     * Xử lý một tin nhắn gửi đến bot Telegram
     */
    @Transactional
    public void handleIncomingMessage(long chatId, String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return;
        }

        String trimmed = messageText.trim();

        // 1. Kiểm tra lệnh /start
        if (trimmed.equalsIgnoreCase("/start")) {
            handleStartCommand(chatId);
            return;
        }

        // 2. Kiểm tra lệnh /link <email> <password>
        if (trimmed.toLowerCase().startsWith("/link")) {
            handleLinkCommand(chatId, trimmed);
            return;
        }

        // 3. Tìm user theo Chat ID
        Optional<User> userOpt = userRepository.findByTelegramChatIdAndStatus(chatId, Status.ACTIVE);

        // Nếu chưa liên kết, thử xem có cấu hình email mặc định không
        if (userOpt.isEmpty() && config.getDefaultUserEmail() != null && !config.getDefaultUserEmail().trim().isEmpty()) {
            Optional<User> defaultUserOpt = userRepository.findByEmailAndStatus(config.getDefaultUserEmail().trim(), Status.ACTIVE);
            if (defaultUserOpt.isPresent()) {
                User defaultUser = defaultUserOpt.get();
                // Tự động gán nếu user mặc định chưa liên kết chat nào
                if (defaultUser.getTelegramChatId() == null) {
                    defaultUser.setTelegramChatId(chatId);
                    userRepository.save(defaultUser);
                    userOpt = Optional.of(defaultUser);
                    botClient.sendMessage(chatId, "🔗 <i>Đã tự động liên kết Bot với tài khoản chủ sở hữu: <b>" + defaultUser.getEmail() + "</b></i>");
                }
            }
        }

        if (userOpt.isEmpty()) {
            botClient.sendMessage(chatId,
                    "⚠️ <b>Bạn chưa liên kết tài khoản!</b>\n\n" +
                    "Để sử dụng bot ghi chép tài chính, vui lòng nhập lệnh:\n" +
                    "<code>/link &lt;email&gt; &lt;mật_khẩu&gt;</code>\n\n" +
                    "<i>Ví dụ: <code>/link nghyqn1201@gmail.com 123456</code></i>");
            return;
        }

        User user = userOpt.get();

        // 4. Xử lý các lệnh tra cứu
        String lowerCmd = trimmed.toLowerCase();

        if (lowerCmd.equals("/help") || lowerCmd.equals("/huongdan")) {
            handleHelpCommand(chatId);
            return;
        }

        if (lowerCmd.equals("/unlink")) {
            handleUnlinkCommand(chatId, user);
            return;
        }

        if (lowerCmd.equals("/sodu") || lowerCmd.equals("/balance")) {
            handleBalanceCommand(chatId, user);
            return;
        }

        if (lowerCmd.equals("/homnay") || lowerCmd.equals("/today")) {
            handleTodayCommand(chatId, user);
            return;
        }

        if (lowerCmd.equals("/thangnay") || lowerCmd.equals("/month")) {
            handleMonthCommand(chatId, user);
            return;
        }

        if (lowerCmd.equals("/vi") || lowerCmd.equals("/wallets")) {
            handleWalletsCommand(chatId, user);
            return;
        }

        if (lowerCmd.equals("/huy") || lowerCmd.equals("/undo")) {
            handleUndoCommand(chatId, user);
            return;
        }

        // 5. Nếu không phải lệnh bắt đầu bằng /, xử lý ghi chép giao dịch tự nhiên
        handleCreateTransaction(chatId, user, trimmed);
    }

    private void handleStartCommand(long chatId) {
        String msg =
                "👋 <b>Chào mừng bạn đến với Bot Quản Lý Tài Chính Cá Nhân!</b>\n\n" +
                "⚡ <b>Ghi chép chi tiêu siêu tốc tùy ý:</b>\n" +
                "• <code>32500 banh mi</code> (Số tiền tùy ý, không cần đơn vị)\n" +
                "• <code>127.500 sieu thi</code> (Có dấu chấm phân cách)\n" +
                "• <code>45.5k cafe</code> hoặc <code>50k cf</code>\n" +
                "• <code>35k com trua vcb</code> (Tự lưu ví VCB)\n" +
                "• <code>+15tr luong</code> (Ghi nhận thu nhập)\n" +
                "• <code>1.25tr tien nha</code>\n\n" +
                "📊 <b>Các lệnh tra cứu nhanh:</b>\n" +
                "• /sodu - Xem số dư các ví & tổng tài sản\n" +
                "• /homnay - Xem tổng chi hôm nay\n" +
                "• /thangnay - Báo cáo thu chi tháng này\n" +
                "• /vi - Danh sách các ví tài khoản\n" +
                "• /huy - Hoàn tác giao dịch vừa thêm\n" +
                "• /help - Hướng dẫn chi tiết";

        botClient.sendMessage(chatId, msg);
    }

    private void handleHelpCommand(long chatId) {
        String msg =
                "📖 <b>HƯỚNG DẪN CÚ PHÁP NHẬP LIỆU</b>\n\n" +
                "💡 <b>Quy tắc nhập số tiền cực kỳ linh hoạt:</b>\n" +
                "1. <b>Số tiền tùy ý bất kỳ:</b> <code>32500</code>, <code>127500</code>, <code>1500000</code>...\n" +
                "2. <b>Dấu chấm/phẩy hàng nghìn:</b> <code>32.500</code>, <code>127,500</code>, <code>1.250.000</code>...\n" +
                "3. <b>Viết tắt hàng nghìn:</b> <code>45k</code>, <code>45.5k</code>, <code>18k</code>, <code>33 nghin</code>...\n" +
                "4. <b>Viết tắt hàng triệu:</b> <code>1.5tr</code>, <code>12.3tr</code>, <code>2m</code>, <code>2 trieu</code>...\n" +
                "5. <b>Vị trí tự do:</b> Số tiền có thể ở đầu (<code>50k cf</code>) hoặc ở cuối (<code>cf 50k</code>).\n\n" +
                "📌 <b>Chỉ định ví thanh toán:</b>\n" +
                "Thêm tên ví vào tin nhắn (ví dụ: <code>vcb</code>, <code>momo</code>, <code>mb</code>, <code>tien mat</code>...).\n" +
                "<i>Nếu không ghi ví, bot tự trừ vào ví tiền mặt hoặc ví hoạt động đầu tiên.</i>\n\n" +
                "💰 <b>Ghi nhận thu nhập:</b>\n" +
                "Thêm dấu <code>+</code> ở đầu hoặc ghi các từ như <code>thu</code>, <code>luong</code>, <code>thuong</code> (ví dụ: <code>+15tr luong</code>, <code>thu 200k ban sach</code>).\n\n" +
                "🔄 <b>Lỡ gõ nhầm?</b>\n" +
                "Chỉ cần gõ <code>/huy</code> để xóa giao dịch vừa tạo và phục hồi số dư ví ngay lập tức.";

        botClient.sendMessage(chatId, msg);
    }

    private void handleLinkCommand(long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 3) {
            botClient.sendMessage(chatId, "⚠️ Cú pháp: <code>/link &lt;email&gt; &lt;mật_khẩu&gt;</code>");
            return;
        }

        String email = parts[1].trim();
        String password = parts[2].trim();

        Optional<User> userOpt = userRepository.findByEmailAndStatus(email, Status.ACTIVE);
        if (userOpt.isEmpty()) {
            botClient.sendMessage(chatId, "❌ Không tìm thấy tài khoản với email: <b>" + email + "</b>");
            return;
        }

        User user = userOpt.get();
        String hashed = BCrypt.hashpw(password, user.getPasswordSalt());
        if (!hashed.equals(user.getPasswordHash())) {
            botClient.sendMessage(chatId, "❌ Mật khẩu không chính xác. Vui lòng thử lại!");
            return;
        }

        user.setTelegramChatId(chatId);
        userRepository.save(user);

        botClient.sendMessage(chatId,
                "✅ <b>Liên kết tài khoản thành công!</b>\n\n" +
                "👤 Người dùng: <b>" + user.getName() + "</b>\n" +
                "📧 Email: <b>" + user.getEmail() + "</b>\n\n" +
                "Bây giờ bạn có thể gõ ngay một khoản chi (ví dụ: <code>50k cafe</code> hoặc <code>32500 banh mi</code>) để bắt đầu!");
    }

    private void handleUnlinkCommand(long chatId, User user) {
        user.setTelegramChatId(null);
        userRepository.save(user);
        botClient.sendMessage(chatId, "👋 <i>Đã hủy liên kết Telegram với tài khoản <b>" + user.getEmail() + "</b>.</i>");
    }

    private void handleBalanceCommand(long chatId, User user) {
        List<Account> accounts = accountRepository.findAllByUserIdAndStatus(user.getId(), Status.ACTIVE);
        if (accounts.isEmpty()) {
            botClient.sendMessage(chatId, "ℹ️ Bạn chưa có ví/tài khoản hoạt động nào.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🏦 <b>SỐ DƯ CÁC VÍ & TÀI KHOẢN</b>\n\n");

        BigDecimal total = BigDecimal.ZERO;
        for (Account acc : accounts) {
            BigDecimal bal = acc.getBalance() != null ? acc.getBalance() : BigDecimal.ZERO;
            total = total.add(bal);
            sb.append("• <b>").append(acc.getName()).append("</b>: ")
              .append(formatMoney(bal)).append("\n");
        }

        sb.append("\n━━━━━━━━━━━━━━━\n");
        sb.append("💎 <b>Tổng tài sản:</b> <code>").append(formatMoney(total)).append("</code>");

        botClient.sendMessage(chatId, sb.toString());
    }

    private void handleTodayCommand(long chatId, User user) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        List<Transaction> transactions = transactionRepository.findAllByUserIdAndCreatedAtBetween(user.getId(), startOfDay, endOfDay);

        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;

        StringBuilder listSb = new StringBuilder();

        for (Transaction t : transactions) {
            BigDecimal amt = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
            if (t.getType() == TransactionType.EXPENSE) {
                totalExpense = totalExpense.add(amt);
                listSb.append("🔴 -").append(formatMoney(amt)).append(" | ").append(t.getDescription()).append("\n");
            } else if (t.getType() == TransactionType.INCOME) {
                totalIncome = totalIncome.add(amt);
                listSb.append("🟢 +").append(formatMoney(amt)).append(" | ").append(t.getDescription()).append("\n");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📅 <b>TỔNG KẾT HÔM NAY (").append(today).append(")</b>\n\n");
        sb.append("💸 Tổng chi tiêu: <b>").append(formatMoney(totalExpense)).append("</b>\n");
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("💰 Tổng thu nhập: <b>").append(formatMoney(totalIncome)).append("</b>\n");
        }

        if (!transactions.isEmpty()) {
            sb.append("\n<b>Chi tiết các khoản:</b>\n").append(listSb);
        } else {
            sb.append("\n<i>Hôm nay bạn chưa có giao dịch nào.</i>");
        }

        botClient.sendMessage(chatId, sb.toString());
    }

    private void handleMonthCommand(long chatId, User user) {
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59);

        List<Transaction> transactions = transactionRepository.findAllByUserIdAndCreatedAtBetween(user.getId(), startOfMonth, endOfMonth);

        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;

        for (Transaction t : transactions) {
            BigDecimal amt = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
            if (t.getType() == TransactionType.EXPENSE) {
                totalExpense = totalExpense.add(amt);
            } else if (t.getType() == TransactionType.INCOME) {
                totalIncome = totalIncome.add(amt);
            }
        }

        BigDecimal net = totalIncome.subtract(totalExpense);

        String sb = "📊 <b>TỔNG KẾT THÁNG " + currentMonth.getMonthValue() + "/" + currentMonth.getYear() + "</b>\n\n" +
                "💸 Tổng chi: <b>" + formatMoney(totalExpense) + "</b>\n" +
                "💰 Tổng thu: <b>" + formatMoney(totalIncome) + "</b>\n" +
                "━━━━━━━━━━━━━━━\n" +
                "📈 Thặng dư tích lũy: <b>" + formatMoney(net) + "</b>";

        botClient.sendMessage(chatId, sb);
    }

    private void handleWalletsCommand(long chatId, User user) {
        List<Account> accounts = accountRepository.findAllByUserIdAndStatus(user.getId(), Status.ACTIVE);
        if (accounts.isEmpty()) {
            botClient.sendMessage(chatId, "ℹ️ Bạn chưa có ví hoạt động nào trong hệ thống.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("💳 <b>DANH SÁCH VÍ & TỪ KHÓA GỢI Ý</b>\n\n");
        for (Account a : accounts) {
            sb.append("• <b>").append(a.getName()).append("</b> (Số dư: ").append(formatMoney(a.getBalance())).append(")\n");
        }
        sb.append("\n<i>Mẹo: Thêm tên ví vào tin nhắn (ví dụ: <code>45k com vcb</code>) để trừ đúng ví mong muốn.</i>");

        botClient.sendMessage(chatId, sb.toString());
    }

    private void handleUndoCommand(long chatId, User user) {
        Optional<Transaction> lastTransOpt = transactionRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (lastTransOpt.isEmpty()) {
            botClient.sendMessage(chatId, "ℹ️ Bạn không có giao dịch nào gần đây để hoàn tác.");
            return;
        }

        Transaction lastTrans = lastTransOpt.get();
        UUID transId = lastTrans.getId();
        String desc = lastTrans.getDescription();
        BigDecimal amt = lastTrans.getAmount();

        Auth auth = new Auth();
        auth.setId(user.getId().toString());
        auth.setName(user.getName());
        auth.setEmail(user.getEmail());
        auth.setRole(user.getRole());
        auth.setStatus(user.getStatus());

        try {
            transactionService.deleteTransaction(transId, auth);
            botClient.sendMessage(chatId,
                    "🗑️ <b>ĐÃ HOÀN TÁC GIAO DỊCH THÀNH CÔNG!</b>\n\n" +
                    "• Khoản tiền: <b>" + formatMoney(amt) + "</b>\n" +
                    "• Ghi chú: <i>" + desc + "</i>\n\n" +
                    "<i>Số dư ví đã được phục hồi nguyên trạng.</i>");
        } catch (Exception e) {
            log.error("[TelegramBot] Error deleting transaction {}: {}", transId, e.getMessage());
            botClient.sendMessage(chatId, "❌ Không thể hoàn tác giao dịch: " + e.getMessage());
        }
    }

    private void handleCreateTransaction(long chatId, User user, String text) {
        List<Account> activeAccounts = accountRepository.findAllByUserIdAndStatus(user.getId(), Status.ACTIVE);
        if (activeAccounts.isEmpty()) {
            botClient.sendMessage(chatId, "⚠️ Bạn chưa có ví hoạt động nào trong hệ thống. Vui lòng tạo ví trên web trước.");
            return;
        }

        TelegramParsedTransaction parsed = messageParser.parse(text, activeAccounts);
        if (!parsed.isSuccess()) {
            botClient.sendMessage(chatId, "⚠️ " + parsed.getErrorMessage());
            return;
        }

        // Chọn ví thanh toán
        Account targetAccount = null;
        if (parsed.getAccountKeyword() != null) {
            for (Account a : activeAccounts) {
                if (a.getName().equalsIgnoreCase(parsed.getAccountKeyword())) {
                    targetAccount = a;
                    break;
                }
            }
        }

        // Nếu không khớp từ khóa ví, chọn ví Tiền mặt hoặc ví đầu tiên
        if (targetAccount == null) {
            for (Account a : activeAccounts) {
                String name = a.getName().toLowerCase();
                if (name.contains("tiền mặt") || name.contains("tien mat") || name.contains("cash")) {
                    targetAccount = a;
                    break;
                }
            }
            if (targetAccount == null) {
                targetAccount = activeAccounts.get(0);
            }
        }

        // Chuẩn bị Request
        TransactionRequest request = new TransactionRequest();
        request.setAccountId(targetAccount.getId());
        request.setAmount(parsed.getAmount());
        request.setType(parsed.getType());
        request.setCategory(parsed.getCategory());
        request.setCurrency(targetAccount.getCurrency());
        request.setDescription(parsed.getDescription());
        request.setHaveImage(false);
        request.setCreateAt(LocalDateTime.now());

        Auth auth = new Auth();
        auth.setId(user.getId().toString());
        auth.setName(user.getName());
        auth.setEmail(user.getEmail());
        auth.setRole(user.getRole());
        auth.setStatus(user.getStatus());

        try {
            transactionService.createTransaction(request, auth, null);

            // Lấy lại số dư mới nhất của ví
            Account updatedAcc = accountRepository.findById(targetAccount.getId()).orElse(targetAccount);

            String icon = parsed.getType() == TransactionType.INCOME ? "🟢 <b>ĐÃ THÊM THU NHẬP</b>" : "🔴 <b>ĐÃ THÊM CHI TIÊU</b>";
            String sign = parsed.getType() == TransactionType.INCOME ? "+" : "-";

            String responseMsg =
                    icon + "\n\n" +
                    "💵 Số tiền: <b>" + sign + formatMoney(parsed.getAmount()) + "</b>\n" +
                    "🏷️ Danh mục: <b>" + parsed.getCategoryName() + "</b>\n" +
                    "📝 Ghi chú: <i>" + parsed.getDescription() + "</i>\n" +
                    "🏦 Ví: <b>" + targetAccount.getName() + "</b>\n" +
                    "💰 Số dư ví còn lại: <code>" + formatMoney(updatedAcc.getBalance()) + "</code>\n\n" +
                    "<i>Gõ /huy nếu bạn muốn hủy giao dịch này.</i>";

            botClient.sendMessage(chatId, responseMsg);

        } catch (Exception e) {
            log.error("[TelegramBot] Error creating transaction: {}", e.getMessage(), e);
            botClient.sendMessage(chatId, "❌ Lỗi khi lưu giao dịch: " + e.getMessage());
        }
    }
}
