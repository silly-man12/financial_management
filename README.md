# 💰 Financial Management System (Hệ thống Quản lý Tài chính Cá nhân)

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Database](https://img.shields.io/badge/Database-MS%20SQL%20Server-red.svg)](https://www.microsoft.com/sql-server)
[![Security](https://img.shields.io/badge/Security-JWT%20%2B%20Spring%20Security-blue.svg)](https://jwt.io/)
[![Swagger](https://img.shields.io/badge/API%20Docs-Swagger%20%2F%20OpenAPI%203-green.svg)](http://localhost:8080/swagger-ui/index.html)

Hệ thống Backend xây dựng trên nền tảng **Spring Boot 3** và **Java 21**, cung cấp giải pháp toàn diện và trọn bộ RESTful API cho bài toán quản lý tài chính cá nhân: theo dõi chi tiêu, quản lý tài khoản/ví ngân hàng, phân bổ ngân sách, lập mục tiêu tiết kiệm, sổ nợ và lịch sử thanh toán, đồng bộ tỷ giá ngoại tệ USD/VND tự động, cùng hệ thống phân tích báo cáo tài chính chuyên sâu (KPI, xu hướng dòng tiền, cơ cấu chi tiêu và xuất báo cáo PDF).

---

## 📑 Mục lục
- [✨ Tính năng chính](#-tính-năng-chính)
- [🛠️ Công nghệ sử dụng](#️-công-nghệ-sử-dụng)
- [🏗️ Cấu trúc dự án](#️-cấu-trúc-dự-án)
- [⚙️ Cài đặt & Cấu hình](#️-cài-đặt--cấu-hình)
- [🚀 Hướng dẫn Chạy ứng dụng](#-hướng-dẫn-chạy-ứng-dụng)
- [📚 Danh sách RESTful API](#-danh-sách-restful-api)
- [🔢 Bảng tra cứu Hằng số (Constants Reference)](#-bảng-tra-cứu-hằng-số-constants-reference)
- [📊 Quy tắc Tính toán & Toàn vẹn Dữ liệu](#-quy-tắc-tính-toán--toàn-vẹn-dữ-liệu)
- [🔐 Cơ chế Bảo mật & Xác thực](#-cơ-chế-bảo-mật--xác-thực)

---

## ✨ Tính năng chính

### 1. 👤 Quản lý Người dùng & Xác thực (Auth & Users)
- **Đăng ký / Đăng nhập**: Xác thực không trạng thái (stateless) qua JWT Token, phân quyền người dùng `USER (2)` và `ADMIN (1)`.
- **Mật khẩu an toàn**: Mã hóa mật khẩu nhiều lớp bằng BCrypt kết hợp Custom Salt độc lập.
- **Quên / Đặt lại mật khẩu**: Gửi email chứa liên kết token tạm thời (hạn 15 phút) qua Gmail SMTP, hỗ trợ xác thực mã token và đặt mật khẩu mới.
- **Quản trị người dùng (Admin)**: Danh sách toàn bộ tài khoản người dùng, chuyển đổi trạng thái Hoạt động / Khóa (`ACTIVE = 1` / `INACTIVE = 2`).

### 2. 💳 Quản lý Tài khoản & Ví (Accounts)
- Hỗ trợ đa dạng các loại tài khoản: Ví tiền mặt (`CASH`), Ngân hàng (`BANK`), Thẻ tín dụng (`CREDIT_CARD`), Ví điện tử (`E_WALLET`), Tài khoản đầu tư (`INVESTMENT`), Sổ tiết kiệm (`SAVINGS`), Khác (`OTHER`).
- Tự động cộng/trừ số dư theo thời gian thực tương ứng với từng giao dịch phát sinh (thu, chi, chuyển khoản, nạp/rút quỹ tiết kiệm, thanh toán nợ).
- **Ràng buộc an toàn**: Chặn xóa cứng tài khoản nếu đã phát sinh lịch sử giao dịch sao kê để tránh thất thoát dữ liệu sổ sách.

### 3. 💸 Quản lý Giao dịch (Transactions)
- **Thu / Chi / Chuyển khoản**:
  - Ghi nhận khoản Chi (`EXPENSE = 0`), Thu (`INCOME = 1`), Chuyển khoản giữa 2 ví (`TRANSFER = 2`).
  - Hỗ trợ chuyển tiền nội bộ giữa 2 tài khoản, tự động cập nhật số dư cả ví nguồn và ví đích.
- **Hóa đơn đính kèm & Thẻ tag**: Tải lên ảnh hóa đơn / biên lai thanh toán (`multipart/form-data`, file tối đa 20MB) và gắn nhãn thẻ tag.
- **Linh hoạt định dạng ngày giờ**: Tự động parse đa định dạng thời gian (`yyyy-MM-dd'T'HH:mm:ss`, `yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd`, ISO OffsetDateTime).
- **Lọc & Phân trang**: Tìm kiếm giao dịch nâng cao theo tài khoản, danh mục, khoảng tiền, thời gian thông qua Spring Data JPA Specification.
- **API tiện ích widget**: Tra cứu nhanh 6 giao dịch gần nhất theo từng tài khoản (`GET /{accountId}/recent`).
- **Hoàn tác tự động**: Khi xóa giao dịch, hệ thống tự động hoàn tác (revert) số dư về tài khoản tương ứng.

### 4. 🏷️ Quản lý Thẻ Tag (Tags)
- Khởi tạo và quản lý nhãn dán tùy chỉnh kèm mã màu sắc hiển thị.
- Gắn nhiều thẻ tag vào một giao dịch thu chi.
- Báo cáo và thống kê tổng hợp số tiền thu/chi theo từng thẻ tag cụ thể (`/tags/summary`).

### 5. 📊 Ngân sách Chi tiêu (Budgets)
- Thiết lập hạn mức chi tiêu theo từng danh mục cho tháng/năm.
- API đối soát ngân sách thông minh (`/budgets/checking`): Tự động tính toán số tiền đã chi, số dư còn lại và tỷ lệ phần trăm đã tiêu so với hạn mức để cảnh báo bội chi.

### 6. 🔄 Giao dịch Định kỳ (Recurring Transactions)
- Tự động hóa các khoản chi định kỳ (tiền nhà, hóa đơn điện nước, netflix...) hoặc thu định kỳ (lương, cổ tức...).
- Chu kỳ hỗ trợ: Hàng ngày (`DAILY`), Hàng tuần (`WEEKLY`), Hàng tháng (`MONTHLY`), Hàng năm (`YEARLY`).
- **Cronjob tự động**: Quét lúc `00:00:00` mỗi ngày để tự động tạo giao dịch khi tới hạn (`nextExecutionDate`).
- Hỗ trợ kích hoạt thủ công tức thì (`POST /{id}/execute-now`) hoặc Bật / Tạm dừng quy tắc (`status: 1/2`).

### 7. 🎯 Mục tiêu Tiết kiệm (Saving Goals & Contributions)
- Lập kế hoạch tài chính cho các mục tiêu (Mua nhà, Mua xe, Du lịch, Quỹ khẩn cấp...).
- **Lịch sử đóng góp chi tiết (`SavingGoalContribution`)**:
  - Ghi vết từng lần Nạp tiền (`DEPOSIT = 1`) hoặc Rút tiền (`WITHDRAW = 2`).
  - Tự động trích hoặc hoàn tiền về tài khoản ngân hàng / ví liên kết.
  - Tự động đổi trạng thái sang Hoàn thành (`COMPLETED = 2`) khi tiến độ đạt $\ge 100\%$.
  - Hỗ trợ hoàn tác một lần đóng góp, tự động khôi phục số dư ví và số tiền tích lũy của mục tiêu.

### 8. 🤝 Sổ nợ & Quản lý Nợ (Debt Management)
- Phân loại rõ ràng: **Đi vay (`BORROW = 1` - Nợ phải trả)** và **Cho vay (`LEND = 2` - Nợ phải thu)**.
- Quản lý số nợ gốc (`amount`) và số nợ còn lại (`remainingAmount`).
- Ghi nhận lịch sử từng đợt trả bớt / thu nợ (`DebtPayment`), tự động trừ số nợ còn lại và cập nhật ví tiền.
- **Tất toán / Miễn nợ (`POST /{id}/settle`)**: Cho phép miễn nợ hoặc chốt sổ mà không gây lệch số dư ví.
- **Tự động chuyển trạng thái**: Đổi sang `PAID = 2` khi nợ về 0, hoặc `OVERDUE = 3` khi quá hạn hẹn trả (`dueDate`).

### 9. 💱 Tỷ giá Ngoại tệ (Currency Exchange)
- Tích hợp dịch vụ theo dõi tỷ giá ngoại tệ USD/VND.
- Cronjob chạy định kỳ hàng ngày đồng bộ tỷ giá trực tuyến vào cơ sở dữ liệu.
- Cho phép kích hoạt đồng bộ thủ công qua API (`POST /currency-exchange/sync`).
- Tự động quy đổi và cung cấp số liệu USD tương ứng trên các báo cáo thống kê (`balanceUsd`, `amountUsd`...).

### 10. 📈 Báo cáo & Phân tích Tài chính Chuyên sâu (Reports & Analytics)
- **Analytics KPI Dashboard (`/reports/analytics`)**:
  - Trả về 8 chỉ số tài chính chủ chốt: Tổng thu nhập, Tổng chi tiêu, Dòng tiền ròng (Net Savings), Tỷ lệ tiết kiệm (Savings Rate %), Chi tiêu trung bình/ngày, kèm các giá trị quy đổi USD.
  - Chuỗi dữ liệu xu hướng dòng tiền theo ngày (`chartPoints`) phục vụ vẽ biểu đồ đường/cột trực quan.
- **Cơ cấu & Phân bổ danh mục (`/reports/category-distribution`)**:
  - Phân tích chi tiết tỷ trọng %, số lượt giao dịch và mức tăng trưởng của từng nhóm chi tiêu hoặc thu nhập.
- **Dòng tiền theo ví tài khoản (`/reports/account-flow`)**:
  - Thống kê chi tiết tiền vào (Inflow), tiền ra (Outflow), dòng tiền ròng và số dư hiện tại của từng ví.
- **Top chi tiêu lớn nhất (`/reports/top-expenses`)**:
  - Liệt kê các khoản chi tiêu có giá trị lớn nhất trong kỳ.
- **Báo cáo định kỳ & So sánh**:
  - Báo cáo chi tiết theo ngày (`/reports/daily`), 12 tháng trong năm (`/reports/monthly`), tổng quan (`/reports/summary`), so sánh tháng này với tháng trước (`/reports/compare`).
- **Loại trừ giao dịch nội bộ**: Báo cáo tài chính tự động loại trừ Chuyển khoản nội bộ (`Category = 16`) và Trả/thu nợ (`Category = 9`) để đảm bảo số liệu thu nhập và chi phí thực tế không bị tính trùng (double counting).
- **Xuất file PDF**: Tự động tạo và xuất báo cáo tài chính dạng PDF chuyên nghiệp theo tháng và theo năm với iText.

---

## 🛠️ Công nghệ sử dụng

| Phân loại | Công nghệ / Thư viện | Phiên bản | Ghi chú |
|---|---|---|---|
| **Core Platform** | Java | **JDK 21** | Nền tảng thực thi chính |
| **Framework** | Spring Boot | **3.5.5** | Framework nền tảng backend |
| **Data & ORM** | Spring Data JPA / Hibernate | 3.5.5 | Truy vấn dữ liệu, Specification |
| **Database** | Microsoft SQL Server | 2012+ | RDBMS lưu trữ dữ liệu |
| **JDBC Driver** | `mssql-jdbc` | 12.6.1.jre11 | Driver kết nối MS SQL Server |
| **Security** | Spring Security 6 | 3.5.5 | Stateless Security Filter Chain |
| **Token Auth** | JJWT (`jjwt-api`, `jjwt-impl`) | **0.11.5** | Tạo và kiểm tra JWT Token |
| **API Docs** | Springdoc OpenAPI / Swagger UI | **2.5.0** | Tài liệu tương tác API trực quan |
| **Object Mapping** | MapStruct | **1.5.5.Final** | Chuyển đổi Entity <-> DTO hiệu năng cao |
| **PDF Generation** | iText | **5.5.13.3** | Xuất tài liệu PDF báo cáo tài chính |
| **Mail Service** | Spring Boot Starter Mail | 3.5.5 | Gửi email khôi phục mật khẩu qua SMTP |
| **Validation** | Jakarta Bean Validation | 3.5.5 | Kiểm tra tính hợp lệ của DTO request |
| **Scheduling** | Spring `@Scheduled` | 3.5.5 | Cronjobs định kỳ cho giao dịch & tỷ giá |
| **Tiện ích** | Lombok, Gson, Jackson | - | Giảm thiểu boilerplate code |

---

## 🏗️ Cấu trúc dự án

```text
financial_management/
├── financial_management.sql           # Script cấu hình Database, Foreign Keys & Cascade Triggers
├── images/                            # Thư mục lưu trữ hình ảnh hóa đơn tải lên
├── README.md                          # Tài liệu hướng dẫn dự án
└── financial_management/              # Mã nguồn dự án Maven Spring Boot
    ├── pom.xml                        # Khai báo thư viện & plugin Maven
    └── src/
        ├── main/
        │   ├── java/com/example/financial_management/
        │   │   ├── config/            # Cấu hình: Security, AuthFilter, Swagger, Web CORS, Converter
        │   │   ├── constant/          # Hằng số hệ thống: Category, TransactionType, Status, DebtType...
        │   │   ├── controllers/       # REST Controllers: Auth, User, Account, Transaction, Report...
        │   │   ├── cronjob/           # Tác vụ định kỳ: RecurringTransactionCronjob, CurrencyExchangeCronjob
        │   │   ├── entity/            # JPA Entities: User, Account, Transaction, Budget, Debt...
        │   │   │   └── base/          # Base Entity (UUID id, Audit createdAt, updatedAt...)
        │   │   ├── exception/         # Xử lý lỗi toàn cục (Global Exception Handler) & Deserializers
        │   │   ├── mapper/            # MapStruct mappers (SavingGoalMapper, DebtMapper...)
        │   │   ├── model/             # DTOs: Request/Response, PageResponse, AbstractResponse
        │   │   ├── repository/        # Spring Data JPA Repositories (TransactionRepository, ...)
        │   │   ├── services/          # Business Logic Services (TransactionService, ReportService...)
        │   │   └── util/              # Tiện ích bổ trợ (JwtTokenUtil, DateTimeUtils...)
        │   └── resources/
        │       └── application.properties # Cấu hình Database, Mail, JWT, Cổng chạy...
        └── test/                      # Unit & Integration Tests
```

---

## ⚙️ Cài đặt & Cấu hình

### 1. Yêu cầu môi trường
- **Java Development Kit (JDK)**: Phiên bản 21 trở lên.
- **Apache Maven**: Phiên bản 3.8 trở lên.
- **Microsoft SQL Server**: Phiên bản 2014 trở lên (đảm bảo dịch vụ SQL Server Browser và TCP/IP port 1433 đã được kích hoạt).

### 2. Thiết lập Cơ sở dữ liệu
1. Tạo một Database trống trên Microsoft SQL Server có tên `financial_management`.
2. Khi khởi động ứng dụng lần đầu, Hibernate sẽ tự động tạo bảng theo cấu hình `spring.jpa.hibernate.ddl-auto=update`.
3. Chạy nội dung file `financial_management.sql` trong SQL Server Management Studio (SSMS) để thiết lập các ràng buộc khóa ngoại và trigger xử lý cascade an toàn:
   - Khóa ngoại `FK_account_user`: Xóa User tự động cascade xóa Account.
   - Khóa ngoại `FK_transactions_account`: Xóa Account đặt `account_id` về `NULL`.
   - Trigger `TRG_DeleteUser`: Tự động dọn dẹp các giao dịch và tài khoản liên quan khi xóa tài khoản người dùng.

### 3. Cấu hình `application.properties`
Chỉnh sửa file `financial_management/src/main/resources/application.properties` cho phù hợp với môi trường của bạn:

```properties
spring.application.name=financial_management

# Kết nối cơ sở dữ liệu MS SQL Server
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=financial_management;encrypt=true;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=your_db_password
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

# Hibernate & JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.database-platform=org.hibernate.dialect.SQLServer2012Dialect

# Giới hạn kích thước tải lên file (ảnh hóa đơn)
spring.servlet.multipart.max-file-size=20MB
spring.servlet.multipart.max-request-size=20MB

# Cấu hình JWT Token
jwt.secret=your_very_secret_key_minimum_256_bits_length_for_hmac_sha256
jwt.expiration=36000000

# Cấu hình Email gửi thông báo / Quên mật khẩu (Gmail SMTP)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your_email@gmail.com
spring.mail.password=your_gmail_app_password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true

# Thư mục lưu file hóa đơn và đường dẫn liên kết
app.upload.dir=images/
email_admin=admin@example.com
app.reset-password.url=http://localhost:8080/auth/reset-password
app.verify-reset-password-url=http://localhost:5173/reset-password
```

---

## 🚀 Hướng dẫn Chạy ứng dụng

### 1. Di chuyển vào thư mục dự án Maven
```bash
cd "financial_management"
```

### 2. Biên dịch và Build ứng dụng
```bash
mvn clean install
```

### 3. Khởi chạy Server
```bash
mvn spring-boot:run
```

Hoặc chạy file JAR sau khi đóng gói:
```bash
java -jar target/financial_management-0.0.1-SNAPSHOT.jar
```

### 4. Kiểm tra ứng dụng
- **Cổng mặc định**: `http://localhost:8080`
- **Tài liệu Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI Schema (JSON)**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 📚 Danh sách RESTful API

Tất cả các API chuẩn hóa đều trả về theo định dạng vỏ bao bọc `AbstractResponse<T>`:
```json
{
  "data": { ... },
  "success": true,
  "code": 200,
  "message": null,
  "executionTimeInSeconds": 0.012
}
```

### 1. Xác thực & Tài khoản (`/auth`)
| Method | Endpoint | Yêu cầu quyền | Mô tả |
|---|---|---|---|
| `POST` | `/auth/signup` | Public | Đăng ký tài khoản người dùng mới |
| `POST` | `/auth/login` | Public | Đăng nhập và nhận chuỗi JWT Token |
| `POST` | `/auth/forgot-password` | Public | Yêu cầu gửi link đặt lại mật khẩu qua email |
| `GET` | `/auth/verify-reset-token?token={t}` | Public | Kiểm tra tính hợp lệ của mã đặt lại mật khẩu |
| `POST` | `/auth/reset-password` | Public | Xác nhận mật khẩu mới cùng mã xác thực |

### 2. Người dùng (`/users`)
| Method | Endpoint | Yêu cầu quyền | Mô tả |
|---|---|---|---|
| `GET` | `/users/me` | User / Admin | Xem thông tin tài khoản đang đăng nhập |
| `POST` | `/users/updateProfile` | User / Admin | Cập nhật họ tên, thông tin cá nhân |
| `POST` | `/users/changePassword` | User / Admin | Đổi mật khẩu tài khoản |
| `GET` | `/users/listUser` | Admin | Lấy danh sách toàn bộ người dùng hệ thống |
| `POST` | `/users/changeStatus` | Admin | Thay đổi trạng thái tài khoản (`1: ACTIVE`, `2: INACTIVE`) |

### 3. Tài khoản & Ví (`/accounts`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/accounts/all` | Lấy toàn bộ danh sách tài khoản/ví của người dùng |
| `GET` | `/accounts/{id}` | Lấy thông tin chi tiết một tài khoản cụ thể |
| `POST` | `/accounts/create` | Khởi tạo tài khoản/ví mới |
| `POST` | `/accounts/{id}` | Cập nhật tên, loại hoặc thông tin tài khoản |
| `POST` | `/accounts/{id}/status?status={s}` | Thay đổi trạng thái tài khoản (`1: ACTIVE`, `2: INACTIVE`) |
| `DELETE`| `/accounts/{id}` | Xóa tài khoản (chỉ cho phép xóa khi chưa có giao dịch) |

### 4. Giao dịch (`/transactions`)
| Method | Endpoint | Content-Type | Mô tả |
|---|---|---|---|
| `GET` | `/transactions/all` | - | Lấy tất cả giao dịch của người dùng |
| `GET` | `/transactions/all-with-pages?page=1&size=20` | - | Lấy danh sách giao dịch có phân trang |
| `GET` | `/transactions/{id}` | - | Xem chi tiết 1 giao dịch |
| `GET` | `/transactions/{accountId}/all?page=1&size=20` | - | Lấy danh sách giao dịch của tài khoản (phân trang) |
| `GET` | `/transactions/{accountId}/recent` | - | **Lấy 6 giao dịch gần nhất** của tài khoản cụ thể |
| `GET` | `/transactions/by-category-and-month?category={c}&monthYear=MM/yyyy` | - | Lọc chi tiêu theo mã danh mục và tháng |
| `POST` | `/transactions/create` | `multipart/form-data` | Tạo giao dịch thu/chi (hỗ trợ upload ảnh `file`, ngày giờ `createAt`, nhãn `tags`) |
| `POST` | `/transactions/{id}` | `multipart/form-data` | Cập nhật giao dịch (số tiền, danh mục, ví, ngày giờ, ảnh `file`, `tags`) |
| `POST` | `/transactions/transfer` | `application/json` | Thực hiện chuyển tiền giữa 2 tài khoản/ví |
| `POST` | `/transactions/filter` | `application/json` | Bộ lọc giao dịch nâng cao đa tiêu chí |
| `DELETE`| `/transactions/{id}` | - | Xóa giao dịch (tự động hoàn tác số dư ví tương ứng) |

### 5. Thẻ Tag (`/tags`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/tags` hoặc `/tags/all` | Lấy danh sách tất cả các thẻ tag của người dùng |
| `POST` | `/tags` hoặc `/tags/create` | Tạo mới một thẻ tag (tên thẻ, màu sắc hex code) |
| `POST` | `/tags/{id}` | Cập nhật thông tin thẻ tag |
| `POST` | `/tags/{id}` | Cập nhật thông tin thẻ tag (hỗ trợ alias qua POST) |
| `DELETE`| `/tags/{id}` | Xóa thẻ tag |
| `GET` | `/tags/summary` | Bảng tổng kết số tiền thu/chi theo từng thẻ tag |
| `GET` | `/tags/{id}/summary` | Thống kê chi tiết thu chi của một thẻ tag cụ thể |

### 6. Ngân sách (`/budgets`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/budgets/all` | Lấy danh sách tất cả ngân sách đã tạo |
| `GET` | `/budgets/{id}` | Xem chi tiết 1 ngân sách |
| `GET` | `/budgets/checking?month={m}&year={y}` | Đối soát ngân sách: số tiền đã chi, số dư còn lại và tỷ lệ |
| `POST` | `/budgets/create` | Thiết lập ngân sách mới cho danh mục |
| `POST` | `/budgets/update?budgetId={id}` | Chỉnh sửa hạn mức ngân sách |
| `POST` | `/budgets/delete?budgetId={id}` | Xóa bỏ một ngân sách |

### 7. Giao dịch Định kỳ (`/recurring-transactions`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/recurring-transactions` | Lấy danh sách các quy tắc lặp (hỗ trợ lọc theo `status`) |
| `GET` | `/recurring-transactions/{id}` | Xem chi tiết quy tắc giao dịch định kỳ |
| `POST` | `/recurring-transactions` | Khởi tạo quy tắc giao dịch lặp lại mới |
| `POST` | `/recurring-transactions/{id}` | Cập nhật chu kỳ, số tiền, ngày thực thi tiếp theo |
| `POST` | `/recurring-transactions/{id}/status?status={1\|2}` | Bật (`ACTIVE = 1`) hoặc Tạm dừng (`INACTIVE = 2`) quy tắc |
| `POST` | `/recurring-transactions/{id}/execute-now` | Ép thực thi giao dịch ngay lập tức theo quy tắc |
| `DELETE`| `/recurring-transactions/{id}` | Xóa quy tắc giao dịch định kỳ |

### 8. Mục tiêu Tiết kiệm & Đóng góp (`/saving-goals`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/saving-goals` | Danh sách mục tiêu tiết kiệm (lọc theo `status`: 1-Đang làm, 2-Xong, 3-Hủy) |
| `GET` | `/saving-goals/{id}` | Xem chi tiết mục tiêu, tiến độ `%` và toàn bộ lịch sử góp/rút quỹ |
| `POST` | `/saving-goals` | Tạo mục tiêu mới (hỗ trợ trích số tiền ban đầu từ tài khoản) |
| `POST` | `/saving-goals/{id}` | Cập nhật thông tin mục tiêu (tên, số tiền đích, deadline, màu sắc) |
| `POST` | `/saving-goals/{id}/deposit` | Nạp tiền vào quỹ tiết kiệm (tự động đổi `COMPLETED` khi $\ge 100\%$) |
| `POST` | `/saving-goals/{id}/withdraw`| Rút tiền từ quỹ tiết kiệm về tài khoản/ví |
| `GET` | `/saving-goals/{id}/contributions` | Xem toàn bộ lịch sử giao dịch nạp/rút của mục tiêu |
| `POST` | `/saving-goals/{id}/contributions` | Ghi nhận bản ghi góp hoặc rút tiền trực tiếp |
| `DELETE`| `/saving-goals/{id}/contributions/{contributionId}` | Hủy 1 lần đóng góp (tự động hoàn tác số dư mục tiêu và số dư ví) |
| `DELETE`| `/saving-goals/{id}` | Xóa toàn bộ mục tiêu tiết kiệm và toàn bộ lịch sử nạp/rút |

### 9. Quản lý Nợ - Sổ nợ (`/debts`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/debts` | Lấy danh sách khoản nợ (lọc theo `type`: 1-Đi vay, 2-Cho vay và `status`) |
| `GET` | `/debts/{id}` | Xem chi tiết khoản nợ kèm toàn bộ lịch sử các lần thanh toán |
| `POST` | `/debts` | Tạo khoản nợ mới (tùy chọn trích tiền từ tài khoản hoặc nhận tiền vào ví) |
| `POST` | `/debts/{id}` | Chỉnh sửa thông tin đối tác, số điện thoại, hạn trả, ghi chú |
| `POST` | `/debts/{id}/settle?reason={r}` | Tất toán / Miễn nợ (xóa số nợ còn lại về 0 mà không làm lệch số dư ví) |
| `POST` | `/debts/{id}/payments` | Ghi nhận 1 lần trả nợ/thu nợ (tự động đổi sang `PAID` khi số nợ còn lại $= 0$) |
| `DELETE`| `/debts/{id}/payments/{paymentId}` | Hủy 1 đợt thanh toán (hoàn tác số nợ và số dư tài khoản ví) |
| `DELETE`| `/debts/{id}` | Xóa khoản nợ khỏi hệ thống (chỉ xóa khi đã thanh toán xong hoặc tất toán) |

### 10. Tỷ giá Ngoại tệ (`/currency-exchange`)
| Method | Endpoint | Yêu cầu quyền | Mô tả |
|---|---|---|---|
| `GET` | `/currency-exchange/latest` | Public | Lấy tỷ giá USD/VND hiện hành đang áp dụng trong hệ thống |
| `GET` | `/currency-exchange/history` | Public | Lấy danh sách lịch sử tỷ giá USD/VND theo thứ tự ngày mới nhất |
| `POST` | `/currency-exchange/sync` | Authenticated | Đồng bộ tỷ giá trực tuyến ngay lập tức vào cơ sở dữ liệu |

### 11. Báo cáo & Thống kê Tài chính (`/reports`)
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/reports/analytics` | **Báo cáo KPI & Xu hướng dòng tiền**: Trả về 8 chỉ số KPI tài chính (thu, chi, net, savings rate, quy đổi USD...) và chuỗi điểm dữ liệu vẽ biểu đồ xu hướng theo ngày |
| `GET` | `/reports/category-distribution` | **Cơ cấu danh mục chuyên sâu**: Thống kê tỷ trọng %, tổng chi tiêu/thu nhập, số lượt giao dịch và tỷ lệ tăng trưởng theo từng danh mục |
| `GET` | `/reports/account-flow` | **Dòng tiền theo tài khoản**: Tiền vào (Inflow), tiền ra (Outflow), dòng tiền ròng và số dư hiện tại của từng ví |
| `GET` | `/reports/top-expenses` | **Top khoản chi tiêu lớn nhất**: Danh sách các giao dịch có số tiền chi tiêu lớn nhất trong kỳ (mặc định top 5) |
| `GET` | `/reports/chart?startDate={yyMMdd}&endDate={yyMMdd}` | Lấy dữ liệu giao dịch trong khoảng ngày phục vụ vẽ đồ thị |
| `GET` | `/reports/account/{accountId}` | Thống kê số dư đầu kỳ, cuối kỳ của một tài khoản cụ thể |
| `GET` | `/reports/distribution/{accountId}` | Thống kê cơ cấu phân bổ thu chi theo danh mục của một tài khoản |
| `POST` | `/reports/summary` | Báo cáo tổng hợp: Tổng thu, tổng chi, số dư ròng theo khoảng thời gian |
| `POST` | `/reports/daily` | Báo cáo biến động thu chi chi tiết từng ngày trong tháng |
| `POST` | `/reports/monthly` | Báo cáo diễn biến thu chi qua 12 tháng trong năm |
| `POST` | `/reports/category` | Báo cáo phân loại tổng hợp theo danh mục |
| `POST` | `/reports/compare` | So sánh chi tiết biến động tài chính giữa tháng này với tháng trước |
| `POST` | `/reports/export/month` | **Xuất file PDF** báo cáo tài chính hàng tháng |
| `POST` | `/reports/export/year` | **Xuất file PDF** báo cáo tài chính hàng năm |

---

## 🔢 Bảng tra cứu Hằng số (Constants Reference)

### 1. Phân loại Danh mục (`Category`)
| ID | Tên danh mục | Phân loại | Ghi chú |
|---|---|---|---|
| `1` | Food (Ăn uống) | Expense | Chi tiêu thiết yếu |
| `2` | Transport (Di chuyển) | Expense | Chi tiêu thiết yếu |
| `3` | Entertainment (Giải trí) | Expense | Chi tiêu cá nhân |
| `4` | Utilities (Hóa đơn / Tiện ích) | Expense | Điện thoại, điện, nước, internet... |
| `5` | Healthcare (Y tế & Sức khỏe) | Expense | Thuốc men, khám sức khỏe... |
| `6` | Education (Giáo dục) | Expense | Học phí, sách vở, khóa học... |
| `7` | Shopping (Mua sắm) | Expense | Quần áo, thiết bị... |
| `8` | Housing (Nhà ở) | Expense | Tiền thuê nhà, bảo trì... |
| `9` | Debt (Trả nợ / Thu nợ) | Expense / Debt | **Được tự động loại trừ khỏi báo cáo thu/chi thông thường** |
| `10`| Other Expense (Chi tiêu khác) | Expense | Các khoản chi phí khác |
| `18`| Traveling (Du lịch) | Expense | Chi tiêu du lịch, vé máy bay, khách sạn |
| `11`| Salary (Tiền lương) | Income | Thu nhập cố định |
| `12`| Business (Kinh doanh) | Income | Thu nhập từ buôn bán, kinh doanh |
| `13`| Investments (Đầu tư) | Income | Cổ phiếu, lợi nhuận đầu tư |
| `14`| Gifts (Quà tặng) | Income | Tiền mừng, quà biếu |
| `15`| Other Income (Thu nhập khác) | Income | Các khoản thu khác |
| `16`| Transfer (Chuyển khoản nội bộ) | Transfer | **Chuyển giữa 2 ví, loại trừ khỏi báo cáo thu/chi để tránh trùng lặp** |
| `17`| Savings (Tiết kiệm / Góp quỹ) | Savings | Nạp hoặc rút từ mục tiêu tiết kiệm |

### 2. Loại Giao dịch (`TransactionType`)
| ID | Mã loại | Mô tả hành vi số dư ví |
|---|---|---|
| `0` | `EXPENSE` | Chi tiêu (Trừ tiền từ ví) |
| `1` | `INCOME` | Thu nhập (Cộng tiền vào ví) |
| `2` | `TRANSFER` | Chuyển khoản nội bộ (Trừ ví nguồn, cộng ví đích) |

### 3. Trạng thái Hoạt động (`Status`)
| ID | Mã trạng thái | Áp dụng cho |
|---|---|---|
| `1` | `ACTIVE` | Tài khoản, Người dùng, Quy tắc định kỳ đang hoạt động |
| `2` | `INACTIVE` | Tài khoản, Người dùng, Quy tắc định kỳ tạm dừng / bị khóa |

### 4. Vai trò Người dùng (`Role`)
| ID | Mã vai trò | Mô tả quyền hạn |
|---|---|---|
| `1` | `ADMIN` | Quản trị viên hệ thống (quản lý user, kích hoạt/khóa tài khoản) |
| `2` | `USER` | Người dùng thông thường |

### 5. Loại Tài khoản / Ví (`AccountType`)
| ID | Mã loại | Ví dụ minh họa |
|---|---|---|
| `1` | `CASH` | Tiền mặt trong ví cá nhân |
| `2` | `BANK` | Tài khoản ngân hàng (Vietcombank, MB, Techcombank...) |
| `3` | `CREDIT_CARD` | Thẻ tín dụng |
| `4` | `E_WALLET` | Ví điện tử (MoMo, ZaloPay, ViettelPay...) |
| `5` | `INVESTMENT` | Tài khoản chứng khoán, tiền số, vàng |
| `6` | `SAVINGS` | Sổ tiết kiệm ngân hàng |
| `7` | `OTHER` | Tài khoản loại khác |

### 6. Đơn vị Tiền tệ (`Currency`)
| ID | Mã tiền tệ |
|---|---|
| `0` | `USD` |
| `1` | `VND` |

### 7. Sổ nợ (`DebtType` & `DebtStatus`)
* **Loại nợ (`DebtType`)**:
  - `1`: `BORROW` (Đi vay - Khoản nợ phải trả)
  - `2`: `LEND` (Cho vay - Khoản nợ phải thu)
* **Trạng thái nợ (`DebtStatus`)**:
  - `1`: `IN_PROGRESS` (Đang vay / Đang nợ chưa trả hết)
  - `2`: `PAID` (Đã hoàn tất / Đã thanh toán hoặc tất toán xong)
  - `3`: `OVERDUE` (Đã quá hạn hẹn trả)

### 8. Mục tiêu Tiết kiệm (`SavingGoalStatus` & `SavingContributionType`)
* **Trạng thái mục tiêu (`SavingGoalStatus`)**:
  - `1`: `IN_PROGRESS` (Đang tích lũy)
  - `2`: `COMPLETED` (Đã hoàn thành mục tiêu $\ge 100\%$)
  - `3`: `CANCELLED` (Đã hủy mục tiêu)
* **Loại giao dịch góp quỹ (`SavingContributionType`)**:
  - `1`: `DEPOSIT` (Nạp tiền / Góp thêm vào quỹ)
  - `2`: `WITHDRAW` (Rút tiền từ quỹ về ví)

### 9. Chu kỳ Giao dịch Định kỳ (`RecurrenceType`)
| ID | Tên chu kỳ | Mô tả |
|---|---|---|
| `1` | `DAILY` | Lặp lại mỗi ngày |
| `2` | `WEEKLY` | Lặp lại mỗi tuần |
| `3` | `MONTHLY` | Lặp lại mỗi tháng |
| `4` | `YEARLY` | Lặp lại mỗi năm |

---

## 📊 Quy tắc Tính toán & Toàn vẹn Dữ liệu

1. **Loại trừ Giao dịch Nội bộ & Nợ trong Báo cáo Doanh thu - Chi phí**:
   - Các truy vấn tính toán Tổng Thu Nhập và Tổng Chi Tiêu trong hệ thống báo cáo tài chính (`ReportService` & `TransactionRepository`) luôn áp dụng điều kiện loại trừ:
     $$\text{category} \notin (9, 16)$$
   - **Lý do**: Chuyển tiền giữa các ví cá nhân (`Category = 16: Transfer`) và Trả/Thu nợ (`Category = 9: Debt`) bản chất là luân chuyển dòng tiền giữa các tài sản, không phải là chi phí sinh hoạt hay nguồn thu nhập thuần túy. Việc loại trừ này giúp ngăn chặn hoàn toàn hiện tượng tính trùng (double-counting) và đảm bảo các chỉ số KPI, tỷ lệ tiết kiệm (Savings Rate) luôn chính xác 100%.

2. **Cơ chế Giao dịch Nguyên tử (ACID Transactional)**:
   - Các nghiệp vụ luân chuyển tiền (Tạo giao dịch, Chuyển khoản, Nạp/Rút quỹ tiết kiệm, Trả nợ) được đóng gói trong `@Transactional`. Nếu xảy ra lỗi ở bất kỳ bước nào, toàn bộ trạng thái sẽ tự động Rollback, đảm bảo số dư ví không bao giờ bị lệch.

3. **Bảo vệ Lịch sử Liên kết (Protected History)**:
   - Các giao dịch được tạo ra tự động từ module Sổ nợ (`Debt`) hoặc Mục tiêu tiết kiệm (`SavingGoal`) được bảo vệ bằng cờ nhận diện. Người dùng không thể xóa hoặc sửa tùy tiện tại màn hình danh sách giao dịch thông thường mà phải thao tác trực tiếp qua module gốc để đảm bảo tính đồng bộ số dư.

---

## 🔐 Cơ chế Bảo mật & Xác thực

1. **JSON Web Token (JWT)**:
   - Cơ chế xác thực không trạng thái (stateless) qua HTTP Header:
     ```http
     Authorization: Bearer <your_access_token>
     ```
   - Thời gian sống của Token được cấu hình qua `jwt.expiration` (mặc định 10 giờ).

2. **Mã hóa Mật khẩu**:
   - Sử dụng thuật toán BCrypt kết hợp cùng chuỗi Salt bảo mật riêng biệt trước khi lưu trữ vào cơ sở dữ liệu.

3. **Kiểm soát Truy cập Phân quyền (RBAC)**:
   - Sử dụng `@EnableMethodSecurity` và `SecurityFilterChain` của Spring Security 6.
   - Các API quản trị hệ thống (`/users/listUser`, `/users/changeStatus`) yêu cầu quyền `ROLE_ADMIN`.

4. **Bảo mật Tải lên File (File Upload)**:
   - Giới hạn dung lượng tối đa 20MB cho mỗi tệp tải lên.
   - Tệp được lưu trữ độc lập tại thư mục `images/` ngoài thư mục mã nguồn và phục vụ qua endpoint tĩnh được bảo vệ.
