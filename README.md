# 💰 Financial Management System (Hệ thống Quản lý Tài chính Cá nhân)

Hệ thống Backend xây dựng trên nền tảng **Spring Boot 3** và **Java 21**, cung cấp trọn bộ RESTful API cho ứng dụng quản lý tài chính cá nhân, theo dõi chi tiêu, ngân sách, mục tiêu tiết kiệm và phân tích báo cáo tài chính chuyên sâu.

---

## 📑 Mục lục
- [✨ Tính năng chính](#-tính-năng-chính)
- [🛠️ Công nghệ sử dụng](#️-công-nghệ-sử-dụng)
- [🏗️ Cấu trúc dự án](#️-cấu-trúc-dự-án)
- [⚙️ Cài đặt & Cấu hình](#️-cài-đặt--cấu-hình)
- [🚀 Chạy ứng dụng](#-chạy-ứng-dụng)
- [📚 Danh sách API](#-danh-sách-api)
- [🔐 Bảo mật & Xác thực](#-bảo-mật--xác-thực)

---

## ✨ Tính năng chính

### 1. 👤 Quản lý Người dùng & Xác thực (Auth & User)
- **Đăng ký / Đăng nhập**: Xác thực qua JWT Token, phân quyền `USER` và `ADMIN`.
- **Mật khẩu an toàn**: Mã hóa mật khẩu nhiều lớp bằng BCrypt + Custom Salt.
- **Quên / Đặt lại mật khẩu**: Gửi email chứa link xác thực token tạm thời (15 phút) qua Gmail SMTP.
- **Quản trị viên (Admin)**: Xem danh sách người dùng, kích hoạt / khóa tài khoản (`ACTIVE` / `INACTIVE`).

### 2. 💳 Quản lý Tài khoản / Ví (Accounts)
- Quản lý đa dạng các loại tài khoản: Ví tiền mặt (`CASH`), Ngân hàng (`BANK`), Thẻ tín dụng (`CREDIT_CARD`), Ví điện tử (`E_WALLET`), Tài khoản đầu tư (`INVESTMENT`), Sổ tiết kiệm (`SAVINGS`).
- Theo dõi số dư tự động cập nhật theo từng giao dịch thu/chi/chuyển tiền.

### 3. 💸 Quản lý Giao dịch (Transactions)
- **Thu / Chi / Chuyển khoản**: Ghi chép các khoản Thu (`INCOME`), Chi (`EXPENSE`), và Chuyển tiền giữa các tài khoản (`TRANSFER`).
- **Phân loại danh mục (Category)**: Ăn uống, Di chuyển, Mua sắm, Hóa đơn, Tiền lương, Đầu tư...
- **Đính kèm hóa đơn**: Hỗ trợ tải lên ảnh hóa đơn / chứng từ thanh toán (Multipart File Upload).
- **Bộ lọc & Phân trang**: Tìm kiếm và lọc giao dịch theo ngày, danh mục, tài khoản, khoảng tiền với Spring JPA Specification.

### 4. 📊 Ngân sách Chi tiêu (Budgets)
- Thiết lập hạn mức chi tiêu theo từng danh mục theo tháng/năm.
- API đối soát cảnh báo ngân sách: Tự động so sánh ngân sách đã đặt với số tiền thực chi để kiểm soát bội chi.

### 5. 🔄 Giao dịch Định kỳ (Recurring Transactions)
- Tự động hóa các khoản thu/chi lặp lại theo chu kỳ: Hàng ngày (`DAILY`), Hàng tuần (`WEEKLY`), Hàng tháng (`MONTHLY`), Hàng năm (`YEARLY`).
- Tự động tính toán `nextExecutionDate` và hỗ trợ Cronjob tự động ghi nhận giao dịch vào ngày đến hạn.
- Cho phép "Ghi nhận ngay" (`execute-now`) hoặc Bật / Tạm dừng quy tắc.

### 6. 🎯 Mục tiêu Tiết kiệm (Saving Goals)
- Khởi tạo các quỹ mục tiêu (Mua xe, Du lịch, Quỹ khẩn cấp...).
- **Nạp tiền / Góp quỹ (`deposit`)**: Tự động trích tiền từ tài khoản nguồn và ghi nhận sao kê nội bộ `TRANSFER`.
- **Rút tiền (`withdraw`)**: Rút tiền từ quỹ tiết kiệm hoàn về tài khoản ví/ngân hàng.
- Theo dõi tiến độ tích lũy `%` và tự động cập nhật trạng thái `COMPLETED` khi đạt $\ge 100\%$.

### 7. 🤝 Quản lý Nợ - Sổ nợ (Debt Management)
- Theo dõi các khoản **Đi vay (`BORROW` - Nợ phải trả)** và **Cho vay (`LEND` - Nợ phải thu)**.
- Ghi nhận lịch sử từng lần trả bớt / thu nợ (`DebtPayment`), tự động trừ số nợ còn lại (`remainingAmount`).
- **Tự động tất toán**: Chuyển trạng thái sang `PAID` khi số nợ còn lại $= 0$.
- **Cảnh báo quá hạn**: Tự động chuyển trạng thái `OVERDUE` khi vượt quá hạn hẹn trả (`dueDate`).
- Đồng bộ biến động số dư tài khoản ví/ngân hàng khi vay, cho vay, trả nợ và thu nợ.

### 8. 📈 Báo cáo & Thống kê (Reports & Statistics)
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
│   ├── config/             # Cấu hình Security, JWT AuthFilter, Swagger, Web CORS
│   ├── constant/           # Các hằng số: Category, TransactionType, Role, Status, RecurrenceType...
│   ├── controllers/        # REST Controllers (Auth, User, Account, Transaction, Budget, Goal, Report...)
│   ├── cronjob/            # Các tác vụ định kỳ tự động (RecurringTransactionCronjob)
│   ├── entity/             # Các JPA Entities (User, Account, Transaction, Budget, SavingGoal...)
│   │   └── base/           # EntityBase (UUID), AuditEntity (createdAt, updatedAt...)
│   ├── exception/          # Custom Deserializer & Exception Handlers
│   ├── mapper/             # MapStruct Mappers (UserMapper, TransactionMapper, SavingGoalMapper...)
│   ├── model/              # DTOs Request & Response, PageResponse, AbstractResponse
│   ├── repository/         # Spring Data JPA Repositories
│   ├── services/           # Business Logic Services (UserService, TransactionService, EmailService...)
│   └── util/               # Tiện ích JwtTokenUtil
├── src/main/resources/
│   └── application.properties # File cấu hình Database, Mail SMTP, JWT Secret...
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

## 📚 Danh sách API

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
| `GET` | `/auth/verify-reset-token` | Xác thực token từ link email & chuyển hướng | Public |
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
| `POST` | `/accounts/{id}/status` | Đổi trạng thái tài khoản |
| `DELETE`| `/accounts/{id}` | Xóa tài khoản |

### 4. Transactions (`/transactions`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/transactions/all` | Lấy toàn bộ lịch sử giao dịch |
| `GET` | `/transactions/all-with-pages` | Lấy danh sách giao dịch có phân trang |
| `GET` | `/transactions/{id}` | Xem chi tiết 1 giao dịch |
| `GET` | `/transactions/{accountId}/all` | Lấy giao dịch theo từng tài khoản |
| `POST` | `/transactions/create` | Tạo giao dịch thu/chi (hỗ trợ upload ảnh) |
| `POST` | `/transactions/{id}` | Cập nhật giao dịch (hỗ trợ đổi ảnh/số dư) |
| `POST` | `/transactions/transfer` | Chuyển tiền giữa 2 tài khoản |
| `POST` | `/transactions/filter` | Lọc giao dịch nâng cao đa tiêu chí |
| `DELETE`| `/transactions/{id}` | Xóa giao dịch (tự động rollback số dư) |

### 5. Budgets (`/budgets`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/budgets/all` | Lấy danh sách ngân sách |
| `GET` | `/budgets/{id}` | Lấy chi tiết 1 ngân sách |
| `POST` | `/budgets/create` | Thiết lập ngân sách mới |
| `POST` | `/budgets/{id}` | Cập nhật ngân sách |
| `POST` | `/budgets/checking` | Kiểm tra tình hình chi tiêu so với ngân sách |
| `DELETE`| `/budgets/{id}` | Xóa ngân sách |

### 6. Recurring Transactions (`/recurring-transactions`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/recurring-transactions` | Lấy danh sách quy tắc định kỳ (lọc `status`) |
| `GET` | `/recurring-transactions/{id}` | Xem chi tiết 1 quy tắc lặp lại |
| `POST` | `/recurring-transactions` | Tạo mới giao dịch định kỳ |
| `POST` | `/recurring-transactions/{id}` | Cập nhật chu kỳ, ngày lặp, số tiền |
| `POST`| `/recurring-transactions/{id}/status`| Bật (`ACTIVE = 1`) / Tạm dừng (`INACTIVE = 2`) |
| `POST` | `/recurring-transactions/{id}/execute-now`| Ghi nhận giao dịch ngay lập tức |
| `DELETE`| `/recurring-transactions/{id}` | Xóa quy tắc định kỳ |

### 7. Saving Goals (`/saving-goals`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/saving-goals` | Lấy danh sách mục tiêu tiết kiệm |
| `GET` | `/saving-goals/{id}` | Xem chi tiết mục tiêu & tiến độ `%` |
| `POST` | `/saving-goals` | Tạo mục tiêu mới |
| `POST` | `/saving-goals/{id}` | Cập nhật mục tiêu |
| `POST` | `/saving-goals/{id}/deposit` | Nạp tiền vào quỹ (tự động đạt 100% khi đủ) |
| `POST` | `/saving-goals/{id}/withdraw`| Rút tiền từ quỹ về tài khoản/ví |
| `DELETE`| `/saving-goals/{id}` | Xóa mục tiêu |

### 8. Debt Management - Sổ nợ (`/debts`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/debts` | Lấy danh sách khoản nợ (lọc theo `type`: 1-Đi vay, 2-Cho vay và `status`) |
| `GET` | `/debts/{id}` | Xem chi tiết 1 khoản nợ + lịch sử các lần trả |
| `POST` | `/debts` | Tạo mới khoản nợ (tùy chọn trích/nhận tiền từ ví `accountId`) |
| `POST` | `/debts/{id}` | Sửa thông tin khoản nợ (tên, sđt, ngày hẹn trả, ghi chú) |
| `POST` | `/debts/{id}/payments` | Ghi nhận 1 lần trả/thu nợ (tự động đổi `PAID` khi hết nợ) |
| `DELETE`| `/debts/{id}/payments/{paymentId}` | Hủy 1 lần trả tiền (hoàn tác số dư nợ & ví) |
| `DELETE`| `/debts/{id}` | Xóa khoản nợ và toàn bộ lịch sử thanh toán liên quan |

### 9. Reports & Analytics (`/reports`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/reports/chart` | Lấy dữ liệu biểu đồ theo khoảng ngày |
| `GET` | `/reports/account/{accountId}` | Thống kê số dư đầu kỳ, cuối kỳ của tài khoản |
| `GET` | `/reports/distribution/{accountId}` | Phân bổ thu chi theo danh mục |
| `POST` | `/reports/summary` | Tổng kết tổng thu, tổng chi, số dư ròng |
| `POST` | `/reports/daily` | Báo cáo chi tiết thu chi từng ngày trong tháng |
| `POST` | `/reports/monthly` | Báo cáo thu chi 12 tháng trong năm |
| `POST` | `/reports/category` | Báo cáo phân loại theo danh mục |
| `POST` | `/reports/compare` | So sánh tình hình tài chính tháng này vs tháng trước |
| `POST` | `/reports/export/month` | **Xuất file PDF** báo cáo tháng |
| `POST` | `/reports/export/year` | **Xuất file PDF** báo cáo năm |

---

## 🔐 Bảo mật & Xác thực

- Các endpoint được bảo vệ bởi **Spring Security Filter Chain** và **JWT Authentication Filter**.
- Khi gửi request đến các API cần xác thực, truyền JWT Token trong header:
  ```http
  Authorization: Bearer <your_jwt_token>
  ```
- **Xác thực dữ liệu (Validation)**: Mọi DTO đều được kiểm tra chặt chẽ bằng Jakarta Validation (`@NotNull`, `@NotBlank`, `@Size`...).
- **Toàn vẹn dữ liệu (Transaction Management)**: Các thao tác liên quan đến tiền tệ, cập nhật số dư, nạp/rút đều được bọc trong `@Transactional` để đảm bảo tính nhất quán (ACID).
