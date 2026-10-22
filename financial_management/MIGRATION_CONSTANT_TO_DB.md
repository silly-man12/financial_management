# 📊 Kế Hoạch Chuyển Đổi TransactionType & Category Sang Database (Phương Án Block ID Dạng Số)

> **Ngày cập nhật:** 2026-10-22  
> **Trạng thái:** Đã hoàn thành triển khai toàn bộ (Code + DB Migration + Full CRUD API)  
> **Định dạng ID:** Số nguyên (`INT`), phân dải theo Block `1xx`, `2xx`, `3xx`

---

## 1. Tổng Quan & Mục Tiêu

Hiện tại hệ thống đã chuyển đổi **TransactionType** (3 giá trị: 1, 2, 3) và **Category** (dải số Block ID `1xx`, `2xx`, `3xx`) hoàn toàn sang Database.

### Hạn chế của hệ thống cũ:
- Giá trị `0` làm ID cho `TransactionType.EXPENSE` là bad practice trong CSDL (dễ nhầm với giá trị mặc định/null).
- Category bị chắp vá, nhảy cóc: `18 (TRAVELING)` và `19 (PRESENT)` là Chi tiêu nhưng bị nằm sau `16 (TRANSFER)` và `17 (SAVINGS)`.
- Không thể thêm/sửa/xóa danh mục tại runtime mà không sửa code, build và redeploy.
- Thiếu các thuộc tính hiển thị (icon Bootstrap, mã màu HEX) trong CSDL khiến Frontend bị phụ thuộc vào các file khai báo tĩnh.
- Thiếu ràng buộc toàn vẹn khóa ngoại (Foreign Key) ở mức database.

### Quyết định kiến trúc (Phương án 1 - Block ID):
1. **Duy trì ID dạng số (`INT`)**: Không dùng `UUID` (quá cồng kềnh cho bảng từ điển vài chục dòng) hay `VARCHAR code` (tốn dung lượng, join chậm trên bảng hàng triệu giao dịch).
2. **Quy hoạch dải số khoa học**:
   - `TransactionType`: Bắt đầu từ `1` (`1: EXPENSE`, `2: INCOME`, `3: TRANSFER`).
   - `Category`: Phân block rõ ràng: Chi tiêu (`1xx`), Thu nhập (`2xx`), Khác (`3xx`).
3. **Lưu trữ trọn vẹn thuộc tính hiển thị trong DB**: Bổ sung `icon NVARCHAR(50)` (lưu Bootstrap Icon class) và `color VARCHAR(20)` (lưu mã màu HEX) trực tiếp vào bảng `categories`.
4. **Cung cấp đầy đủ bộ API CRUD**: Cho phép thêm, sửa, xóa, bật/tắt danh mục linh hoạt tại runtime mà không cần sửa code.

---

## 2. Bảng Ánh Xạ ID: Cũ ➡️ Mới (Mapping Matrix)

### 2.1 TransactionType

| Constant | ID Cũ | ID Mới | Code | Tên hiển thị |
| :--- | :---: | :---: | :--- | :--- |
| `EXPENSE` | `0` | **`1`** | `EXPENSE` | Chi tiêu |
| `INCOME` | `1` | **`2`** | `INCOME` | Thu nhập |
| `TRANSFER` | `2` | **`3`** | `TRANSFER` | Chuyển khoản |

---

### 2.2 Category

| Nhóm | Constant | ID Cũ | ID Mới | Code | Tên tiếng Việt | Icon Bootstrap | Mã màu (HEX) |
| :--- | :--- | :---: | :---: | :--- | :--- | :--- | :---: |
| **Chi tiêu**<br/>*(Dải 1xx)* | `FOOD` | 1 | **101** | `FOOD` | Ăn uống | `bi-fork-knife` | `#FF6384` |
| | `TRANSPORT` | 2 | **102** | `TRANSPORT` | Di chuyển | `bi-car-front-fill` | `#36A2EB` |
| | `ENTERTAINMENT` | 3 | **103** | `ENTERTAINMENT` | Giải trí | `bi-controller` | `#FFCE56` |
| | `UTILITIES` | 4 | **104** | `UTILITIES` | Tiện ích | `bi-lightning-charge-fill` | `#4BC0C0` |
| | `HEALTHCARE` | 5 | **105** | `HEALTHCARE` | Sức khỏe | `bi-heart-pulse-fill` | `#9966FF` |
| | `EDUCATION` | 6 | **106** | `EDUCATION` | Giáo dục | `bi-mortarboard-fill` | `#FF9F40` |
| | `SHOPPING` | 7 | **107** | `SHOPPING` | Mua sắm | `bi-cart-fill` | `#66BB6A` |
| | `HOUSING` | 8 | **108** | `HOUSING` | Nhà ở | `bi-house-door-fill` | `#EF5350` |
| | `DEBT` | 9 | **109** | `DEBT` | Sổ nợ (Trả nợ) | `bi-cash-stack` | `#8D6E63` |
| | `TRAVELING` | 18 | **110** | `TRAVELING` | Du lịch | `bi-airplane-fill` | `#00BCD4` |
| | `PRESENT` | 19 | **111** | `PRESENT` | Quà biếu / Tặng | `bi-gift-fill` | `#F43F5E` |
| | `OTHER_EXPENSE` | 10 | **112** | `OTHER_EXPENSE` | Khoản chi khác | `bi-wallet` | `#90A4AE` |
| **Thu nhập**<br/>*(Dải 2xx)* | `SALARY` | 11 | **201** | `SALARY` | Tiền lương | `bi-cash-stack` | `#2ECC71` |
| | `BUSINESS` | 12 | **202** | `BUSINESS` | Kinh doanh | `bi-building-fill` | `#1ABC9C` |
| | `INVESTMENTS` | 13 | **203** | `INVESTMENTS` | Đầu tư / Lãi | `bi-graph-up-arrow` | `#F1C40F` |
| | `GIFTS` | 14 | **204** | `GIFTS` | Thưởng / Quà | `bi-gift-fill` | `#E91E63` |
| | `OTHER_INCOME` | 15 | **205** | `OTHER_INCOME` | Khoản thu khác | `bi-wallet2` | `#3498DB` |
| **Khác**<br/>*(Dải 3xx)* | `TRANSFER` | 16 | **301** | `TRANSFER` | Chuyển khoản | `bi-arrow-left-right` | `#0d6dfc` |
| | `SAVINGS` | 17 | **302** | `SAVINGS` | Mục tiêu tiết kiệm | `bi-piggy-bank-fill` | `#8b5cf6` |

> [!TIP]
> **Ưu điểm vượt trội của dải số 1xx, 2xx, 3xx:**
> - Trong Java, kiểm tra nhóm chỉ cần một phép so sánh đơn giản:
>   - Là Chi tiêu: `categoryId >= 100 && categoryId < 200`
>   - Là Thu nhập: `categoryId >= 200 && categoryId < 300`
> - Dải `101 -> 199` còn trống tới hơn 80 số, thoải mái mở rộng thêm các danh mục chi tiêu sau này mà không bao giờ bị nhảy cóc.

---

## 3. Thiết Kế Bảng Database Mới (SQL Server)

```sql
-- 1. Bảng transaction_types
CREATE TABLE transaction_types (
    id          INT PRIMARY KEY,
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        NVARCHAR(100) NOT NULL,
    created_at  DATETIME2 DEFAULT SYSUTCDATETIME()
);

-- Seed data: transaction_types
INSERT INTO transaction_types (id, code, name) VALUES
(1, 'EXPENSE',  N'Chi tiêu'),
(2, 'INCOME',   N'Thu nhập'),
(3, 'TRANSFER', N'Chuyển khoản');

-- 2. Bảng categories (Đầy đủ icon Bootstrap và mã màu HEX)
CREATE TABLE categories (
    id                  INT PRIMARY KEY,
    transaction_type_id INT NOT NULL,
    code                VARCHAR(50) NOT NULL UNIQUE,
    name                NVARCHAR(100) NOT NULL,
    name_vi             NVARCHAR(100) NOT NULL,
    icon                NVARCHAR(50),
    color               VARCHAR(20),
    is_system           BIT NOT NULL DEFAULT 1,
    is_active           BIT NOT NULL DEFAULT 1,
    display_order       INT DEFAULT 0,
    created_at          DATETIME2 DEFAULT SYSUTCDATETIME(),
    
    CONSTRAINT FK_category_type FOREIGN KEY (transaction_type_id) 
        REFERENCES transaction_types(id)
);

-- Seed data: categories (Đầy đủ Icon Bootstrap & Mã màu HEX cho Frontend đọc động trực tiếp từ DB)
INSERT INTO categories (id, transaction_type_id, code, name, name_vi, icon, color, is_system, display_order) VALUES
-- Chi tiêu (1xx)
(101, 1, 'FOOD',          'Food',          N'Ăn uống',           'bi-fork-knife',            '#FF6384', 1, 1),
(102, 1, 'TRANSPORT',     'Transport',     N'Di chuyển',         'bi-car-front-fill',        '#36A2EB', 1, 2),
(103, 1, 'ENTERTAINMENT', 'Entertainment', N'Giải trí',          'bi-controller',            '#FFCE56', 1, 3),
(104, 1, 'UTILITIES',     'Utilities',     N'Tiện ích',          'bi-lightning-charge-fill', '#4BC0C0', 1, 4),
(105, 1, 'HEALTHCARE',    'Healthcare',    N'Sức khỏe',          'bi-heart-pulse-fill',      '#9966FF', 1, 5),
(106, 1, 'EDUCATION',     'Education',     N'Giáo dục',          'bi-mortarboard-fill',      '#FF9F40', 1, 6),
(107, 1, 'SHOPPING',      'Shopping',      N'Mua sắm',          'bi-cart-fill',             '#66BB6A', 1, 7),
(108, 1, 'HOUSING',       'Housing',       N'Nhà ở',            'bi-house-door-fill',       '#EF5350', 1, 8),
(109, 1, 'DEBT',          'Debt',          N'Sổ nợ (Trả nợ)',    'bi-cash-stack',            '#8D6E63', 1, 9),
(110, 1, 'TRAVELING',     'Traveling',     N'Du lịch',          'bi-airplane-fill',         '#00BCD4', 1, 10),
(111, 1, 'PRESENT',       'Present',       N'Quà biếu / Tặng',   'bi-gift-fill',             '#F43F5E', 1, 11),
(112, 1, 'OTHER_EXPENSE', 'Other Expense', N'Khoản chi khác',    'bi-wallet',                '#90A4AE', 1, 12),
-- Thu nhập (2xx)
(201, 2, 'SALARY',        'Salary',        N'Tiền lương',        'bi-cash-stack',            '#2ECC71', 1, 1),
(202, 2, 'BUSINESS',      'Business',      N'Kinh doanh',       'bi-building-fill',         '#1ABC9C', 1, 2),
(203, 2, 'INVESTMENTS',   'Investments',   N'Đầu tư / Lãi',    'bi-graph-up-arrow',        '#F1C40F', 1, 3),
(204, 2, 'GIFTS',         'Gifts',         N'Thưởng / Quà',     'bi-gift-fill',             '#E91E63', 1, 4),
(205, 2, 'OTHER_INCOME',  'Other Income',  N'Thu nhập khác',    'bi-wallet2',               '#3498DB', 1, 5),
-- Chuyển khoản & Tiết kiệm (3xx)
(301, 3, 'TRANSFER',      'Transfer',      N'Chuyển khoản',     'bi-arrow-left-right',      '#0d6dfc', 1, 1),
(302, 1, 'SAVINGS',       'Savings',       N'Mục tiêu tiết kiệm','bi-piggy-bank-fill',       '#8b5cf6', 1, 2);
```

---

## 4. Các Script Chuyển Đổi & Cập Nhật CSDL

### 4.1 Script Chuyển Đổi Dữ Liệu Cũ Sang Bộ Block ID Mới (Áp dụng khi dữ liệu đang ở ID cũ 1..19)

Toàn bộ quá trình chuyển đổi dữ liệu được bao bọc trong một **Database Transaction** khép kín, sử dụng câu lệnh `CASE WHEN` để cập nhật đồng thời, ngăn chặn hoàn toàn lỗi ghi đè vòng tròn.

```sql
-- ============================================================================
-- SCRIPT MIGRATION DỮ LIỆU HIỆN CÓ SANG BỘ ID MỚI
-- ============================================================================
BEGIN TRANSACTION;
BEGIN TRY

    -- 1. Cập nhật Category cho 3 bảng: transactions, budgets, recurring_transactions
    UPDATE transactions SET category = CASE category
        WHEN 1 THEN 101  WHEN 2 THEN 102  WHEN 3 THEN 103  WHEN 4 THEN 104
        WHEN 5 THEN 105  WHEN 6 THEN 106  WHEN 7 THEN 107  WHEN 8 THEN 108
        WHEN 9 THEN 109  WHEN 18 THEN 110 WHEN 19 THEN 111 WHEN 10 THEN 112
        WHEN 11 THEN 201 WHEN 12 THEN 202 WHEN 13 THEN 203 WHEN 14 THEN 204 WHEN 15 THEN 205
        WHEN 16 THEN 301 WHEN 17 THEN 302
        ELSE category END
    WHERE category IS NOT NULL;

    UPDATE budgets SET category = CASE category
        WHEN 1 THEN 101  WHEN 2 THEN 102  WHEN 3 THEN 103  WHEN 4 THEN 104
        WHEN 5 THEN 105  WHEN 6 THEN 106  WHEN 7 THEN 107  WHEN 8 THEN 108
        WHEN 9 THEN 109  WHEN 18 THEN 110 WHEN 19 THEN 111 WHEN 10 THEN 112
        WHEN 11 THEN 201 WHEN 12 THEN 202 WHEN 13 THEN 203 WHEN 14 THEN 204 WHEN 15 THEN 205
        WHEN 16 THEN 301 WHEN 17 THEN 302
        ELSE category END
    WHERE category IS NOT NULL;

    UPDATE recurring_transactions SET category = CASE category
        WHEN 1 THEN 101  WHEN 2 THEN 102  WHEN 3 THEN 103  WHEN 4 THEN 104
        WHEN 5 THEN 105  WHEN 6 THEN 106  WHEN 7 THEN 107  WHEN 8 THEN 108
        WHEN 9 THEN 109  WHEN 18 THEN 110 WHEN 19 THEN 111 WHEN 10 THEN 112
        WHEN 11 THEN 201 WHEN 12 THEN 202 WHEN 13 THEN 203 WHEN 14 THEN 204 WHEN 15 THEN 205
        WHEN 16 THEN 301 WHEN 17 THEN 302
        ELSE category END
    WHERE category IS NOT NULL;

    -- 2. Cập nhật Type cho 2 bảng: transactions, recurring_transactions
    UPDATE transactions SET type = CASE type
        WHEN 0 THEN 1
        WHEN 1 THEN 2
        WHEN 2 THEN 3
        ELSE type END;

    UPDATE recurring_transactions SET type = CASE type
        WHEN 0 THEN 1
        WHEN 1 THEN 2
        WHEN 2 THEN 3
        ELSE type END;

    -- 3. Tạo các ràng buộc Khóa Ngoại (Foreign Key) để bảo vệ toàn vẹn dữ liệu
    ALTER TABLE transactions 
        ADD CONSTRAINT FK_tx_type FOREIGN KEY (type) REFERENCES transaction_types(id);

    ALTER TABLE transactions 
        ADD CONSTRAINT FK_tx_category FOREIGN KEY (category) REFERENCES categories(id);

    ALTER TABLE budgets 
        ADD CONSTRAINT FK_budget_category FOREIGN KEY (category) REFERENCES categories(id);

    ALTER TABLE recurring_transactions 
        ADD CONSTRAINT FK_recurring_type FOREIGN KEY (type) REFERENCES transaction_types(id);

    ALTER TABLE recurring_transactions 
        ADD CONSTRAINT FK_recurring_category FOREIGN KEY (category) REFERENCES categories(id);

    COMMIT TRANSACTION;
    PRINT N'✅ MIGRATION THÀNH CÔNG RỰC RỠ!';
END TRY
BEGIN CATCH
    ROLLBACK TRANSACTION;
    PRINT N'❌ LỖI TRONG QUÁ TRÌNH MIGRATION: ' + ERROR_MESSAGE();
    THROW;
END CATCH;
```

---

### 4.2 Script Bổ Sung Trường Dữ Liệu Còn Thiếu & Convert Dữ Liệu Tương Ứng (Áp dụng khi dữ liệu đã migrate sang Block ID)

Khi bảng `categories` và các bảng `transactions`, `budgets`, `recurring_transactions` đã được chuyển đổi sang bộ Block ID (`1xx`, `2xx`, `3xx`), ta chỉ cần chạy script sau để:
1. Thêm cột `color` (`VARCHAR(20)`) nếu chưa có.
2. Mở rộng cột `icon` lên `NVARCHAR(50)` để lưu trọn vẹn class Bootstrap Icons.
3. Chạy lệnh SQL `UPDATE ... FROM (VALUES ...)` để nạp toàn bộ icon Bootstrap và mã màu HEX tương ứng cho từng dòng dữ liệu (khớp theo cả `id` và `code`):

```sql
-- ============================================================================
-- 1. BỔ SUNG CỘT CÒN THIẾU & MỞ RỘNG ĐỘ DÀI CỘT ICON
-- ============================================================================

-- Thêm cột color (mã màu HEX) nếu chưa tồn tại
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'categories' AND COLUMN_NAME = 'color'
)
BEGIN
    ALTER TABLE categories ADD color VARCHAR(20) NULL;
    PRINT N'✅ Đã thêm cột color vào bảng categories';
END;
GO

-- Mở rộng độ dài cột icon lên NVARCHAR(50) để chứa trọn vẹn class Bootstrap Icons
ALTER TABLE categories ALTER COLUMN icon NVARCHAR(50) NULL;
PRINT N'✅ Đã mở rộng độ dài cột icon lên NVARCHAR(50)';
GO

-- ============================================================================
-- 2. CẬP NHẬT / CONVERT DỮ LIỆU ICON & MÃ MÀU CHO TỪNG DANH MỤC
-- Ánh xạ đồng thời theo cả ID (1xx, 2xx, 3xx) và CODE để đảm bảo cập nhật chính xác 100%
-- ============================================================================
BEGIN TRANSACTION;
BEGIN TRY

    UPDATE c
    SET 
        c.name_vi = v.name_vi,
        c.icon    = v.icon,
        c.color   = v.color
    FROM categories c
    INNER JOIN (VALUES
        -- Chi tiêu (Dải 1xx)
        (101, 'FOOD',          N'Ăn uống',             'bi-fork-knife',            '#FF6384'),
        (102, 'TRANSPORT',     N'Di chuyển',           'bi-car-front-fill',        '#36A2EB'),
        (103, 'ENTERTAINMENT', N'Giải trí',            'bi-controller',            '#FFCE56'),
        (104, 'UTILITIES',     N'Tiện ích',            'bi-lightning-charge-fill', '#4BC0C0'),
        (105, 'HEALTHCARE',    N'Sức khỏe',            'bi-heart-pulse-fill',      '#9966FF'),
        (106, 'EDUCATION',     N'Giáo dục',            'bi-mortarboard-fill',      '#FF9F40'),
        (107, 'SHOPPING',      N'Mua sắm',             'bi-cart-fill',             '#66BB6A'),
        (108, 'HOUSING',       N'Nhà ở',               'bi-house-door-fill',       '#EF5350'),
        (109, 'DEBT',          N'Sổ nợ (Trả nợ)',      'bi-cash-stack',            '#8D6E63'),
        (110, 'TRAVELING',     N'Du lịch',             'bi-airplane-fill',         '#00BCD4'),
        (111, 'PRESENT',       N'Quà biếu / Tặng',     'bi-gift-fill',             '#F43F5E'),
        (112, 'OTHER_EXPENSE', N'Khoản chi khác',       'bi-wallet',                '#90A4AE'),

        -- Thu nhập (Dải 2xx)
        (201, 'SALARY',        N'Tiền lương',          'bi-cash-stack',            '#2ECC71'),
        (202, 'BUSINESS',      N'Kinh doanh',          'bi-building-fill',         '#1ABC9C'),
        (203, 'INVESTMENTS',   N'Đầu tư / Lãi',        'bi-graph-up-arrow',        '#F1C40F'),
        (204, 'GIFTS',         N'Thưởng / Quà',        'bi-gift-fill',             '#E91E63'),
        (205, 'OTHER_INCOME',  N'Khoản thu khác',       'bi-wallet2',               '#3498DB'),

        -- Chuyển khoản & Tiết kiệm (Dải 3xx)
        (301, 'TRANSFER',      N'Chuyển khoản',        'bi-arrow-left-right',      '#0d6dfc'),
        (302, 'SAVINGS',       N'Mục tiêu tiết kiệm',   'bi-piggy-bank-fill',       '#8b5cf6')
    ) AS v(id, code, name_vi, icon, color)
        ON c.id = v.id OR c.code = v.code;

    COMMIT TRANSACTION;
    PRINT N'✅ ĐÃ CẬP NHẬT THÀNH CÔNG ICON VÀ COLOR CHO TOÀN BỘ 19 CATEGORIES!';
END TRY
BEGIN CATCH
    ROLLBACK TRANSACTION;
    PRINT N'❌ LỖI TRONG QUÁ TRÌNH CẬP NHẬT: ' + ERROR_MESSAGE();
    THROW;
END CATCH;
GO

-- 3. Kiểm tra lại kết quả
SELECT 
    id, 
    transaction_type_id AS [type], 
    code, 
    name_vi, 
    icon, 
    color, 
    display_order
FROM categories
ORDER BY id;
```

---

## 5. Danh Sách Các Bước Cần Làm (Step-by-Step Action Plan)

Quy trình triển khai gồm 8 bước tuần tự được thiết kế nhằm đảm bảo hệ thống không bị gián đoạn hay lỗi dữ liệu:

```mermaid
flowchart TD
    S1["Bước 1: Backup CSDL"] --> S2["Bước 2: Chạy Migration SQL"]
    S2 --> S3["Bước 3: Cập nhật Constant Java"]
    S3 --> S4["Bước 4: Cập nhật TransactionRepository"]
    S4 --> S5["Bước 5: Tạo Entity, DTO & Triển Khai CRUD API"]
    S5 --> S6["Bước 6: Refactor Tầng Nghiệp Vụ"]
    S6 --> S7["Bước 7: Kiểm Thử Toàn Diện"]
    S7 --> S8["Bước 8: Hoàn Tất & Deploy"]
```

### Bước 1: Sao lưu CSDL (Database Backup)
- Xuất bản backup toàn bộ database SQL Server hiện tại (file `.bak` hoặc script SQL data) để dự phòng phục hồi khẩn cấp nếu phát sinh sự cố.

### Bước 2: Thực thi Script CSDL
- Chạy script tạo 2 bảng `transaction_types` và `categories` (Mục 3).
- Chạy script data migration chuyển đổi dữ liệu và gắn khóa ngoại FK (Mục 4.1 hoặc Mục 4.2).
- Xác minh bằng các câu lệnh `COUNT` để đảm bảo không có bản ghi nào bị rơi vào giá trị lạ.

### Bước 3: Cập nhật Constants Java
- Cập nhật file `TransactionType.java`:
  - `EXPENSE = 1`, `INCOME = 2`, `TRANSFER = 3`.
- Cập nhật file `Category.java`:
  - Đổi giá trị `FOOD = 101` ... `OTHER_EXPENSE = 112`, `SALARY = 201` ... `OTHER_INCOME = 205`, `TRANSFER = 301`, `SAVINGS = 302`.
  - Đơn giản hóa hàm `isExpense()` thành `categoryId >= 100 && categoryId < 200`.
  - Đơn giản hóa hàm `isIncome()` thành `categoryId >= 200 && categoryId < 300`.

### Bước 4: Refactor Queries trong `TransactionRepository.java`
- Thay thế các magic numbers cũ trong ~15 JPQL/Native queries:
  - `type = 0` ➡️ `type = 1`
  - `type = 1` ➡️ `type = 2`
  - `category NOT IN (9, 16)` ➡️ `category NOT IN (109, 301)` *(loại trừ Sổ nợ & Chuyển khoản)*
  - `category != 16` ➡️ `category != 301`

### Bước 5: Tạo Entity, Repository, DTO & Triển Khai Bộ API CRUD Mới
- Tạo `TransactionTypeEntity.java` và `CategoryEntity.java` (khóa chính `Integer id`, đầy đủ `icon NVARCHAR(50)` và `color VARCHAR(20)`).
- Tạo `CategoryRequest.java` và `CategoryResponse.java`.
- Tạo `TransactionTypeRepository.java` và `CategoryRepository.java` (hỗ trợ sinh Block ID tự động theo range).
- Tạo `CategoryService.java` tích hợp `@Cacheable` tra cứu nhanh và `@CacheEvict` tự động làm mới khi thêm/sửa/xóa.
- Xây dựng `CategoryController.java` cung cấp đầy đủ bộ API RESTful CRUD:
  - `GET /categories`: Danh sách danh mục (lọc theo `typeId`, `includeInactive`).
  - `GET /categories/{id}`: Chi tiết danh mục.
  - `POST /categories`: Thêm mới danh mục (tự sinh Block ID nếu không truyền).
  - `POST /categories/{id}`: Cập nhật thông tin danh mục.
  - `POST /categories/{id}/status`: Bật / tắt trạng thái hoạt động.
  - `DELETE /categories/{id}`: Xóa danh mục an toàn (chặn xóa danh mục hệ thống, tự động chuyển xóa mềm nếu đã phát sinh dữ liệu, xóa cứng nếu chưa dùng).
- Tạo `CategoryDataInitializer.java` tự động seed và sync dữ liệu khi Spring Boot khởi động.

### Bước 6: Đồng Bộ Tầng Giao Tiếp (Telegram Bot & Service)
- Kiểm tra các service: `TransactionService`, `ReportService`, `DebtService`, `CashflowService`, `TelegramMessageParser`.
- Vì code đã dùng `Category.FOOD`, `TransactionType.EXPENSE` nên phần lớn logic nghiệp vụ tự động ăn theo ID mới mà không cần sửa.

### Bước 7: Kiểm Thử Toàn Diện (Testing & Verification)
- Chạy toàn bộ test suites (`mvn clean test`).
- Kiểm tra tính đúng đắn của các báo cáo tài chính (`/report/...`), thống kê danh mục (`/report/category-distribution`), dashboard tổng hợp (`/dashboard`).
- Thử tạo mới giao dịch qua API và qua Telegram bot để xác nhận ID mới được lưu đúng vào DB.

### Bước 8: Hoàn Tất & Cập Nhật Tài Liệu
- Cập nhật lại tài liệu Swagger API và README.md với bộ ID mới.

---

## 6. Kịch Bản Rollback Khẩn Cấp (Emergency Rollback)

Nếu cần quay lại hệ thống cũ vì bất kỳ lý do gì, chạy script sau để khôi phục lại ID cũ:

```sql
BEGIN TRANSACTION;
-- 1. Xóa các ràng buộc FK
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS FK_tx_type;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS FK_tx_category;
ALTER TABLE budgets DROP CONSTRAINT IF EXISTS FK_budget_category;
ALTER TABLE recurring_transactions DROP CONSTRAINT IF EXISTS FK_recurring_type;
ALTER TABLE recurring_transactions DROP CONSTRAINT IF EXISTS FK_recurring_category;

-- 2. Đảo ngược Category
UPDATE transactions SET category = CASE category
    WHEN 101 THEN 1  WHEN 102 THEN 2  WHEN 103 THEN 3  WHEN 104 THEN 4
    WHEN 105 THEN 5  WHEN 106 THEN 6  WHEN 107 THEN 7  WHEN 108 THEN 8
    WHEN 109 THEN 9  WHEN 110 THEN 18 WHEN 111 THEN 19 WHEN 112 THEN 10
    WHEN 201 THEN 11 WHEN 202 THEN 12 WHEN 203 THEN 13 WHEN 204 THEN 14 WHEN 205 THEN 15
    WHEN 301 THEN 16 WHEN 302 THEN 17
    ELSE category END;

UPDATE budgets SET category = CASE category
    WHEN 101 THEN 1  WHEN 102 THEN 2  WHEN 103 THEN 3  WHEN 104 THEN 4
    WHEN 105 THEN 5  WHEN 106 THEN 6  WHEN 107 THEN 7  WHEN 108 THEN 8
    WHEN 109 THEN 9  WHEN 110 THEN 18 WHEN 111 THEN 19 WHEN 112 THEN 10
    WHEN 201 THEN 11 WHEN 202 THEN 12 WHEN 203 THEN 13 WHEN 204 THEN 14 WHEN 205 THEN 15
    WHEN 301 THEN 16 WHEN 302 THEN 17
    ELSE category END;

UPDATE recurring_transactions SET category = CASE category
    WHEN 101 THEN 1  WHEN 102 THEN 2  WHEN 103 THEN 3  WHEN 104 THEN 4
    WHEN 105 THEN 5  WHEN 106 THEN 6  WHEN 107 THEN 7  WHEN 108 THEN 8
    WHEN 109 THEN 9  WHEN 110 THEN 18 WHEN 111 THEN 19 WHEN 112 THEN 10
    WHEN 201 THEN 11 WHEN 202 THEN 12 WHEN 203 THEN 13 WHEN 204 THEN 14 WHEN 205 THEN 15
    WHEN 301 THEN 16 WHEN 302 THEN 17
    ELSE category END;

-- 3. Đảo ngược Type
UPDATE transactions SET type = CASE type
    WHEN 1 THEN 0
    WHEN 2 THEN 1
    WHEN 3 THEN 2
    ELSE type END;

UPDATE recurring_transactions SET type = CASE type
    WHEN 1 THEN 0
    WHEN 2 THEN 1
    WHEN 3 THEN 2
    ELSE type END;

COMMIT TRANSACTION;
```

---

## 7. Bảng So Sánh Trước & Sau Khi Hoàn Thành

| Tiêu chí | Trước migration (Cũ) | Sau migration (Mới - PA 1) |
| :--- | :--- | :--- |
| **Kiểu dữ liệu ID** | `static final int` (Java) | `INT PRIMARY KEY` (Database) |
| **Quy chuẩn ID** | Lộn xộn (`0` làm type, `18-19` nhảy cóc) | Chuẩn mực (`1, 2, 3` và `1xx, 2xx, 3xx`) |
| **Kiểm tra nhóm** | Điều kiện dài dòng, dễ sót | Gọn gàng (`id >= 100 && id < 200`) |
| **Toàn vẹn CSDL** | Không có ràng buộc FK | Ràng buộc khóa ngoại FK chặt chẽ |
| **Thuộc tính hiển thị** | Tĩnh trong frontend code | Lưu trong DB (`icon` Bootstrap + `color` HEX) |
| **Quản trị danh mục** | Không thể (phải sửa code & build lại) | Đầy đủ API RESTful CRUD tại runtime |
| **Khả năng mở rộng** | Sửa code, build, deploy | Thêm dòng mới vào DB kèm cache tức thì |
| **Độ an toàn khi migrate** | Không có | Có script 1-1 có bảo vệ transaction + rollback |
