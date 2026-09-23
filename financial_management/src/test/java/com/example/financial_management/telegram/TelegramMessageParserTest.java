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

        testAccounts = List.of(vcb, cash, momo);
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
}
