# 📋 ĐÁNH GIÁ ĐIỀU KIỆN & ĐẶC TẢ KỸ THUẬT TRIỂN KHAI YÊU CẦU 1: TELEGRAM BOT CÁ NHÂN

> **Tài liệu phân tích kỹ thuật**: Đối soát giữa Lộ trình phát triển (`project_road_map.md` - Phase Q4/2026) và hiện trạng mã nguồn module Telegram Backend (`com.example.financial_management.telegram`).  
> **Ngày lập**: 11/10/2026  
> **Trạng thái kết luận**: ✅ **Đủ điều kiện hoàn tất 100% Backend** (Đã hoàn thành Whitelist Guard, 6 API Web, bóc tách hashtag #tag, phản hồi số tiền còn lại và % ngân sách thẻ/chung ngay khi ghi chép, Cronjob 21:30 và Cảnh báo ngân sách tức thì khi >= 90%).

---

## 🎯 1. Chi Tiết Yêu Cầu Số 1 Trong Lộ Trình (Phase Q4/2026)

Trích xuất từ mục **Phần 2: Ưu tiên phát triển gần (Q4/2026)**:
*Mục tiêu: Đang đi cà phê hoặc đi chợ, chỉ cần mở Telegram nhắn 1 dòng là tiền tự trừ vào ví.*

1. **Kết nối tài khoản Telegram riêng**:
   - Cấu hình Chat ID cá nhân để chỉ duy nhất tôi mới có quyền tương tác với bot.
2. **Bóc tách tin nhắn tự nhiên (Smart Syntax)**:
   - `cf 35k` $\rightarrow$ Chi 35.000đ, Danh mục: Ăn uống, Ví mặc định.
   - `an toi 150k vcb` $\rightarrow$ Chi 150.000đ, Danh mục: Ăn uống, Trừ ví VCB.
   - `luong 30tr vcb` $\rightarrow$ Thu 30.000.000đ, Danh mục: Lương, Cộng ví VCB.
   - `xang 70k #di-lai` $\rightarrow$ Gắn thẻ `#di-lai`.
3. **Bot gửi thông báo tự động hàng ngày**:
   - **21:30 mỗi tối**: Bot gửi tin nhắn tổng kết: *"Hôm nay bạn đã chi 245.000đ (3 giao dịch). Ngân sách tháng còn lại: 4.250.000đ."*
   - **Cảnh báo tức thì**: Nhắn tin báo động ngay khi một khoản chi khiến ngân sách danh mục vượt quá 90%.

---

## 🔍 2. Hiện Trạng Module Telegram Hiện Có

Các thành phần đã được triển khai tại package `com.example.financial_management.telegram`:

| Thành phần / File | Chức năng hiện tại |
| :--- | :--- |
| `TelegramBotConfig.java` | Đọc cấu hình `telegram.bot.*` (`enabled`, `token`, `username`, `defaultUserEmail`). |
| `TelegramBotClient.java` | Client HTTP kết nối Telegram API: `getMe`, `getUpdates`, `sendMessage` (HTML), `deleteMessage`. |
| `TelegramBotPollingService.java` | Daemon Thread Long Polling liên tục nhận tin nhắn từ Telegram. |
| `TelegramMessageParser.java` | NLP Regex bóc tách số tiền đa định dạng (`50k`, `1.5tr`, `32500`, phân cách dấu chấm/phẩy), nhận diện ví và danh mục. |
| `TelegramCommandHandler.java` | Điều phối lệnh: `/start`, `/help`, `/link <email> <pwd>`, `/unlink`, `/sodu`, `/homnay`, `/thangnay`, `/vi`, `/huy` và tạo giao dịch. |
| `TelegramController.java` | `GET /telegram/test-parse` (test bóc tách) và `POST /telegram/simulate` (giả lập tin nhắn). |

---

## ⚖️ 3. Bảng Đối Soát Điều Kiện Triển Khai

| STT | Tiêu chí yêu cầu 1 | Trạng thái hiện tại | Đánh giá điều kiện |
| :---: | :--- | :--- | :---: |
| **1** | Cấu hình Chat ID cá nhân, chỉ duy nhất chủ sở hữu tương tác | Đã xây dựng Whitelist Guard chặn hoàn toàn người lạ; Cơ chế mã OTP 6 số (hết hạn 10 phút) & Deep Link kết nối an toàn; Bộ 6 API quản lý trên Web (`/status`, `/generate-link-code`, `/unlink`, `/settings`, `/test-*`). | ✅ **ĐẠT** |
| **2.1** | Ghi chép chi tiêu tự do: `cf 35k` $\rightarrow$ Ăn uống, ví mặc định | Đã hoàn thành trong `TelegramMessageParser` & `TelegramCommandHandler`. | ✅ **ĐẠT** |
| **2.2** | Chỉ định ví thanh toán: `an toi 150k vcb` | Đã hoàn thành (tự động nhận diện `vcb`, `tcb`, `momo`, `tien mat`...). | ✅ **ĐẠT** |
| **2.3** | Ghi nhận thu nhập: `luong 30tr vcb` | Đã hoàn thành (nhận diện dấu `+`, từ khóa `luong`, `thu`, `nhan`...). | ✅ **ĐẠT** |
| **2.4** | Bóc tách thẻ hashtag: `xang 70k #di-lai` | Đã triển khai Regex bóc tách hashtag `#([\\p{L}0-9_-]+)`, lưu vào `TransactionRequest.tags` và hiển thị thẻ trong phản hồi của bot. | ✅ **ĐẠT** |
| **3.1** | Báo cáo chi tiêu tự động 21:30 mỗi tối | Đã tạo `TelegramDailySummaryCronjob` (`@Scheduled(cron = "0 30 21 * * *", zone = "Asia/Ho_Chi_Minh")`), phát sóng báo cáo tổng kết chi tiêu kèm số dư ngân sách tháng còn lại cho toàn bộ người dùng kích hoạt nhận báo cáo. | ✅ **ĐẠT** |
| **3.2** | Cảnh báo tức thì khi chi tiêu vượt quá 90% ngân sách | Đã tích hợp `BudgetService.checkingBudget` trong `TelegramCommandHandler`: khi ghi chép chi tiêu có tag (hoặc thông thường), tính toán và phản hồi ngay số tiền còn lại + % ngân sách; đối soát chính xác theo thẻ (tag) nếu khoản chi có tag (qua cả tagName, tags và tagId), hoặc hạch toán vào ngân sách chung (trừ các tag riêng) nếu chưa có ngân sách tag; tự động bắn cảnh báo đỏ tức thì khi tiến độ ngân sách đạt $\ge 90\%$ (kèm số tiền còn lại hoặc số tiền vượt). | ✅ **ĐẠT** |

---

## 🚀 4. Danh Sách Các API Cần Bổ Sung & Đặc Tả Kỹ Thuật

### 4.1. Nhóm API Quản Lý & Kết Nối Telegram Trên Web (REST Controllers)

#### ① `GET /telegram/status`
* **Mục đích**: Giao diện Web hiển thị trạng thái kết nối Telegram của người dùng hiện tại.
* **Quyền hạn**: Đã xác thực (JWT Token).
* **Response Body** (`AbstractResponse<TelegramStatusResponse>`):
```json
{
  "code": 200,
  "message": "Thành công",
  "data": {
    "isLinked": true,
    "telegramChatId": 987654321,
    "botUsername": "MyFinanceBot",
    "dailyReportEnabled": true,
    "budgetAlertEnabled": true,
    "reportTime": "21:30"
  }
}
```

---

#### ② `POST /telegram/generate-link-code`
* **Mục đích**: Tạo mã OTP 6 số kết nối an toàn (hết hạn trong 10 phút) để người dùng chat với Bot mà không cần lộ mật khẩu tài khoản.
* **Quyền hạn**: Đã xác thực (JWT Token).
* **Response Body** (`AbstractResponse<TelegramLinkCodeResponse>`):
```json
{
  "code": 200,
  "message": "Mã liên kết được tạo thành công",
  "data": {
    "code": "849201",
    "expiredAt": "2026-10-11T17:40:00",
    "deepLink": "https://t.me/MyFinanceBot?start=LINK_849201"
  }
}
```
* **Logic phía Bot**: Khi người dùng click Deep Link hoặc gõ `/link 849201`, bot tự động đối soát mã hợp lệ và gán `chatId` cho tài khoản.

---

#### ③ `POST /telegram/unlink`
* **Mục đích**: Hủy liên kết Telegram khỏi tài khoản trực tiếp từ giao diện Web.
* **Quyền hạn**: Đã xác thực (JWT Token).
* **Response Body**:
```json
{
  "code": 200,
  "message": "Đã hủy liên kết Telegram thành công",
  "data": null
}
```

---

#### ④ `POST /telegram/settings`
* **Mục đích**: Tùy chỉnh bật/tắt nhận báo cáo tối và cảnh báo ngân sách.
* **Request Body**:
```json
{
  "dailyReportEnabled": true,
  "budgetAlertEnabled": true
}
```

---

#### ⑤ `POST /telegram/test-daily-summary` & `POST /telegram/test-budget-alert` (Dành cho Dev/Testing)
* **Mục đích**: Bắn ngay tin nhắn tổng kết hoặc cảnh báo mẫu về Telegram để kiểm thử giao diện thông báo mà không cần đợi đến 21:30.

---

### 4.2. Nâng Cấp Bóc Tách Thẻ `#tag` (Smart Syntax)

Không cần tạo thêm controller mới, mà cần nâng cấp các file hiện hữu:

1. **`TelegramMessageParser.java`**:
   * Thêm Regex bóc tách hashtag:
     ```java
     Pattern HASHTAG_PATTERN = Pattern.compile("#([\\p{L}0-9_-]+)");
     ```
   * Trích xuất danh sách tags (ví dụ `["di-lai"]`) và xóa hashtag khỏi ghi chú giao dịch.
2. **`TelegramParsedTransaction.java`**:
   * Bổ sung trường:
     ```java
     private List<String> tags;
     ```
3. **`TelegramCommandHandler.java`**:
   * Khi gọi `transactionService.createTransaction`:
     ```java
     if (parsed.getTags() != null && !parsed.getTags().isEmpty()) {
         request.setTags(parsed.getTags());
     }
     ```
   *(Backend `TransactionService` và `TagService` đã hỗ trợ sẵn logic `resolveTags`, chỉ cần truyền mảng tags vào `TransactionRequest`).*

---

### 4.3. Thiết Kế Cronjob Báo Cáo 21:30 & Cảnh Báo Ngân Sách Tức Thì

#### A. Cronjob 21:30 Mỗi Tối (`TelegramDailySummaryCronjob.java`)
* **Thời gian kích hoạt**: `@Scheduled(cron = "0 30 21 * * *", zone = "Asia/Ho_Chi_Minh")`.
* **Luồng xử lý**:
  1. Lấy danh sách tất cả các User đang hoạt động có `telegramChatId != null`.
  2. Với mỗi user:
     - Lấy tổng chi tiêu trong ngày hôm nay từ `transactionRepository.findByUserIdAndCreatedAtBetween(...)`.
     - Lấy tổng số giao dịch đã phát sinh trong ngày.
     - Lấy ngân sách tháng còn lại từ `budgetService.getBudgetSummary(currentMonth, currentYear, auth)`.
  3. Gửi tin nhắn Telegram qua `TelegramBotClient.sendMessage(chatId, message)`:
     ```html
     🌙 <b>TỔNG KẾT CHI TIÊU HÔM NAY (11/10/2026)</b>

     💸 Hôm nay bạn đã chi: <b>245.000 ₫</b> (3 giao dịch)
     📊 Ngân sách tháng còn lại: <b>4.250.000 ₫</b>

     <i>Chúc bạn một buổi tối thư thái! ✨</i>
     ```

#### B. Cảnh Báo Vượt 90% Ngân Sách Tức Thì (`TelegramCommandHandler.java`)
* **Vị trí tích hợp**: Ngay sau khi `transactionService.createTransaction(request, auth, null)` thành công với giao dịch loại `EXPENSE`.
* **Luồng xử lý**:
  1. Gọi `findAffectedBudgets`: Nhận diện đúng ngân sách bị ảnh hưởng trực tiếp (ngân sách thẻ `#tag` nếu khoản chi có tag và đã có ngân sách riêng; hoặc ngân sách chung nếu không gắn tag / thẻ chưa có ngân sách riêng).
  2. Tính toán số tiền còn lại (`remaining = amount - spending`) và tỷ lệ % ngân sách chiếm dụng (`usedPercentage = spending / amount * 100`).
  3. Phản hồi ngay trạng thái ngân sách trong tin nhắn tạo giao dịch:
     ```text
     📊 Ngân sách Ăn uống 🍜 (Thẻ #cafe): Còn lại 50.000 ₫ (chiếm 95.0%)
     ```
  4. Nếu `usedPercentage >= 90.0` và người dùng bật `telegramBudgetAlertEnabled`:
     - Gửi ngay tin nhắn cảnh báo đỏ:
       ```html
       🚨 <b>CẢNH BÁO VƯỢT HẠN MỨC NGÂN SÁCH!</b>

       Danh mục: <b>Ăn uống 🍜 (Thẻ #cafe)</b>
       📊 Chiếm ngân sách: <b>95.0%</b>
       💵 Đã chi: <b>950.000 ₫</b> / Hạn mức: <b>1.000.000 ₫</b>
       💰 Số tiền còn lại: <code>50.000 ₫</code>

       ⚠️ Bạn sắp chạm trần ngân sách cho tháng này!
       ```
       *(Nếu $\ge 100\%$, tiêu đề đổi thành **BÁO ĐỘNG ĐỎ** và hiển thị số tiền đã vượt hạn mức)*.

---

---

## 🤖 6. Số Lượng Bot Cần Tạo & Thiết Kế Kiến Trúc Bot (Bot Provisioning)

### 6.1. Đánh giá số lượng bot: Nên tạo 1 Bot hay 2 Bot?

Khi xét toàn bộ nhu cầu tài chính cá nhân trong lộ trình:
- **Tương tác 2 chiều (Chat & Ghi chép)**: Nhận tin nhắn, bóc tách NLP, trả lời tra cứu số dư.
- **Thông báo 1 chiều (Push Notifications)**: Báo cáo tối 21:30, cảnh báo ngân sách $\ge 90\%$.
- **Gửi file định kỳ (Backup)**: Tự động gửi file sao lưu database cuối mỗi tháng (Mục 4 Roadmap).

Có 2 phương án kiến trúc:

| Tiêu chí so sánh | 🥇 Phương án 1: 1 Bot Duy Nhất (Khuyên dùng) | 🥈 Phương án 2: Tách 2 Bot Riêng Biệt |
| :--- | :--- | :--- |
| **Số lượng bot cần tạo** | **1 Bot duy nhất** (All-in-One Assistant) | **2 Bot riêng biệt** (1 Chat Bot + 1 Alert Bot) |
| **Tên bot mẫu** | `@MyFinanceAppBot` | `Bot 1: @MyFinanceBot`<br>`Bot 2: @MyFinanceNotifyBot` |
| **Phân chia nhiệm vụ** | Đảm nhận trọn vẹn cả 3 vai trò:<br>1. Ghi chép thu chi & Tra cứu lệnh.<br>2. Bắn thông báo 21:30 & Cảnh báo ngân sách đỏ.<br>3. Gửi file nén backup cuối tháng. | - **Bot 1**: Chuyên nhận tin nhắn, parse cú pháp, tra cứu nhanh dưới 3 giây.<br>- **Bot 2**: Chuyên gửi thông báo 1 chiều từ server (báo cáo tối, cảnh báo, file backup). |
| **Trải nghiệm người dùng** | ⭐ **Tối ưu nhất cho cá nhân**: Mọi thông tin tài chính tập trung trong 1 cuộc hội thoại duy nhất trên Telegram, không phải mở 2 khung chat. | Tin nhắn ghi chép không bị trôi khi server gửi file backup nặng; có thể tắt chuông (mute) riêng bot thông báo. |
| **Độ phức tạp Backend** | Rất đơn giản, chỉ cần 1 cấu hình `telegram.bot.token`, 1 client HTTP và 1 polling loop. | Phải quản lý 2 Token, 2 HTTP Client, người dùng phải `/start` và liên kết Chat ID ở cả 2 bot. |

> 💡 **Khuyến nghị chính thức cho dự án cá nhân (Personal Solo Project)**:  
> **Chỉ cần tạo DUY NHẤT 1 BOT**. Telegram Bot API cho phép một bot vừa lắng nghe tin nhắn (Polling) vừa chủ động gửi tin nhắn (`sendMessage`) và tệp tin (`sendDocument`) tới cùng một `chatId` của bạn bất cứ lúc nào mà không gặp xung đột nào.

---

### 6.2. Hướng dẫn thiết lập Bot với `@BotFather` trên Telegram

Để triển khai, bạn cần mở ứng dụng Telegram và tương tác với tài khoản chính thức **`@BotFather`**:

1. **Tạo Bot mới**:
   * Gửi lệnh: `/newbot`
   * Đặt tên hiển thị (Bot Name): `My Finance Assistant`
   * Đặt username (phải kết thúc bằng chữ `bot`): ví dụ `nghy_finance_bot` hoặc `my_personal_finance_bot`
2. **Lưu Token bí mật**:
   * `@BotFather` sẽ trả về mã Token HTTP API (dạng: `7123456789:AAFlM...xyz`).
   * Điền mã này vào cấu hình Backend (`application.properties`):
     ```properties
     telegram.bot.enabled=true
     telegram.bot.token=7123456789:AAFlM...xyz
     telegram.bot.username=nghy_finance_bot
     ```
3. **Cài đặt Danh sách Menu Lệnh nhanh (`/setcommands`)**:
   * Gửi lệnh: `/setcommands` $\rightarrow$ Chọn bot vừa tạo $\rightarrow$ Dán danh sách lệnh sau:
     ```text
     sodu - Xem số dư các ví và tổng tài sản
     homnay - Chi tiêu và thu nhập hôm nay
     thangnay - Báo cáo thu chi tháng hiện tại
     vi - Danh sách các ví tài khoản đang dùng
     huy - Hoàn tác giao dịch vừa nhập
     help - Xem hướng dẫn cú pháp ghi chép
     ```
4. **Cài đặt ảnh đại diện & Mô tả**:
   * `/setuserpic`: Tải logo đồng tiền / ví tài chính lên làm Avatar.
   * `/setdescription`: Điền mô tả *"Trợ lý ghi chép tài chính & cảnh báo ngân sách cá nhân"*.

---

## 📌 7. Kế Hoạch Triển Khai Đề Xuất (Roadmap Implementation Checklist)

- [x] **Bước 1**: Tạo 1 Bot trên `@BotFather`, lấy API Token và cấu hình danh sách commands. *(Người dùng đã cấu hình bot `@bang_linh_bot`)*
- [x] **Bước 2**: Bổ sung Regex bóc tách `#tag` trong `TelegramMessageParser` và map vào `TransactionRequest` $\rightarrow$ Hoàn thành 100% cú pháp Smart Syntax.
- [x] **Bước 3**: Thêm các API quản lý kết nối an toàn trên Web (`status`, `generate-link-code`, `unlink`, `settings`, `test-*`), cơ chế mã OTP 6 số và chặn tin nhắn từ Chat ID lạ (Whitelist Guard).
- [x] **Bước 4**: Tạo class `TelegramDailySummaryCronjob` gửi báo cáo 21:30 hàng ngày.
- [x] **Bước 5**: Tích hợp kiểm tra ngân sách theo thẻ tag, tính toán số tiền còn lại, % ngân sách và bắn cảnh báo tức thì khi `usedPercentage >= 90%` trong `TelegramCommandHandler`.
- [ ] **Bước 6**: Cập nhật giao diện Vue 3 trên trang Profile để người dùng lấy mã liên kết OTP và cấu hình thông báo.

