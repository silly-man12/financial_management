package com.example.financial_management.telegram;

import com.example.financial_management.constant.AccountType;
import com.example.financial_management.constant.Status;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.budget.BudgetCheckingResponse;
import com.example.financial_management.model.transaction.TransactionRequest;
import com.example.financial_management.repository.AccountRepository;
import com.example.financial_management.repository.TagRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;
import com.example.financial_management.services.BudgetService;
import com.example.financial_management.services.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final TelegramService telegramService;
    private final BudgetService budgetService;
    private final TagRepository tagRepository;

    private static final Locale VI_LOCALE = Locale.of("vi", "VN");

    private String formatMoney(BigDecimal amount) {
        if (amount == null)
            return "0 ₫";
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

        // 1. Kiểm tra lệnh /start (hỗ trợ cả /start và deep link /start LINK_xxxxxx)
        if (trimmed.toLowerCase().startsWith("/start")) {
            handleStartCommand(chatId, trimmed);
            return;
        }

        // 2. Kiểm tra lệnh /link
        if (trimmed.toLowerCase().startsWith("/link")) {
            handleLinkCommand(chatId, trimmed);
            return;
        }

        // 3. Kiểm tra lệnh /help hoặc /huongdan (hướng dẫn cú pháp chi tiết)
        String lowerCmd = trimmed.toLowerCase();
        if (lowerCmd.equals("/help") || lowerCmd.equals("/huongdan")) {
            handleHelpCommand(chatId);
            return;
        }

        // 4. Whitelist Guard: Kiểm tra người dùng đã liên kết với Chat ID này chưa
        Optional<User> userOpt = userRepository.findByTelegramChatIdAndStatus(chatId, Status.ACTIVE);

        if (userOpt.isEmpty()) {
            botClient.sendMessage(chatId,
                    "⛔ <b>Bạn chưa liên kết tài khoản!</b>\n\n" +
                            "Vì lý do an toàn bảo mật (Whitelist Guard), bot chỉ phục vụ tài khoản đã được xác thực.\n\n"
                            +
                            "👉 Vui lòng đăng nhập vào ứng dụng Web, mở phần <b>Cài đặt Telegram</b> để tạo mã liên kết OTP (hoặc bấm vào liên kết trực tiếp).\n\n"
                            +
                            "💡 <i>Gõ <code>/help</code> để xem trước cẩm nang hướng dẫn sử dụng bot.</i>");
            return;
        }

        User user = userOpt.get();

        // 5. Xử lý các lệnh tra cứu (chỉ cho phép user đã xác thực)
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

    private void handleStartCommand(long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length > 1) {
            String param = parts[1].trim();
            // Người dùng click deep link: /start LINK_123456 hoặc /start 123456
            Optional<User> linkedUserOpt = telegramService.linkUserByCode(param, chatId);
            if (linkedUserOpt.isPresent()) {
                User user = linkedUserOpt.get();
                String msg = "✅ <b>LIÊN KẾT TÀI KHOẢN THÀNH CÔNG!</b>\n\n" +
                        "👤 Người dùng: <b>" + user.getName() + "</b>\n" +
                        "📧 Email: <b>" + user.getEmail() + "</b>\n\n" +
                        "⚡ <b>Bây giờ bạn có thể ghi chép chi tiêu siêu tốc ngay lập tức:</b>\n" +
                        "• <code>50k cafe</code>\n" +
                        "• <code>35k com trua vcb</code>\n" +
                        "• <code>+15tr luong</code>\n\n" +
                        "📊 <i>Gõ /help để xem danh sách lệnh tra cứu số dư và báo cáo.</i>";
                botClient.sendMessage(chatId, msg);
                return;
            } else {
                botClient.sendMessage(chatId,
                        "❌ <b>Mã liên kết không hợp lệ hoặc đã hết hạn (quá 10 phút)!</b>\n\n" +
                                "👉 Vui lòng đăng nhập vào ứng dụng Web, mở phần <b>Cài đặt Telegram</b> để tạo mã liên kết mới.");
                return;
            }
        }

        // Không kèm mã: Kiểm tra xem chatId này đã được liên kết hay chưa
        Optional<User> userOpt = userRepository.findByTelegramChatIdAndStatus(chatId, Status.ACTIVE);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String msg = "👋 <b>Xin chào " + user.getName() + "!</b>\n\n" +
                    "Tài khoản của bạn đã được liên kết với Bot Quản Lý Tài Chính Cá Nhân.\n\n" +
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
        } else {
            String msg = "👋 <b>Chào mừng bạn đến với Bot Quản Lý Tài Chính Cá Nhân!</b>\n\n" +
                    "🔒 <b>Bảo mật tài khoản (Whitelist Guard):</b>\n" +
                    "Bot hoạt động ở chế độ riêng tư. Để sử dụng bot, bạn cần liên kết với tài khoản trên ứng dụng Web.\n\n"
                    +
                    "👉 <b>Cách liên kết an toàn:</b>\n" +
                    "1. Đăng nhập vào ứng dụng Web Quản lý Tài chính.\n" +
                    "2. Vào mục <b>Cài đặt Telegram</b> và bấm <b>Tạo mã liên kết OTP</b>.\n" +
                    "3. Bấm vào liên kết trực tiếp hiển thị trên Web, hoặc nhập lệnh tại đây:\n" +
                    "<code>/link &lt;mã_otp_6_số&gt;</code>\n\n" +
                    "<i>Ví dụ: <code>/link 849201</code></i>";
            botClient.sendMessage(chatId, msg);
        }
    }

    private void handleHelpCommand(long chatId) {
        String msg = "📖 <b>CẨM NANG HƯỚNG DẪN CÚ PHÁP SỬ DỤNG BOT</b>\n\n" +
                "⚡ <b>1. CÚ PHÁP GHI CHÉP TỰ NHIÊN:</b>\n" +
                "Cấu trúc: <code>[Số tiền] [Nội dung] [Tên ví]</code>\n" +
                "<i>(Vị trí các thành phần hoàn toàn tự do, không phân biệt chữ hoa hay chữ thường)</i>\n\n" +
                "• <code>50k cafe</code> ➔ Chi 50.000đ Ăn uống (dùng ví mặc định)\n" +
                "• <code>com trua 35k vcb</code> ➔ Chi 35.000đ Ăn uống trừ ví VCB\n" +
                "• <code>100 do xang cash</code> ➔ Chi 100.000đ Di chuyển trừ ví Tiền mặt\n" +
                "• <code>1.5tr mua giay mb</code> ➔ Chi 1.500.000đ Mua sắm trừ ví MB Bank\n\n" +
                "━━━━━━━━━━━━━━━━━━━\n" +
                "💵 <b>2. QUY TẮC NHẬP SỐ TIỀN CỰC KỲ LINH HOẠT:</b>\n" +
                "• <b>Viết tắt nghìn:</b> <code>50k</code>, <code>45.5k</code>, <code>30 nghin</code>, <code>30 ngan</code>\n"
                +
                "• <b>Viết tắt triệu:</b> <code>1.5tr</code>, <code>2m</code>, <code>12.3tr</code>, <code>2 trieu</code>\n"
                +
                "• <b>Viết tắt tỷ:</b> <code>1ty</code>, <code>1.2ty</code>, <code>1b</code>\n" +
                "• <b>Số nhỏ &lt; 1000:</b> Tự động hiểu là nghìn! (<code>100 do xang</code> = 100.000đ, <code>35 com trua</code> = 35.000đ)\n"
                +
                "• <b>Số có dấu chấm/phẩy:</b> <code>32.500</code>, <code>127,500</code>, <code>1.250.000</code>\n" +
                "• <b>Số nguyên bất kỳ:</b> <code>32500 banh mi</code>, <code>50000</code>\n" +
                "• <b>Số lẻ có đơn vị:</b> <code>500d keo</code>, <code>500vnd</code> ➔ hiểu là 500đ\n\n" +
                "━━━━━━━━━━━━━━━━━━━\n" +
                "🏦 <b>3. CHỈ ĐỊNH VÍ NGUỒN THANH TOÁN:</b>\n" +
                "Thêm tên ví hoặc từ khóa vào tin nhắn (ví dụ: <code>vcb</code>, <code>momo</code>, <code>mb</code>, <code>cash</code>, <code>tien mat</code>, <code>tpb</code>, <code>zalopay</code>...).\n\n"
                +
                "⚠️ <b>Lưu ý quan trọng về ví:</b>\n" +
                "• Tên ví bạn nhập <b>bắt buộc phải tồn tại</b> trong tài khoản của bạn. Nếu không tìm thấy, Bot sẽ <b>từ chối</b> giao dịch và liệt kê danh sách ví bạn đang có để bạn chọn lại, tuyệt đối không trừ nhầm ví khác.\n"
                +
                "• <b>Nếu không nhập ví:</b> Bot sẽ ưu tiên trừ vào <b>Ví Tiền mặt</b> (hoặc ví hoạt động đầu tiên của bạn).\n"
                +
                "• Gõ <code>/vi</code> để kiểm tra danh sách chính xác các ví bạn đang sở hữu.\n\n" +
                "━━━━━━━━━━━━━━━━━━━\n" +
                "💰 <b>4. GHI NHẬN KHOẢN THU NHẬP (INCOME):</b>\n" +
                "• <b>Thêm dấu <code>+</code>:</b> <code>+15tr luong vcb</code>, <code>+500k tien thuong</code>\n" +
                "• <b>Dùng từ khóa thu:</b> <code>thu 200k ban sach</code>, <code>nhan 1tr tra no</code>\n" +
                "• <b>Tự động nhận diện:</b> Tin nhắn chứa các từ như <code>luong</code>, <code>thuong</code>, <code>lai</code>... sẽ tự động được ghi nhận là Thu nhập.\n\n"
                +
                "━━━━━━━━━━━━━━━━━━━\n" +
                "🤖 <b>5. TỰ ĐỘNG PHÂN LOẠI DANH MỤC:</b>\n" +
                "Bot tự động gắn danh mục và biểu tượng theo nội dung bạn gõ:\n" +
                "• 🍜 <b>Ăn uống:</b> cafe, com, pho, tra sua, banh mi, an toi...\n" +
                "• 🛵 <b>Di chuyển:</b> xang, grab, be, gui xe, taxi...\n" +
                "• 🛒 <b>Mua sắm:</b> sieu thi, quan ao, shopee, cho...\n" +
                "• 🏠 <b>Hóa đơn:</b> dien, nuoc, internet, wifi, tien phong, tien nha...\n" +
                "• 💊 <b>Sức khỏe:</b> thuoc, kham benh, gym...\n\n" +
                "━━━━━━━━━━━━━━━━━━━\n" +
                "🔄 <b>6. SỬA SAI VÀ HOÀN TÁC (UNDO):</b>\n" +
                "Nếu bạn gõ nhầm nội dung hoặc số tiền, hãy gửi ngay lệnh:\n" +
                "👉 <code>/huy</code> hoặc <code>/undo</code>\n" +
                "Bot sẽ lập tức xóa giao dịch gần nhất và phục hồi lại số dư ví nguyên trạng!\n\n" +
                "━━━━━━━━━━━━━━━━━━━\n" +
                "📋 <b>7. DANH MỤC CÁC LỆNH HỆ THỐNG:</b>\n" +
                "• <code>/sodu</code> - Xem số dư từng ví và tổng tài sản hiện tại\n" +
                "• <code>/vi</code> - Xem danh sách các ví đang hoạt động\n" +
                "• <code>/homnay</code> - Báo cáo tổng chi tiêu hôm nay\n" +
                "• <code>/thangnay</code> - Báo cáo tổng thu chi tháng này\n" +
                "• <code>/huy</code> - Hoàn tác giao dịch vừa thêm\n" +
                "• <code>/link &lt;mã_otp&gt;</code> - Kết nối tài khoản bằng mã OTP 6 số từ Web\n" +
                "• <code>/unlink</code> - Hủy liên kết Telegram khỏi tài khoản Web\n" +
                "• <code>/help</code> hoặc <code>/huongdan</code> - Mở cẩm nang hướng dẫn cú pháp này";

        botClient.sendMessage(chatId, msg);
    }

    private void handleLinkCommand(long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length >= 3) {
            // Người dùng nhập cú pháp cũ: /link <email> <password>
            botClient.sendMessage(chatId,
                    "🛡️ <b>NÂNG CẤP AN TOÀN BẢO MẬT</b>\n\n" +
                            "Hệ thống không còn hỗ trợ gửi mật khẩu tài khoản qua chat Telegram để đảm bảo an toàn tuyệt đối.\n\n"
                            +
                            "👉 Vui lòng đăng nhập trên website, vào mục <b>Cài đặt Telegram</b> để tạo mã OTP 6 số (hết hạn sau 10 phút) và gõ cú pháp:\n"
                            +
                            "<code>/link &lt;mã_otp_6_số&gt;</code> (Ví dụ: <code>/link 849201</code>)");
            return;
        }

        if (parts.length == 2) {
            String code = parts[1].trim();
            Optional<User> linkedUserOpt = telegramService.linkUserByCode(code, chatId);
            if (linkedUserOpt.isPresent()) {
                User user = linkedUserOpt.get();
                botClient.sendMessage(chatId,
                        "✅ <b>LIÊN KẾT TÀI KHOẢN THÀNH CÔNG!</b>\n\n" +
                                "👤 Người dùng: <b>" + user.getName() + "</b>\n" +
                                "📧 Email: <b>" + user.getEmail() + "</b>\n\n" +
                                "Bây giờ bạn có thể gõ ngay một khoản chi (ví dụ: <code>50k cafe</code> hoặc <code>35k com trua vcb</code>) để bắt đầu!");
            } else {
                botClient.sendMessage(chatId,
                        "❌ <b>Mã liên kết không hợp lệ hoặc đã hết hạn (quá 10 phút)!</b>\n\n" +
                                "👉 Vui lòng vào ứng dụng Web, mở phần <b>Cài đặt Telegram</b> để tạo mã OTP mới.");
            }
            return;
        }

        botClient.sendMessage(chatId,
                "⚠️ <b>Cú pháp liên kết:</b>\n" +
                        "<code>/link &lt;mã_otp_6_số&gt;</code>\n\n" +
                        "<i>Ví dụ: <code>/link 849201</code></i>\n" +
                        "👉 Lấy mã liên kết trên ứng dụng Web tại mục <b>Cài đặt Telegram</b>.");
    }

    private void handleUnlinkCommand(long chatId, User user) {
        telegramService.unlinkUser(user.getId());
        botClient.sendMessage(chatId,
                "👋 <i>Đã hủy liên kết Telegram với tài khoản <b>" + user.getEmail() + "</b>.</i>");
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

        List<Transaction> transactions = transactionRepository.findAllByUserIdAndCreatedAtBetween(user.getId(),
                startOfDay, endOfDay);

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

        List<Transaction> transactions = transactionRepository.findAllByUserIdAndCreatedAtBetween(user.getId(),
                startOfMonth, endOfMonth);

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
            sb.append("• <b>").append(a.getName()).append("</b> (Số dư: ").append(formatMoney(a.getBalance()))
                    .append(")\n");
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
            botClient.sendMessage(chatId,
                    "⚠️ Bạn chưa có ví hoạt động nào trong hệ thống. Vui lòng tạo ví trên web trước.");
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
            String kw = parsed.getAccountKeyword().toLowerCase();
            boolean isCashKeyword = kw.contains("tiền mặt") || kw.contains("tien mat") || kw.contains("cash");

            for (Account a : activeAccounts) {
                if (a.getName().equalsIgnoreCase(parsed.getAccountKeyword())
                        || (isCashKeyword && (a.getType() == AccountType.CASH
                                || a.getName().toLowerCase().contains("tiền mặt")
                                || a.getName().toLowerCase().contains("tien mat")
                                || a.getName().toLowerCase().contains("cash")))) {
                    targetAccount = a;
                    break;
                }
            }

            // Nếu người dùng chỉ định một ví cụ thể nhưng không tìm thấy trong tài khoản
            if (targetAccount == null) {
                StringBuilder availWallets = new StringBuilder();
                for (int i = 0; i < activeAccounts.size(); i++) {
                    availWallets.append("• <b>").append(activeAccounts.get(i).getName()).append("</b>\n");
                }

                String displayKw = parsed.getRawAccountKeyword() != null
                        ? parsed.getRawAccountKeyword()
                        : parsed.getAccountKeyword();
                if (isCashKeyword && !displayKw.equalsIgnoreCase("tiền mặt")) {
                    displayKw = displayKw + " (Tiền mặt)";
                }

                botClient.sendMessage(chatId,
                        "⚠️ <b>Không tìm thấy ví: " + displayKw + "</b>\n\n" +
                                "Tài khoản của bạn hiện chưa có ví này trong hệ thống.\n\n" +
                                "💳 <b>Danh sách ví bạn hiện có:</b>\n" + availWallets + "\n" +
                                "👉 Vui lòng tạo ví trên ứng dụng Web hoặc chỉ định đúng tên ví hiện có.");
                return;
            }
        } else {
            // Không chỉ định ví: Ưu tiên chọn ví Tiền mặt nếu có, nếu không chọn ví đầu
            // tiên
            for (Account a : activeAccounts) {
                String name = a.getName().toLowerCase();
                if (name.contains("tiền mặt") || name.contains("cash") || a.getType() == AccountType.CASH) {
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
        if (parsed.getTags() != null && !parsed.getTags().isEmpty()) {
            request.setTags(parsed.getTags());
        }

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

            String icon = parsed.getType() == TransactionType.INCOME ? "🟢 <b>ĐÃ THÊM THU NHẬP</b>"
                    : "🔴 <b>ĐÃ THÊM CHI TIÊU</b>";
            String sign = parsed.getType() == TransactionType.INCOME ? "+" : "-";

            String tagDisplay = "";
            if (parsed.getTags() != null && !parsed.getTags().isEmpty()) {
                tagDisplay = "\n🏷️ Thẻ: " + parsed.getTags().stream().map(t -> "#" + t).reduce((a, b) -> a + " " + b).orElse("");
            }

            // Xét ngân sách bị ảnh hưởng (ngân sách gắn thẻ hoặc ngân sách chung)
            String budgetStatusInfo = "";
            List<BudgetCheckingResponse> affectedBudgets = Collections.emptyList();
            boolean hasTags = parsed.getTags() != null && !parsed.getTags().isEmpty();
            if (parsed.getType() == TransactionType.EXPENSE && (hasTags || user.isTelegramBudgetAlertEnabled())) {
                affectedBudgets = findAffectedBudgets(user, parsed, auth);
                if (!affectedBudgets.isEmpty()) {
                    BudgetCheckingResponse primaryBudget = affectedBudgets.get(0);
                    if (primaryBudget.getAmount() != null && primaryBudget.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal spending = primaryBudget.getSpending() != null ? primaryBudget.getSpending() : BigDecimal.ZERO;
                        BigDecimal remaining = primaryBudget.getAmount().subtract(spending);
                        BigDecimal pct = primaryBudget.getUsedPercentage() != null ? primaryBudget.getUsedPercentage() : BigDecimal.ZERO;
                        String budgetDisplayName = getBudgetDisplayName(primaryBudget);

                        if (remaining.compareTo(BigDecimal.ZERO) >= 0) {
                            budgetStatusInfo = "\n📊 Ngân sách " + budgetDisplayName + ": Còn lại <code>" + formatMoney(remaining) + "</code> (chiếm " + String.format(Locale.US, "%.1f%%", pct.doubleValue()) + ")";
                        } else {
                            budgetStatusInfo = "\n📊 Ngân sách " + budgetDisplayName + ": Đã vượt <code>" + formatMoney(remaining.abs()) + "</code> (chiếm " + String.format(Locale.US, "%.1f%%", pct.doubleValue()) + ")";
                        }
                    }
                }
            }

            String responseMsg = icon + "\n\n" +
                    "💵 Số tiền: <b>" + sign + formatMoney(parsed.getAmount()) + "</b>\n" +
                    "🏷️ Danh mục: <b>" + parsed.getCategoryName() + "</b>" + tagDisplay + "\n" +
                    "📝 Ghi chú: <i>" + parsed.getDescription() + "</i>\n" +
                    "🏦 Ví: <b>" + targetAccount.getName() + "</b>\n" +
                    "💰 Số dư ví còn lại: <code>" + formatMoney(updatedAcc.getBalance()) + "</code>" +
                    budgetStatusInfo + "\n\n" +
                    "<i>Gõ /huy nếu bạn muốn hủy giao dịch này.</i>";

            botClient.sendMessage(chatId, responseMsg);

            // Cảnh báo ngân sách tức thì nếu chi tiêu khiến ngân sách danh mục hoặc thẻ vượt quá 90%
            if (parsed.getType() == TransactionType.EXPENSE && user.isTelegramBudgetAlertEnabled()) {
                sendBudgetAlertsIfNeeded(chatId, user, affectedBudgets);
            }

        } catch (Exception e) {
            log.error("[TelegramBot] Error creating transaction: {}", e.getMessage(), e);
            botClient.sendMessage(chatId, "❌ Lỗi khi lưu giao dịch: " + e.getMessage());
        }
    }

    /**
     * Xác định danh sách ngân sách bị ảnh hưởng trực tiếp bởi giao dịch:
     * - Nếu giao dịch CÓ tag và ĐÃ CÓ ngân sách gắn tag đó: hạch toán và kiểm tra ngân sách của tag đó.
     * - Nếu giao dịch KHÔNG gắn tag, hoặc CÓ tag nhưng CHƯA CÓ ngân sách nào gắn tag đó:
     *   hạch toán vào ngân sách chung của category đó (ngân sách không có tag, tự động trừ các tag có ngân sách riêng).
     */
    public List<BudgetCheckingResponse> findAffectedBudgets(User user, TelegramParsedTransaction parsed, Auth auth) {
        try {
            LocalDate now = LocalDate.now();
            List<BudgetCheckingResponse> budgets = budgetService.checkingBudget(now.getMonthValue(), now.getYear(), auth);
            if (budgets == null || budgets.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> transTags = (parsed.getTags() != null) ? parsed.getTags() : Collections.emptyList();
            List<BudgetCheckingResponse> matchedTaggedBudgets = new ArrayList<>();
            List<BudgetCheckingResponse> generalBudgets = new ArrayList<>();

            for (BudgetCheckingResponse b : budgets) {
                boolean hasTags = (b.getTagId() != null)
                        || (b.getTagName() != null && !b.getTagName().isBlank())
                        || (b.getTags() != null && !b.getTags().isEmpty());

                if (hasTags) {
                    if (matchesBudgetTag(b, transTags, user.getId())) {
                        matchedTaggedBudgets.add(b);
                    }
                } else if (b.getCategory() == parsed.getCategory()) {
                    generalBudgets.add(b);
                }
            }

            if (!matchedTaggedBudgets.isEmpty()) {
                return matchedTaggedBudgets;
            } else {
                return generalBudgets;
            }
        } catch (Exception e) {
            log.warn("[TelegramBot] Error checking affected budgets for user {}: {}", user.getEmail(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Gửi tin nhắn cảnh báo đỏ tức thì nếu ngân sách bị ảnh hưởng đạt >= 90% hạn mức.
     */
    public void sendBudgetAlertsIfNeeded(long chatId, User user, List<BudgetCheckingResponse> affectedBudgets) {
        if (affectedBudgets == null || affectedBudgets.isEmpty()) {
            return;
        }

        for (BudgetCheckingResponse targetBudget : affectedBudgets) {
            if (targetBudget.getUsedPercentage() != null
                    && targetBudget.getUsedPercentage().compareTo(BigDecimal.valueOf(90)) >= 0) {

                String displayName = getBudgetDisplayName(targetBudget);
                BigDecimal spending = targetBudget.getSpending() != null ? targetBudget.getSpending() : BigDecimal.ZERO;
                BigDecimal amount = targetBudget.getAmount() != null ? targetBudget.getAmount() : BigDecimal.ZERO;
                BigDecimal remaining = amount.subtract(spending);
                double pct = targetBudget.getUsedPercentage().doubleValue();

                boolean isOver = targetBudget.getUsedPercentage().compareTo(BigDecimal.valueOf(100)) >= 0;
                String header = isOver
                        ? "🚨 <b>BÁO ĐỘNG ĐỎ VƯỢT HẠN MỨC NGÂN SÁCH!</b>"
                        : "🚨 <b>CẢNH BÁO VƯỢT HẠN MỨC NGÂN SÁCH!</b>";

                String remainingLine;
                if (remaining.compareTo(BigDecimal.ZERO) >= 0) {
                    remainingLine = "💰 Số tiền còn lại: <code>" + formatMoney(remaining) + "</code>\n\n" +
                            "⚠️ Bạn sắp chạm trần ngân sách cho tháng này!";
                } else {
                    remainingLine = "⚠️ Đã vượt hạn mức: <code>" + formatMoney(remaining.abs()) + "</code>\n\n" +
                            "🚨 <b>Bạn đã vượt quá hạn mức ngân sách cho tháng này!</b>";
                }

                String alertMsg = header + "\n\n" +
                        "Danh mục: <b>" + displayName + "</b>\n" +
                        "📊 Chiếm ngân sách: <b>" + String.format(Locale.US, "%.1f%%", pct) + "</b>\n" +
                        "💵 Đã chi: <b>" + formatMoney(spending) + "</b> / Hạn mức: <b>" + formatMoney(amount) + "</b>\n" +
                        remainingLine;

                botClient.sendMessage(chatId, alertMsg);
                log.info("[TelegramBot] Triggered budget alert for user {} - {}: {}% (remaining: {})",
                        user.getEmail(), displayName, targetBudget.getUsedPercentage(), remaining);
            }
        }
    }

    public void checkAndSendBudgetAlert(long chatId, User user, TelegramParsedTransaction parsed, Auth auth) {
        List<BudgetCheckingResponse> affectedBudgets = findAffectedBudgets(user, parsed, auth);
        sendBudgetAlertsIfNeeded(chatId, user, affectedBudgets);
    }

    public String getBudgetDisplayName(BudgetCheckingResponse targetBudget) {
        String catBaseName = messageParser.getCategoryVietnameseName(targetBudget.getCategory());
        if (catBaseName == null || catBaseName.isBlank()) {
            catBaseName = "Danh mục #" + targetBudget.getCategory();
        }

        boolean isTagged = (targetBudget.getTagName() != null && !targetBudget.getTagName().isBlank())
                || (targetBudget.getTags() != null && !targetBudget.getTags().isEmpty());

        if (isTagged) {
            String rawTagName = targetBudget.getTagName() != null ? targetBudget.getTagName()
                    : (targetBudget.getTags() != null && !targetBudget.getTags().isEmpty()
                            ? targetBudget.getTags().get(0).getName() : "");
            return catBaseName + " (Thẻ #" + cleanTag(rawTagName) + ")";
        }
        return catBaseName;
    }

    private String cleanTag(String tag) {
        if (tag == null) return "";
        return tag.trim().replaceFirst("^#+", "").trim();
    }

    private boolean matchesBudgetTag(BudgetCheckingResponse b, List<String> transTags, UUID userId) {
        if (transTags == null || transTags.isEmpty()) {
            return false;
        }

        if (b.getTagName() != null && !b.getTagName().isBlank()) {
            String cleanBudgetTag = cleanTag(b.getTagName());
            for (String tag : transTags) {
                if (cleanBudgetTag.equalsIgnoreCase(cleanTag(tag))) {
                    return true;
                }
            }
        }

        if (b.getTags() != null) {
            for (com.example.financial_management.model.tag.TagResponse tr : b.getTags()) {
                if (tr != null && tr.getName() != null && !tr.getName().isBlank()) {
                    String cleanBudgetTag = cleanTag(tr.getName());
                    for (String tag : transTags) {
                        if (cleanBudgetTag.equalsIgnoreCase(cleanTag(tag))) {
                            return true;
                        }
                    }
                }
            }
        }

        if (b.getTagId() != null && tagRepository != null && userId != null) {
            try {
                List<com.example.financial_management.entity.Tag> userTags =
                        tagRepository.findAllByUserIdAndNameIn(userId, transTags);
                for (com.example.financial_management.entity.Tag ut : userTags) {
                    if (ut != null && b.getTagId().equals(ut.getId())) {
                        return true;
                    }
                }
            } catch (Exception e) {
                log.debug("Error matching tag by id: {}", e.getMessage());
            }
        }

        return false;
    }
}
