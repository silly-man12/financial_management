# 📊 Phân Tích Ảnh Hưởng: Chuyển TransactionType & Category Sang Database

> **Ngày tạo:** 2026-10-20  
> **Tác giả:** Auto-generated  
> **Trạng thái:** Đang phân tích — chờ quyết định

---

## 1. Tổng Quan

Hiện tại hệ thống đang hardcode **TransactionType** (3 giá trị) và **Category** (19 giá trị) dưới dạng `static final int` trong package `constant`. Khi hệ thống lớn lên, việc này gây ra các hạn chế:

- Không thể thêm/sửa/xóa category mà không sửa code → build → deploy
- Không hỗ trợ custom category cho từng user
- Không hỗ trợ đa ngôn ngữ (tên category hardcode trong Java)
- Không có ràng buộc FK trong database → dữ liệu có thể chứa giá trị category không hợp lệ

**Đề xuất:** Chuyển sang bảng database, giữ nguyên giá trị `int` ID hiện tại để **không cần migrate data**.

---

## 2. Giá Trị Hiện Tại

### TransactionType.java

| Constant   | Giá trị | Mô tả     |
|------------|---------|-----------|
| `EXPENSE`  | 0       | Chi tiêu  |
| `INCOME`   | 1       | Thu nhập  |
| `TRANSFER` | 2       | Chuyển khoản |

### Category.java

| Constant        | Giá trị | Loại    | Mô tả          |
|-----------------|---------|---------|-----------------|
| `FOOD`          | 1       | Expense | Ăn uống         |
| `TRANSPORT`     | 2       | Expense | Di chuyển       |
| `ENTERTAINMENT` | 3       | Expense | Giải trí        |
| `UTILITIES`     | 4       | Expense | Hóa đơn/Tiện ích|
| `HEALTHCARE`    | 5       | Expense | Sức khỏe        |
| `EDUCATION`     | 6       | Expense | Học tập         |
| `SHOPPING`      | 7       | Expense | Mua sắm         |
| `HOUSING`       | 8       | Expense | Nhà ở           |
| `DEBT`          | 9       | Expense | Sổ nợ           |
| `OTHER_EXPENSE` | 10      | Expense | Chi tiêu khác   |
| `SALARY`        | 11      | Income  | Lương           |
| `BUSINESS`      | 12      | Income  | Kinh doanh      |
| `INVESTMENTS`   | 13      | Income  | Đầu tư/Lãi      |
| `GIFTS`         | 14      | Income  | Thưởng/Quà      |
| `OTHER_INCOME`  | 15      | Income  | Thu nhập khác   |
| `TRANSFER`      | 16      | Transfer| Chuyển khoản    |
| `SAVINGS`       | 17      | Special | Tiết kiệm       |
| `TRAVELING`     | 18      | Expense | Du lịch         |
| `PRESENT`       | 19      | Expense | Quà tặng        |

---

## 3. Mức Độ Ảnh Hưởng: TRUNG BÌNH — CAO

### 3.1 Tổng kết

| Thống kê                  | TransactionType | Category | Tổng        |
|---------------------------|-----------------|----------|-------------|
| Số file bị ảnh hưởng     | 7               | 5        | **9 file**  |
| Số dòng code reference   | ~50             | ~55      | **~105 dòng** |
| Native queries hardcode   | ~15             | ~20      | **~35 queries** |

### 3.2 Chi tiết từng file

#### Entity Layer (3 files) — Thay đổi nhỏ

| File | Vị trí | Ảnh hưởng |
|------|--------|-----------|
| `entity/Transaction.java` | Dòng 42-46 | `int type`, `int category` → thêm FK annotation |
| `entity/Budget.java` | Dòng 32-33 | `int category` → thêm FK annotation |
| `entity/RecurringTransaction.java` | Dòng 33-37 | `int type`, `int category` → thêm FK annotation |

#### Service Layer (4 files) — Thay đổi nhiều nhất

| File | Số reference | Chi tiết |
|------|-------------|----------|
| `services/TransactionService.java` | ~15 | `validateCategory()`, so sánh `==`, logic tính toán delta |
| `services/ReportService.java` | ~20 | Hardcode `TransactionType.INCOME/EXPENSE` ở rất nhiều chỗ gọi repo |
| `services/SavingGoalService.java` | ~5 | Tạo transaction với hardcode type |
| `services/RecurringTransactionService.java` | ~3 | Logic tính delta |

#### Telegram Layer (3 files) — Thay đổi nhiều

| File | Số reference | Chi tiết |
|------|-------------|----------|
| `telegram/TelegramCommandHandler.java` | ~12 | So sánh type/category, hiển thị icon |
| `telegram/TelegramMessageParser.java` | ~25 | `detectCategory()`, `getCategoryVietnameseName()` — nặng nhất |
| `telegram/TelegramService.java` | ~3 | So sánh type |

#### Repository Layer (1 file) — ⚠️ RỦI RO CAO NHẤT

| File | Vấn đề |
|------|--------|
| `repository/TransactionRepository.java` | **~35 JPQL/native queries hardcode magic numbers `0`, `1`, `9`, `16`** |

**Ví dụ các magic numbers trong queries:**

```sql
-- Lặp đi lặp lại rất nhiều lần trong TransactionRepository.java
CASE WHEN [type] = 1 AND [category] NOT IN (9, 16) THEN [amount] ...
CASE WHEN [type] = 0 AND [category] NOT IN (9, 16) THEN [amount] ...
AND t.type = 0 AND t.category != 16
```

Giá trị `0` = EXPENSE, `1` = INCOME, `9` = DEBT, `16` = TRANSFER bị hardcode trực tiếp. Đây vừa là lý do cần chuyển, vừa là nơi dễ gây bug nhất khi migrate.

---

## 4. Vấn Đề Kiến Trúc Hiện Tại

### 4.1 Magic Numbers tràn lan
- 12+ queries trong `TransactionRepository.java` dùng `0`, `1`, `9`, `16` trực tiếp
- Nếu thay đổi ID, phải tìm và sửa tay từng query

### 4.2 Logic nghiệp vụ embed trong constant
```java
// Category.java — logic isExpense/isIncome dựa trên range số
public static boolean isExpense(int categoryId) {
    return (categoryId >= FOOD && categoryId <= OTHER_EXPENSE) 
           || categoryId == TRAVELING || categoryId == PRESENT;
}
```
Mỗi lần thêm category mới phải sửa code, recompile, redeploy. Dễ quên cập nhật hàm `isExpense()`.

### 4.3 Không mở rộng được runtime
- Không thể thêm category mới từ UI/API
- Mỗi user không thể có custom category riêng
- Tên category chỉ hỗ trợ tiếng Anh + hardcode tiếng Việt riêng trong Telegram parser

---

## 5. Thiết Kế Database Mới

### 5.1 Bảng `transaction_types`

```sql
CREATE TABLE transaction_types (
    id    INT PRIMARY KEY,           -- Giữ nguyên: 0, 1, 2
    code  VARCHAR(50) NOT NULL UNIQUE, -- 'EXPENSE', 'INCOME', 'TRANSFER'
    name  NVARCHAR(100) NOT NULL       -- Tên hiển thị
);

-- Seed data (giữ nguyên ID cũ)
INSERT INTO transaction_types (id, code, name) VALUES
(0, 'EXPENSE',  N'Chi tiêu'),
(1, 'INCOME',   N'Thu nhập'),
(2, 'TRANSFER', N'Chuyển khoản');
```

### 5.2 Bảng `categories`

```sql
CREATE TABLE categories (
    id                  INT PRIMARY KEY,              -- Giữ nguyên: 1-19
    transaction_type_id INT NOT NULL,                 -- FK → transaction_types.id
    code                VARCHAR(50) NOT NULL UNIQUE,  -- 'FOOD', 'TRANSPORT', ...
    name                NVARCHAR(100) NOT NULL,       -- Tên tiếng Anh
    name_vi             NVARCHAR(100),                -- Tên tiếng Việt
    icon                NVARCHAR(10),                 -- Emoji icon
    is_system           BIT NOT NULL DEFAULT 1,       -- Danh mục hệ thống (không xóa được)
    is_active           BIT NOT NULL DEFAULT 1,       -- Đang hoạt động
    display_order       INT DEFAULT 0,                -- Thứ tự hiển thị
    
    CONSTRAINT FK_category_type FOREIGN KEY (transaction_type_id) 
        REFERENCES transaction_types(id)
);

-- Seed data (giữ nguyên ID cũ → KHÔNG cần migrate data bảng transactions)
INSERT INTO categories (id, transaction_type_id, code, name, name_vi, icon, is_system) VALUES
-- Expense categories
(1,  0, 'FOOD',          'Food',          N'Ăn uống',           N'🍜', 1),
(2,  0, 'TRANSPORT',     'Transport',     N'Di chuyển',         N'🛵', 1),
(3,  0, 'ENTERTAINMENT', 'Entertainment', N'Giải trí',          N'🎮', 1),
(4,  0, 'UTILITIES',     'Utilities',     N'Hóa đơn / Tiện ích', N'💡', 1),
(5,  0, 'HEALTHCARE',    'Healthcare',    N'Sức khỏe',          N'💊', 1),
(6,  0, 'EDUCATION',     'Education',     N'Học tập',           N'📚', 1),
(7,  0, 'SHOPPING',      'Shopping',      N'Mua sắm',          N'🛍️', 1),
(8,  0, 'HOUSING',       'Housing',       N'Nhà ở',            N'🏠', 1),
(9,  0, 'DEBT',          'Debt',          N'Sổ nợ',            N'💳', 1),
(10, 0, 'OTHER_EXPENSE', 'Other Expense', N'Chi tiêu khác',    N'📦', 1),
(18, 0, 'TRAVELING',     'Traveling',     N'Du lịch',          N'✈️', 1),
(19, 0, 'PRESENT',       'Present',       N'Quà tặng',         N'🎁', 1),
-- Income categories
(11, 1, 'SALARY',        'Salary',        N'Lương',            N'💵', 1),
(12, 1, 'BUSINESS',      'Business',      N'Kinh doanh',       N'💼', 1),
(13, 1, 'INVESTMENTS',   'Investments',   N'Đầu tư / Lãi',    N'📈', 1),
(14, 1, 'GIFTS',         'Gifts',         N'Thưởng / Quà',     N'🎁', 1),
(15, 1, 'OTHER_INCOME',  'Other Income',  N'Thu nhập khác',    N'💰', 1),
-- Transfer category
(16, 2, 'TRANSFER',      'Transfer',      N'Chuyển khoản',     N'🔄', 1),
-- Savings category
(17, 0, 'SAVINGS',       'Savings',       N'Tiết kiệm',       N'🏦', 1);
```

### 5.3 Thêm FK cho bảng hiện tại

```sql
-- Thêm FK constraint (data hiện tại đã khớp ID → không lỗi)
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
```

---

## 6. Chiến Lược Migration (5 Phase)

### Phase 1: Tạo bảng + Seed data
- Tạo bảng `transaction_types` và `categories`
- Seed toàn bộ data với **cùng ID** như constant hiện tại
- Thêm FK constraint
- ✅ **Không cần migrate data** vì ID không đổi!

### Phase 2: Tạo Entity + Repository + Service mới
- Tạo `TransactionTypeEntity`, `CategoryEntity`
- Tạo `TransactionTypeRepository`, `CategoryRepository`
- Tạo `CategoryService` với `@Cacheable` để không ảnh hưởng performance
- Tạo `TransactionTypeService` tương tự

### Phase 3: Refactor Service Layer
- Thay `TransactionType.EXPENSE` → `transactionTypeService.getExpenseId()` hoặc giữ constant wrapper
- Thay `Category.isExpense(id)` → `categoryService.isExpense(id)` (query DB, cached)
- Thay `Category.getName(id)` → `categoryService.getName(id)`
- Thay `getCategoryVietnameseName()` → lấy từ `categories.name_vi`

### Phase 4: Refactor Repository Queries
- Thay magic numbers `0`, `1`, `9`, `16` bằng tham số
- Hoặc dùng subquery: `t.type = (SELECT id FROM transaction_types WHERE code = 'EXPENSE')`
- Cân nhắc giữ `int` constant cho các query đã ổn định

### Phase 5: Thêm API quản lý + Dọn dẹp
- Tạo CRUD API cho Category (admin)
- Xóa file `TransactionType.java` và `Category.java` (hoặc giữ làm fallback)
- Cập nhật test

---

## 7. So Sánh Trước & Sau

| Tiêu chí | Hiện tại (Constant) | Sau migration (DB) |
|----------|---------------------|---------------------|
| Thêm category mới | Sửa code → build → deploy | INSERT vào DB → xong |
| Đa ngôn ngữ | ❌ Hardcode tên | ✅ Cột `name_vi`, `name_en`, ... |
| Custom category/user | ❌ Không thể | ✅ Thêm `user_id` nullable |
| Performance | ⚡ Nhanh nhất (compile-time) | 🟡 Thêm 1 query (cache giải quyết) |
| Data integrity | ⚠️ Không có FK | ✅ FK constraint |
| Code complexity | Đơn giản | Phức tạp hơn một chút |
| Backward compatible | N/A | ✅ Hoàn toàn, nếu giữ cùng ID |
| Quản lý từ UI | ❌ | ✅ |

---

## 8. Kết Luận & Khuyến Nghị

| Câu hỏi | Trả lời |
|----------|---------|
| Có ảnh hưởng nặng không? | **Trung bình-Cao** về số file cần sửa (~9 files, ~105 dòng), nhưng **không breaking** nếu giữ cùng ID |
| Có nên làm không? | **Nên làm**, đặc biệt nếu dự định mở rộng nghiệp vụ |
| Rủi ro lớn nhất? | ~35 native queries hardcode magic numbers trong `TransactionRepository.java` |
| Thời gian ước tính? | ~2-3 ngày làm việc nếu cẩn thận |

**Key insight:** Vì hệ thống hiện tại dùng `int` cho cả `type` và `category`, ta có thể **giữ nguyên kiểu dữ liệu** và **giữ nguyên giá trị ID** khi tạo bảng mới. Điều này có nghĩa là **không cần migrate data** trong bảng `transactions` — chỉ cần tạo bảng lookup mới và thêm FK. Đây là điểm giảm thiểu rủi ro lớn nhất.
