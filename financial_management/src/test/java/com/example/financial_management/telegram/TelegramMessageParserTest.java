package com.example.financial_management.telegram;

import com.example.financial_management.constant.Category;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TelegramMessageParserTest {

    private TelegramMessageParser parser;
    private List<Account> testAccounts;

    @BeforeEach
    void setUp() {
        parser = new TelegramMessageParser();

        Account vcb = new Account();
        vcb.setName("Vietcombank");

        Account cash = new Account();
        cash.setName("Tiền mặt");

        Account momo = new Account();
        momo.setName("MoMo");

        Account mb = new Account();
        mb.setName("MB Bank");

        testAccounts = List.of(vcb, cash, momo, mb);
    }

    @Test
    @DisplayName("Tùy ý nhập số tiền nguyên bất kỳ không đơn vị (ví dụ 32500, 127500, 1234567)")
    void testArbitraryRawAmounts() {
        TelegramParsedTransaction res1 = parser.parse("32500 banh mi", testAccounts);
        assertTrue(res1.isSuccess());
        assertEquals(0, new BigDecimal("32500").compareTo(res1.getAmount()));
        assertEquals(Category.FOOD, res1.getCategory());
        assertEquals(TransactionType.EXPENSE, res1.getType());

        TelegramParsedTransaction res2 = parser.parse("127500 mua do sieu thi", testAccounts);
        assertTrue(res2.isSuccess());
        assertEquals(0, new BigDecimal("127500").compareTo(res2.getAmount()));
        assertEquals(Category.FOOD, res2.getCategory());

        TelegramParsedTransaction res3 = parser.parse("1234567 thuong du an", testAccounts);
        assertTrue(res3.isSuccess());
        assertEquals(0, new BigDecimal("1234567").compareTo(res3.getAmount()));
        assertEquals(Category.GIFTS, res3.getCategory());
        assertEquals(TransactionType.INCOME, res3.getType());
    }

    @Test
    @DisplayName("Tùy ý nhập số tiền có dấu chấm/phẩy phân cách hàng nghìn (ví dụ 32.500, 127.500, 1.250.000)")
    void testThousandSeparatorAmounts() {
        TelegramParsedTransaction res1 = parser.parse("32.500 banh mi thit", testAccounts);
        assertTrue(res1.isSuccess());
        assertEquals(0, new BigDecimal("32500").compareTo(res1.getAmount()));

        TelegramParsedTransaction res2 = parser.parse("127.500 di cho", testAccounts);
        assertTrue(res2.isSuccess());
        assertEquals(0, new BigDecimal("127500").compareTo(res2.getAmount()));

        TelegramParsedTransaction res3 = parser.parse("1,250,000 hoc phi tieng anh", testAccounts);
        assertTrue(res3.isSuccess());
        assertEquals(0, new BigDecimal("1250000").compareTo(res3.getAmount()));
        assertEquals(Category.EDUCATION, res3.getCategory());
    }

    @Test
    @DisplayName("Nhập số tiền có số thập phân và đơn vị (ví dụ 45.5k, 1.5tr, 12.3tr)")
    void testDecimalWithUnits() {
        TelegramParsedTransaction res1 = parser.parse("45.5k cafe sang", testAccounts);
        assertTrue(res1.isSuccess());
        assertEquals(0, new BigDecimal("45500").compareTo(res1.getAmount()));
        assertEquals(Category.FOOD, res1.getCategory());

        TelegramParsedTransaction res2 = parser.parse("1.5tr tien nha", testAccounts);
        assertTrue(res2.isSuccess());
        assertEquals(0, new BigDecimal("1500000").compareTo(res2.getAmount()));
        assertEquals(Category.HOUSING, res2.getCategory());

        TelegramParsedTransaction res3 = parser.parse("12.3tr sua xe oto", testAccounts);
        assertTrue(res3.isSuccess());
        assertEquals(0, new BigDecimal("12300000").compareTo(res3.getAmount()));
        assertEquals(Category.TRANSPORT, res3.getCategory());
    }

    @Test
    @DisplayName("Số tiền ở cuối câu nhắn (ví dụ: banh mi 32500, cf 45k, luong +15tr)")
    void testAmountAtEnd() {
        TelegramParsedTransaction res1 = parser.parse("banh mi 32500", testAccounts);
        assertTrue(res1.isSuccess());
        assertEquals(0, new BigDecimal("32500").compareTo(res1.getAmount()));
        assertEquals(Category.FOOD, res1.getCategory());

        TelegramParsedTransaction res2 = parser.parse("cafe muoi 45k", testAccounts);
        assertTrue(res2.isSuccess());
        assertEquals(0, new BigDecimal("45000").compareTo(res2.getAmount()));

        TelegramParsedTransaction res3 = parser.parse("luong cty +15tr", testAccounts);
        assertTrue(res3.isSuccess());
        assertEquals(0, new BigDecimal("15000000").compareTo(res3.getAmount()));
        assertEquals(TransactionType.INCOME, res3.getType());
        assertEquals(Category.SALARY, res3.getCategory());
    }

    @Test
    @DisplayName("Nhận diện ví thanh toán từ câu nhắn (ví dụ: 35k com trua vcb)")
    void testAccountMatching() {
        TelegramParsedTransaction res = parser.parse("35k com trua vcb", testAccounts);
        assertTrue(res.isSuccess());
        assertEquals(0, new BigDecimal("35000").compareTo(res.getAmount()));
        assertEquals("Vietcombank", res.getAccountKeyword());
        assertEquals("Com trua", res.getDescription());
    }

    @Test
    @DisplayName("Nhận diện ví ZaloPay và loại bỏ từ khóa ví khỏi ghi chú (ví dụ: 32500 banh mi zalopay)")
    void testZaloPayMatching() {
        TelegramParsedTransaction res = parser.parse("32500 banh mi zalopay", testAccounts);
        assertTrue(res.isSuccess());
        assertEquals(0, new BigDecimal("32500").compareTo(res.getAmount()));
        assertEquals(Category.FOOD, res.getCategory());
        assertEquals("ZaloPay", res.getAccountKeyword());
        assertEquals("Banh mi", res.getDescription());
    }

    @Test
    @DisplayName("Bóc tách hashtag đơn (ví dụ: xang 70k #di-lai) và loại bỏ hashtag khỏi ghi chú")
    void testSingleHashtagExtraction() {
        TelegramParsedTransaction res = parser.parse("xang 70k #di-lai", testAccounts);
        assertTrue(res.isSuccess());
        assertEquals(0, new BigDecimal("70000").compareTo(res.getAmount()));
        assertEquals(Category.TRANSPORT, res.getCategory());
        assertEquals("Xang", res.getDescription());
        assertNotNull(res.getTags());
        assertEquals(1, res.getTags().size());
        assertEquals("di-lai", res.getTags().get(0));
    }

    @Test
    @DisplayName("Bóc tách nhiều hashtag kết hợp chỉ định ví (ví dụ: an toi 150k #banbe #cuoituan vcb)")
    void testMultipleHashtagsWithWallet() {
        TelegramParsedTransaction res = parser.parse("an toi 150k #banbe #cuoituan vcb", testAccounts);
        assertTrue(res.isSuccess());
        assertEquals(0, new BigDecimal("150000").compareTo(res.getAmount()));
        assertEquals(Category.FOOD, res.getCategory());
        assertEquals("Vietcombank", res.getAccountKeyword());
        assertEquals("An toi", res.getDescription());
        assertNotNull(res.getTags());
        assertEquals(2, res.getTags().size());
        assertTrue(res.getTags().contains("banbe"));
        assertTrue(res.getTags().contains("cuoituan"));
    }

    @Test
    @DisplayName("Nhận diện danh mục PRESENT (Quà tặng, mừng cưới, biếu mẹ, biếu bố - EXPENSE)")
    void testPresentCategory() {
        // Đúng câu của người dùng: 1500k bieu me mb
        TelegramParsedTransaction resUser = parser.parse("1500k bieu me mb", testAccounts);
        assertTrue(resUser.isSuccess());
        assertEquals(0, new BigDecimal("1500000").compareTo(resUser.getAmount()));
        assertEquals(Category.PRESENT, resUser.getCategory());
        assertEquals(TransactionType.EXPENSE, resUser.getType());
        assertEquals("MB Bank", resUser.getAccountKeyword());
        assertEquals("Bieu me", resUser.getDescription());

        TelegramParsedTransaction res1 = parser.parse("mung cuoi ban 500k", testAccounts);
        assertTrue(res1.isSuccess());
        assertEquals(0, new BigDecimal("500000").compareTo(res1.getAmount()));
        assertEquals(Category.PRESENT, res1.getCategory());
        assertEquals(TransactionType.EXPENSE, res1.getType());

        TelegramParsedTransaction res2 = parser.parse("500k phong bi sinh nhat", testAccounts);
        assertTrue(res2.isSuccess());
        assertEquals(Category.PRESENT, res2.getCategory());
        assertEquals(TransactionType.EXPENSE, res2.getType());

        TelegramParsedTransaction res3 = parser.parse("bieu bo 500k vcb", testAccounts);
        assertTrue(res3.isSuccess());
        assertEquals(Category.PRESENT, res3.getCategory());
        assertEquals(TransactionType.EXPENSE, res3.getType());
        assertEquals("Vietcombank", res3.getAccountKeyword());

        TelegramParsedTransaction res4 = parser.parse("300k tang vo", testAccounts);
        assertTrue(res4.isSuccess());
        assertEquals(Category.PRESENT, res4.getCategory());
        assertEquals(TransactionType.EXPENSE, res4.getType());

        TelegramParsedTransaction res5 = parser.parse("200k li xi chau", testAccounts);
        assertTrue(res5.isSuccess());
        assertEquals(Category.PRESENT, res5.getCategory());
        assertEquals(TransactionType.EXPENSE, res5.getType());
    }

    @Test
    @DisplayName("Phân biệt đúng Quà biếu (PRESENT - Chi tiêu) vs Thưởng/Nhận quà (GIFTS - Thu nhập)")
    void testPresentVsGiftsIncome() {
        // Chi tiền mang đi biếu
        TelegramParsedTransaction exp = parser.parse("1500k bieu me", testAccounts);
        assertEquals(TransactionType.EXPENSE, exp.getType());
        assertEquals(Category.PRESENT, exp.getCategory());

        // Nhận tiền thưởng/lì xì có dấu + hoặc từ nhận
        TelegramParsedTransaction inc1 = parser.parse("+1500k bieu me", testAccounts);
        assertEquals(TransactionType.INCOME, inc1.getType());
        assertEquals(Category.GIFTS, inc1.getCategory());

        TelegramParsedTransaction inc2 = parser.parse("+500k li xi", testAccounts);
        assertEquals(TransactionType.INCOME, inc2.getType());
        assertEquals(Category.GIFTS, inc2.getCategory());

        TelegramParsedTransaction inc3 = parser.parse("duoc me cho 500k", testAccounts);
        assertEquals(TransactionType.INCOME, inc3.getType());
        assertEquals(Category.GIFTS, inc3.getCategory());

        // Thưởng công việc tự động là INCOME
        TelegramParsedTransaction inc4 = parser.parse("1tr thuong tet cty", testAccounts);
        assertEquals(TransactionType.INCOME, inc4.getType());
        assertEquals(Category.GIFTS, inc4.getCategory());
    }

    @Test
    @DisplayName("Từ khóa 'di lai' không bị nhầm lẫn thành Đầu tư/Lãi mà phải là Di chuyển")
    void testTransportNotConfusedWithInvestments() {
        TelegramParsedTransaction res = parser.parse("50k di lai", testAccounts);
        assertTrue(res.isSuccess());
        assertEquals(TransactionType.EXPENSE, res.getType());
        assertEquals(Category.TRANSPORT, res.getCategory());
    }
}
