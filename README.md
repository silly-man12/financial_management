# 💰 Financial Management System (Hệ thống Quản lý Tài chính Cá nhân)

Hệ thống Backend xây dựng trên nền tảng **Spring Boot 3** và **Java 21**, cung cấp trọn bộ RESTful API cho ứng dụng quản lý tài chính cá nhân: theo dõi chi tiêu, quản lý ví/tài khoản ngân hàng, lập ngân sách, mục tiêu tiết kiệm và lịch sử góp quỹ, sổ nợ và phân tích báo cáo tài chính chuyên sâu.

---

## 📑 Mục lục
- [✨ Tính năng chính](#-tính-năng-chính)
- [🛠️ Công nghệ sử dụng](#️-công-nghệ-sử-dụng)
- [🏗️ Cấu trúc dự án](#️-cấu-trúc-dự-án)
- [⚙️ Cài đặt & Cấu hình](#️-cài-đặt--cấu-hình)
- [🚀 Chạy ứng dụng](#-chạy-ứng-dụng)
- [📚 Danh sách API chi tiết](#-danh-sách-api-chi-tiết)
- [🔢 Bảng tra cứu Hằng số (Constants Reference)](#-bảng-tra-cứu-hằng-số-constants-reference)
- [🔐 Bảo mật & Toàn vẹn Dữ liệu](#-bảo-mật--toàn-vẹn-dữ-liệu)

---

## ✨ Tính năng chính

### 1. 👤 Quản lý Người dùng & Xác thực (Auth & User)
- **Đăng ký / Đăng nhập**: Xác thực qua JWT Token, phân quyền `USER (2)` và `ADMIN (1)`.
- **Mật khẩu an toàn**: Mã hóa mật khẩu nhiều lớp bằng BCrypt + Custom Salt.
- **Quên / Đặt lại mật khẩu**: Gửi email chứa link xác thực token tạm thời (15 phút) qua Gmail SMTP.
- **Quản trị viên (Admin)**: Xem danh sách người dùng, kích hoạt / khóa tài khoản (`ACTIVE = 1` / `INACTIVE = 2`).

### 2. 💳 Quản lý Tài khoản / Ví (Accounts)
- Quản lý đa dạng các loại tài khoản: Ví tiền mặt (`CASH`), Ngân hàng (`BANK`), Thẻ tín dụng (`CREDIT_CARD`), Ví điện tử (`E_WALLET`), Tài khoản đầu tư (`INVESTMENT`), Sổ tiết kiệm (`SAVINGS`).
- Theo dõi số dư tự động cập nhật chính xác theo từng giao dịch thu/chi/chuyển tiền/nạp rút quỹ.
- **Bảo vệ toàn vẹn**: Chặn xóa cứng tài khoản khi đã phát sinh giao dịch sao kê để bảo vệ lịch sử sổ sách.

### 3. 💸 Quản lý Giao dịch (Transactions)
- **Thu / Chi / Chuyển khoản**: Ghi chép các khoản Chi (`EXPENSE = 0`), Thu (`INCOME = 1`), và Chuyển tiền giữa các tài khoản (`TRANSFER = 2`).
- **Phân loại danh mục (Category)**: Đầy đủ danh mục chi tiêu thiết yếu (Ăn uống, Di chuyển, Du lịch, Mua sắm, Hóa đơn...) và nguồn thu nhập (Lương, Kinh doanh, Đầu tư...).
- **Giao dịch gần nhất**: Hỗ trợ API tra cứu nhanh 6 giao dịch mới nhất của từng tài khoản phục vụ widget/dashboard.
- **Đính kèm hóa đơn & Thẻ tag**: Hỗ trợ tải lên ảnh hóa đơn / chứng từ thanh toán (`MultipartFile` qua `multipart/form-data`) và gắn các thẻ phân loại (`Tag`).
- **Xử lý ngày giờ linh hoạt**: Tự động nhận diện và chuyển đổi mọi định dạng ngày giờ (`yyyy-MM-dd'T'HH:mm:ss`, `yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd`, ISO OffsetDateTime).
- **Bộ lọc & Phân trang**: Tìm kiếm và lọc giao dịch theo ngày, danh mục, tài khoản, khoảng tiền với Spring JPA Specification.
- **Ràng buộc toàn vẹn**: Chặn sửa/xóa trực tiếp các giao dịch sinh ra từ Quản lý nợ hoặc Mục tiêu tiết kiệm để tránh làm lệch số dư.

### 4. 📊 Ngân sách Chi tiêu (Budgets)
- Thiết lập hạn mức chi tiêu theo từng danh mục theo tháng/năm.
- API đối soát ngân sách (`/budgets/checking`): Tự động so sánh ngân sách đã đặt với số tiền thực chi để kiểm soát bội chi.

### 5. 🔄 Giao dịch Định kỳ (Recurring Transactions)
- Tự động hóa các khoản thu/chi lặp lại theo chu kỳ: Hàng ngày (`DAILY = 1`), Hàng tuần (`WEEKLY = 2`), Hàng tháng (`MONTHLY = 3`), Hàng năm (`YEARLY = 4`).
- Tự động tính toán `nextExecutionDate` và hỗ trợ Cronjob tự động quét mỗi ngày lúc `00:00:00` để ghi nhận các giao dịch đến hạn.
- Hỗ trợ "Ghi nhận ngay" (`POST /{id}/execute-now`) cho từng quy tắc hoặc Bật / Tạm dừng (`status`: `1: ACTIVE`, `2: INACTIVE`).

### 6. 🎯 Mục tiêu Tiết kiệm & Lịch sử Góp quỹ (Saving Goals & Contributions)
- **Quản lý mục tiêu**: Khởi tạo các quỹ mục tiêu (Mua xe, Mua nhà, Du lịch, Quỹ khẩn cấp...).
- **Lịch sử đóng góp chi tiết (`SavingGoalContribution`)**:
  - Lưu vết toàn bộ từng lần nạp/góp tiền (`DEPOSIT = 1`) và rút tiền (`WITHDRAW = 2`).
  - Ghi nhận ngày giao dịch, tài khoản nguồn/đích trích tiền, ghi chú và mã giao dịch sao kê liên kết.
  - Tự động ghi nhận đóng góp khi khởi tạo số dư ban đầu, khi nạp quỹ hoặc rút quỹ.
- **Tiến độ tích lũy**: Theo dõi tiến độ tích lũy `%` tự động và chuyển trạng thái sang `COMPLETED = 2` khi đạt $\ge 100\%$.
- **Hoàn tác linh hoạt**: Cho phép hủy 1 lần đóng góp, tự động hoàn tác số dư mục tiêu và số dư tài khoản ví liên quan.

### 7. 🤝 Quản lý Nợ - Sổ nợ (Debt Management)
- Theo dõi các khoản **Đi vay (`BORROW = 1` - Nợ phải trả)** và **Cho vay (`LEND = 2` - Nợ phải thu)**.
- Ghi nhận lịch sử từng lần trả bớt / thu nợ (`DebtPayment`), tự động trừ số nợ còn lại (`remainingAmount`).
- **Tất toán / Miễn nợ (`settle`)**: Cho phép xóa nợ / miễn nợ mà không làm lệch số dư ví.
- **Tự động tất toán**: Chuyển trạng thái sang `PAID = 2` khi số nợ còn lại $= 0$.
- **Cảnh báo quá hạn**: Tự động chuyển trạng thái `OVERDUE = 3` khi vượt quá hạn hẹn trả (`dueDate`).

### 8. 🏷️ Quản lý Thẻ Tag (Tags)
- Tạo và quản lý các thẻ tag phân loại kèm màu sắc tùy biến.
- Gắn nhiều tag vào một giao dịch hoặc liên kết tag với ngân sách chi tiêu.
- Thống kê chi tiết và tổng hợp số tiền thu/chi theo từng thẻ tag.

### 9. 💱 Tỷ giá Ngoại tệ (Currency Exchange)
- Tích hợp API cập nhật tỷ giá ngoại tệ USD/VND theo ngày.
- Cronjob tự động đồng bộ tỷ giá mới nhất hoặc kích hoạt đồng bộ thủ công (`POST /currency-exchange/sync`).
- Tự động quy đổi và hiển thị giá trị tương đương theo USD (`balanceUsd`, `amountUsd`, `spendingUsd`...) trên mọi báo cáo và phản hồi API.

### 10. 📈 Báo cáo & Thống kê (Reports & Statistics)
- **Tổng quan tài chính**: Báo cáo tổng thu, tổng chi, số dư ròng theo ngày/tháng/khoảng thời gian.
- **Báo cáo cơ cấu danh mục**: Biểu đồ phân bổ chi tiêu theo % từng nhóm danh mục.
- **Báo cáo so sánh**: So sánh tăng/giảm thu chi giữa tháng này với tháng trước.
- **Xuất báo cáo PDF**: Tự động xuất file PDF báo cáo tài chính hàng tháng/hàng năm được định dạng chuyên nghiệp với iText.

---

## 🛠️ Công nghệ sử dụng

- **Ngôn ngữ**: Java 21
- **Framework**: Spring Boot 3.5.5
  - Spring Web MVC
  - Spring Data JPA / Hibernate
  - Spring Security & OAuth2 Client
  - Spring Mail (`JavaMailSender`)
  - Spring Validation
- **Cơ sở dữ liệu**: Microsoft SQL Server
- **JWT**: `io.jsonwebtoken` (JJWT 0.11.5)
- **Object Mapping**: MapStruct 1.5.5.Final
- **Tài liệu API**: Springdoc OpenAPI / Swagger UI 2.5.0
- **Xuất PDF**: iText 5.5.13.3
- **Tiện ích**: Lombok, Gson, Jackson

---

## 🏗️ Cấu trúc dự án

```text
financial_management/
├── src/main/java/com/example/financial_management/
│   ├── config/             # Cấu hình Security, JWT AuthFilter, Swagger, Web CORS, StringToLocalDateTimeConverter
│   ├── constant/           # Hằng số: Category, TransactionType, SavingContributionType, DebtType, Status...
│   ├── controllers/        # REST Controllers (Auth, User, Account, Transaction, Budget, SavingGoal, Debt, Report...)
│   ├── cronjob/            # Tác vụ định kỳ tự động quét 00:00:00 hàng ngày (RecurringTransactionCronjob)
│   ├── entity/             # JPA Entities (User, Account, Transaction, Budget, SavingGoal, SavingGoalContribution, Debt...)
│   │   └── base/           # EntityBase (UUID), AuditEntity (createdAt, updatedAt...)
│   ├── exception/          # Custom Deserializers & Global Exception Handlers
│   ├── mapper/             # MapStruct Mappers (SavingGoalMapper, SavingGoalContributionMapper, DebtMapper...)
│   ├── model/              # DTOs Request & Response, PageResponse, AbstractResponse
│   ├── repository/         # Spring Data JPA Repositories
│   ├── services/           # Business Logic Services (SavingGoalService, DebtService, TransactionService...)
│   └── util/               # Tiện ích JwtTokenUtil
├── src/main/resources/
│   └── application.properties # Cấu hình Database, Mail SMTP, JWT Secret...
├── images/                 # Thư mục lưu trữ ảnh hóa đơn / upload
└── pom.xml                 # Maven Dependencies & Plugins
```

---

## ⚙️ Cài đặt & Cấu hình

### 1. Yêu cầu môi trường
- **JDK 21** trở lên.
- **Apache Maven 3.8+**
- **Microsoft SQL Server** (đã bật TCP/IP port 1433).

### 2. Cấu hình `application.properties`
Mở file `src/main/resources/application.properties` và cấu hình các thông số phù hợp:

```properties
spring.application.name=financial_management

# Database Connection (MS SQL Server)
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=financial_management;encrypt=true;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=your_db_password
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

# Hibernate JPA (Tự động cập nhật bảng)
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.database-platform=org.hibernate.dialect.SQLServer2012Dialect

# JWT Configuration
jwt.secret=your_very_secret_key_for_jwt_generation_minimum_256_bits
jwt.expiration=36000000

# Spring Mail (Dùng gửi email quên mật khẩu)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your_email@gmail.com
spring.mail.password=your_gmail_app_password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true

# Đường dẫn URL Frontend & Uploads
app.upload.dir=images/
email_admin=admin@example.com
app.frontend.reset-password-url=http://localhost:3000/reset-password
app.frontend.forgot-password-url=http://localhost:3000/forgot-password
app.backend.verify-url=http://localhost:8080/auth/verify-reset-token
```

---

## 🚀 Chạy ứng dụng

### 1. Build dự án bằng Maven
```bash
mvn clean install
```

### 2. Chạy ứng dụng
```bash
mvn spring-boot:run
```

Sau khi ứng dụng khởi động thành công:
- **Server URL**: `http://localhost:8080`
- **Swagger UI (Tài liệu API tương tác)**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI Docs JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 📚 Danh sách API chi tiết

Tất cả các API trả về theo chuẩn cấu trúc `AbstractResponse<T>`:
```json
{
  "data": { ... },
  "success": true,
  "code": 200,
  "message": null,
  "executionTimeInSeconds": 0.015
}
```

### 1. Authentication (`/auth`)
| Method | Endpoint | Mô tả | Quyền |
|---|---|---|---|
| `POST` | `/auth/signup` | Đăng ký tài khoản mới | Public |
| `POST` | `/auth/login` | Đăng nhập nhận JWT Token | Public |
| `POST` | `/auth/forgot-password` | Gửi email yêu cầu đặt lại mật khẩu | Public |
| `GET` | `/auth/verify-reset-token?token={token}` | Xác thực token từ link email | Public |
| `POST` | `/auth/reset-password` | Đặt mật khẩu mới với token | Public |

### 2. User Management (`/users`)
| Method | Endpoint | Mô tả | Quyền |
|---|---|---|---|
| `GET` | `/users/me` | Lấy thông tin user hiện tại | User |
| `POST` | `/users/updateProfile` | Cập nhật họ tên | User |
| `POST` | `/users/changePassword` | Đổi mật khẩu | User |
| `GET` | `/users/listUser` | Danh sách tất cả người dùng | Admin |
| `POST` | `/users/changeStatus` | Bật / Khóa tài khoản người dùng | Admin |

### 3. Accounts (`/accounts`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/accounts/all` | Lấy danh sách tất cả tài khoản/ví |
| `GET` | `/accounts/{id}` | Lấy chi tiết tài khoản |
| `POST` | `/accounts/create` | Tạo mới tài khoản/ví |
| `POST` | `/accounts/{id}` | Cập nhật thông tin tài khoản |
| `POST` | `/accounts/{id}/status?status={status}` | Đổi trạng thái tài khoản (`1: ACTIVE`, `2: INACTIVE`) |
| `DELETE`| `/accounts/{id}` | Xóa tài khoản (chỉ xóa khi chưa có giao dịch) |

### 4. Transactions (`/transactions`)
| Method | Endpoint | Content-Type | Mô tả |
|---|---|---|---|
| `GET` | `/transactions/all` | - | Lấy toàn bộ lịch sử giao dịch |
| `GET` | `/transactions/all-with-pages?page=1&size=20` | - | Lấy danh sách giao dịch có phân trang |
| `GET` | `/transactions/{id}` | - | Xem chi tiết 1 giao dịch |
| `GET` | `/transactions/{accountId}/all?page=1&size=20` | - | Lấy giao dịch theo từng tài khoản (phân trang) |
| `GET` | `/transactions/{accountId}/recent` | - | **Lấy 6 giao dịch gần nhất** của tài khoản cụ thể |
| `GET` | `/transactions/by-category-and-month?category={c}&monthYear=MM/yyyy` | - | Lọc chi tiêu theo danh mục và tháng |
| `POST` | `/transactions/create` | `multipart/form-data` | Tạo giao dịch thu/chi (hỗ trợ upload ảnh `file`, ngày giờ `createAt`, gắn `tags`) |
| `POST` | `/transactions/{id}` | `multipart/form-data` | Cập nhật toàn bộ thông tin giao dịch (số tiền, danh mục, ví, ngày giờ, ảnh, `tags`) |
| `POST` | `/transactions/transfer` | `application/json` | Chuyển tiền giữa 2 tài khoản |
| `POST` | `/transactions/filter` | `application/json` | Lọc giao dịch nâng cao đa tiêu chí có phân trang |
| `DELETE`| `/transactions/{id}` | - | Xóa giao dịch (tự động hoàn tác số dư ví) |

### 5. Tags Management (`/tags`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/tags` hoặc `/tags/all` | Lấy toàn bộ danh sách thẻ tag của người dùng |
| `POST` | `/tags` hoặc `/tags/create` | Tạo mới thẻ tag (tên thẻ, mã màu sắc) |
| `PUT` | `/tags/{id}` | Cập nhật thông tin thẻ tag |
| `DELETE`| `/tags/{id}` | Xóa thẻ tag |
| `GET` | `/tags/summary` | Bảng tổng kết chi tiêu / thu nhập theo từng thẻ tag |
| `GET` | `/tags/{id}/summary` | Thống kê chi tiết thu chi của 1 thẻ tag cụ thể |

### 6. Budgets (`/budgets`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/budgets/all` | Lấy danh sách tất cả ngân sách |
| `GET` | `/budgets/{id}` | Lấy chi tiết 1 ngân sách |
| `GET` | `/budgets/checking?month={month}&year={year}` | Kiểm tra tình hình chi tiêu so với ngân sách |
| `POST` | `/budgets/create` | Thiết lập ngân sách mới |
| `POST` | `/budgets/update?budgetId={budgetId}` | Cập nhật ngân sách |
| `POST` | `/budgets/delete?budgetId={budgetId}` | Xóa ngân sách |

### 7. Recurring Transactions (`/recurring-transactions`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/recurring-transactions` | Lấy danh sách quy tắc định kỳ (lọc theo `status`) |
| `GET` | `/recurring-transactions/{id}` | Xem chi tiết 1 quy tắc lặp lại |
| `POST` | `/recurring-transactions` | Tạo mới giao dịch định kỳ |
| `POST` | `/recurring-transactions/{id}` | Cập nhật chu kỳ, ngày lặp, số tiền |
| `POST`| `/recurring-transactions/{id}/status?status={1\|2}`| Bật (`ACTIVE = 1`) / Tạm dừng (`INACTIVE = 2`) |
| `POST` | `/recurring-transactions/{id}/execute-now`| Ghi nhận giao dịch ngay lập tức theo quy tắc này |
| `DELETE`| `/recurring-transactions/{id}` | Xóa quy tắc định kỳ |

### 8. Saving Goals & Contributions (`/saving-goals`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/saving-goals` | Lấy danh sách mục tiêu tiết kiệm (lọc theo `status`) |
| `GET` | `/saving-goals/{id}` | Xem chi tiết mục tiêu, tiến độ `%` và toàn bộ lịch sử góp quỹ (`contributions`) |
| `POST` | `/saving-goals` | Tạo mục tiêu mới (khởi tạo số tiền ban đầu $\ge 0$) |
| `POST` | `/saving-goals/{id}` | Cập nhật thông tin mục tiêu (tên, số tiền đích, hạn chót, màu sắc) |
| `POST` | `/saving-goals/{id}/deposit` | Nạp tiền vào quỹ (tự động cập nhật `COMPLETED` khi đạt $\ge 100\%$) |
| `POST` | `/saving-goals/{id}/withdraw`| Rút tiền từ quỹ về tài khoản ví/ngân hàng |
| `GET` | `/saving-goals/{id}/contributions` | Lấy danh sách toàn bộ lịch sử nạp/rút tiền của mục tiêu |
| `POST` | `/saving-goals/{id}/contributions` | Thêm bản ghi đóng góp hoặc rút quỹ trực tiếp |
| `DELETE`| `/saving-goals/{id}/contributions/{contributionId}` | Hủy 1 lần đóng góp (tự động hoàn tác số dư mục tiêu & tài khoản ví) |
| `DELETE`| `/saving-goals/{id}` | Xóa mục tiêu tiết kiệm và toàn bộ lịch sử đóng góp liên quan |

### 9. Debt Management - Sổ nợ (`/debts`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/debts` | Lấy danh sách khoản nợ (lọc theo `type`: 1-Đi vay, 2-Cho vay và `status`) |
| `GET` | `/debts/{id}` | Xem chi tiết 1 khoản nợ + lịch sử các lần trả (`payments`) |
| `POST` | `/debts` | Tạo mới khoản nợ (tùy chọn trích/nhận tiền từ ví `accountId`) |
| `POST` | `/debts/{id}` | Sửa thông tin khoản nợ (tên, sđt, ngày hẹn trả, ghi chú) |
| `POST` | `/debts/{id}/settle?reason={reason}` | Tất toán / Miễn nợ khoản nợ |
| `POST` | `/debts/{id}/payments` | Ghi nhận 1 lần trả/thu nợ (tự động đổi `PAID` khi hết nợ) |
| `DELETE`| `/debts/{id}/payments/{paymentId}` | Hủy 1 lần trả tiền (hoàn tác số dư nợ & ví) |
| `DELETE`| `/debts/{id}` | Xóa khoản nợ khỏi hệ thống (chỉ xóa khi đã thanh toán hết) |

### 10. Currency Exchange (`/currency-exchange`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/currency-exchange/latest` | Lấy tỷ giá USD/VND hiện hành đang áp dụng |
| `GET` | `/currency-exchange/history` | Lấy danh sách lịch sử tỷ giá USD/VND theo ngày |
| `POST` | `/currency-exchange/sync` | Kích hoạt đồng bộ tỷ giá ngoại tệ trực tuyến thủ công |

### 11. Reports & Analytics (`/reports`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/reports/chart?startDate={yyMMdd}&endDate={yyMMdd}` | Lấy dữ liệu biểu đồ theo khoảng ngày |
| `GET` | `/reports/account/{accountId}` | Thống kê số dư đầu kỳ, cuối kỳ của tài khoản |
| `GET` | `/reports/distribution/{accountId}` | Phân bổ thu chi theo danh mục của tài khoản |
| `POST` | `/reports/summary` | Tổng kết tổng thu, tổng chi, số dư ròng |
| `POST` | `/reports/daily` | Báo cáo chi tiết thu chi từng ngày trong tháng |
| `POST` | `/reports/monthly` | Báo cáo thu chi 12 tháng trong năm |
| `POST` | `/reports/category` | Báo cáo phân loại theo danh mục |
| `POST` | `/reports/compare` | So sánh tình hình tài chính tháng này vs tháng trước |
| `POST` | `/reports/export/month` | **Xuất file PDF** báo cáo tháng |
| `POST` | `/reports/export/year` | **Xuất file PDF** báo cáo năm |

---

## 🔢 Bảng tra cứu Hằng số (Constants Reference)

### 1. Phân loại Danh mục (`Category`)
| ID | Tên danh mục | Loại |
|---|---|---|
| `1` | Food (Ăn uống) | Expense |
| `2` | Transport (Di chuyển) | Expense |
| `3` | Entertainment (Giải trí) | Expense |
| `4` | Utilities (Hóa đơn / Tiện ích) | Expense |
| `5` | Healthcare (Y tế / Sức khỏe) | Expense |
| `6` | Education (Giáo dục) | Expense |
| `7` | Shopping (Mua sắm) | Expense |
| `8` | Housing (Nhà ở) | Expense |
| `9` | Debt (Trả nợ) | Expense |
| `10`| Other Expense (Chi tiêu khác) | Expense |
| `18`| Traveling (Du lịch) | Expense |
| `11`| Salary (Tiền lương) | Income |
| `12`| Business (Kinh doanh) | Income |
| `13`| Investments (Đầu tư) | Income |
| `14`| Gifts (Quà tặng) | Income |
| `15`| Other Income (Thu nhập khác) | Income |
| `16`| Transfer (Chuyển khoản) | Transfer |
| `17`| Savings (Tiết kiệm / Góp quỹ) | Savings |

### 2. Loại Giao dịch (`TransactionType`)
| ID | Mã loại | Mô tả |
|---|---|---|
| `0` | `EXPENSE` | Chi tiêu (Trừ tiền từ ví) |
| `1` | `INCOME` | Thu nhập (Cộng tiền vào ví) |
| `2` | `TRANSFER` | Chuyển khoản nội bộ giữa 2 ví |

### 3. Trạng thái hoạt động (`Status`)
| ID | Mã trạng thái | Mô tả | Áp dụng cho |
|---|---|---|---|
| `1` | `ACTIVE` | Hoạt động / Kích hoạt | Tài khoản, Người dùng, Giao dịch định kỳ |
| `2` | `INACTIVE` | Tạm dừng / Vô hiệu hóa | Tài khoản, Người dùng, Giao dịch định kỳ |

### 4. Vai trò người dùng (`Role`)
| ID | Mã vai trò | Mô tả |
|---|---|---|
| `1` | `ADMIN` | Quản trị viên hệ thống |
| `2` | `USER` | Người dùng thông thường |

### 5. Loại Tài khoản (`AccountType`)
| ID | Mã loại | Mô tả |
|---|---|---|
| `1` | `CASH` | Tiền mặt |
| `2` | `BANK` | Tài khoản ngân hàng |
| `3` | `CREDIT_CARD` | Thẻ tín dụng |
| `4` | `E_WALLET` | Ví điện tử (Momo, ZaloPay...) |
| `5` | `INVESTMENT` | Tài khoản đầu tư (Chứng khoán, Vàng...) |
| `6` | `SAVINGS` | Sổ tiết kiệm |
| `7` | `OTHER` | Khác |

### 6. Tiền tệ (`Currency`)
| ID | Mã tiền tệ |
|---|---|
| `0` | `USD` |
| `1` | `VND` |

### 7. Sổ nợ (`DebtType` & `DebtStatus`)
* **Loại nợ (`DebtType`)**:
  - `1`: `BORROW` (Đi vay - Nợ phải trả)
  - `2`: `LEND` (Cho vay - Nợ phải thu)
* **Trạng thái nợ (`DebtStatus`)**:
  - `1`: `IN_PROGRESS` (Đang nợ / Chưa thanh toán xong)
  - `2`: `PAID` (Đã tất toán / Đã trả xong)
  - `3`: `OVERDUE` (Quá hạn thanh toán)

### 8. Mục tiêu tiết kiệm (`SavingGoalStatus` & `SavingContributionType`)
* **Trạng thái mục tiêu (`SavingGoalStatus`)**:
  - `1`: `IN_PROGRESS` (Đang thực hiện)
  - `2`: `COMPLETED` (Đã hoàn thành đạt $\ge 100\%$)
  - `3`: `CANCELLED` (Đã hủy)
* **Loại giao dịch góp quỹ (`SavingContributionType`)**:
  - `1`: `DEPOSIT` (Nạp tiền / Góp quỹ)
  - `2`: `WITHDRAW` (Rút tiền từ quỹ)

### 9. Chu kỳ Giao dịch định kỳ (`RecurrenceType`)
| ID | Chu kỳ | Mô tả |
|---|---|---|
| `1` | `DAILY` | Hàng ngày |
| `2` | `WEEKLY` | Hàng tuần |
| `3` | `MONTHLY` | Hàng tháng |
| `4` | `YEARLY` | Hàng năm |

---

## 🔐 Bảo mật & Toàn vẹn Dữ liệu

- **JWT Authentication**: Các endpoint được bảo vệ bởi Spring Security. Gửi token qua header:
  ```http
  Authorization: Bearer <your_jwt_token>
  ```
- **Validation**: Mọi DTO đều được kiểm tra chặt chẽ bằng Jakarta Validation (`@NotNull`, `@NotBlank`, `@DecimalMin`...).
- **Data Integrity**: Các thao tác cập nhật số dư, nạp/rút quỹ, chuyển tiền được bọc trong `@Transactional` để đảm bảo tính nhất quán (ACID).
- **Protected History**: Giao dịch sinh ra từ Quản lý nợ và Mục tiêu tiết kiệm được bảo vệ, chỉ có thể chỉnh sửa hoặc hủy từ module gốc để tránh mất cân bằng số dư.
