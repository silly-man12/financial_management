package com.example.financial_management.telegram;

import com.example.financial_management.constant.Category;
import com.example.financial_management.constant.TransactionType;
import com.example.financial_management.entity.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class TelegramMessageParser {

    /**
     * Regex nhận diện số tiền cực kỳ linh hoạt:
     * 1. Số có dấu phân cách nghìn: 127.500, 127,500, 1.250.000, 1,250,000
     * 2. Số kèm đơn vị k, nghin, tr, trieu, m, ty: 50k, 45.5k, 1.5tr, 12.3tr, 33 nghin, 2trieu, 1ty
     * 3. Số nguyên độc lập: 32500, 127500, 1500000, 75000, 20000
     * 4. Số kèm đơn vị đ/d/vnd: 50000d, 50000đ, 50000vnd
     */
    // Pattern 1: Số có dấu phân cách hàng nghìn (ví dụ 1.250.000 hoặc 127,500 hoặc 32.500)
    private static final Pattern THOUSAND_SEP_PATTERN =
            Pattern.compile("(\\b|\\+|-)(\\d{1,3}([.,]\\d{3})+)(\\s*(d|đ|vnd))?\\b", Pattern.CASE_INSENSITIVE);

    // Pattern 2: Số có đơn vị k, nghìn, tr, triệu, m, tỷ (ví dụ 50k, 45.5k, 1.5tr, 12.3tr, 2m, 1ty, 33 nghin)
    private static final Pattern UNIT_AMOUNT_PATTERN =
            Pattern.compile("(\\b|\\+|-)(\\d+([.,]\\d+)?)\\s*(k|nghin|nghìn|ngan|ngàn|tr|trieu|triệu|m|ty|tỷ|b)\\b", Pattern.CASE_INSENSITIVE);

    // Pattern 3: Số nguyên độc lập (ví dụ 32500, 127500, 50000, hoặc số nhỏ kèm vnd)
    private static final Pattern RAW_NUMBER_PATTERN =
            Pattern.compile("(\\b|\\+|-)(\\d+)(\\s*(d|đ|vnd))?\\b", Pattern.CASE_INSENSITIVE);

    public static class ParseAmountResult {
        public BigDecimal amount;
        public String sign; // "+", "-", or null
        public String matchedText;
        public int startIndex;
        public int endIndex;
    }

    /**
     * Bóc tách số tiền từ bất kỳ vị trí nào trong câu nhắn
     */
    public ParseAmountResult extractAmount(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        // Ưu tiên 1: Số có đơn vị rõ ràng (ví dụ: 45.5k, 1.5tr, 50k, 12.3tr)
        Matcher unitMatcher = UNIT_AMOUNT_PATTERN.matcher(input);
        if (unitMatcher.find()) {
            String sign = unitMatcher.group(1);
            String numStr = unitMatcher.group(2).replace(',', '.');
            String unit = unitMatcher.group(4).toLowerCase();

            try {
                double base = Double.parseDouble(numStr);
                double multiplier = switch (unit) {
                    case "k", "nghin", "nghìn", "ngan", "ngàn" -> 1_000.0;
                    case "tr", "trieu", "triệu", "m" -> 1_000_000.0;
                    case "ty", "tỷ", "b" -> 1_000_000_000.0;
                    default -> 1.0;
                };

                BigDecimal total = BigDecimal.valueOf((long) Math.round(base * multiplier));
                ParseAmountResult res = new ParseAmountResult();
                res.amount = total;
                res.sign = sign != null && !sign.isEmpty() ? sign.trim() : null;
                res.matchedText = unitMatcher.group(0);
                res.startIndex = unitMatcher.start();
                res.endIndex = unitMatcher.end();
                return res;
            } catch (Exception ignored) {
            }
        }

        // Ưu tiên 2: Số có dấu phân cách hàng nghìn (ví dụ: 127.500 hoặc 1.250.000)
        Matcher sepMatcher = THOUSAND_SEP_PATTERN.matcher(input);
        if (sepMatcher.find()) {
            String sign = sepMatcher.group(1);
            String rawNumber = sepMatcher.group(2).replaceAll("[.,]", "");

            try {
                long val = Long.parseLong(rawNumber);
                ParseAmountResult res = new ParseAmountResult();
                res.amount = BigDecimal.valueOf(val);
                res.sign = sign != null && !sign.isEmpty() ? sign.trim() : null;
                res.matchedText = sepMatcher.group(0);
                res.startIndex = sepMatcher.start();
                res.endIndex = sepMatcher.end();
                return res;
            } catch (Exception ignored) {
            }
        }

        // Ưu tiên 3: Số nguyên thông thường (ví dụ: 32500, 127500, 50000, 20000)
        Matcher rawMatcher = RAW_NUMBER_PATTERN.matcher(input);
        while (rawMatcher.find()) {
            String sign = rawMatcher.group(1);
            String digits = rawMatcher.group(2);
            String unit = rawMatcher.group(4); // d, đ, vnd

            try {
                long val = Long.parseLong(digits);
                if (val <= 0) continue;

                // Nếu người dùng gõ số < 1000 và không có đơn vị đ/vnd (ví dụ: gõ "35 com trua" thay vì "35k")
                // Trong văn hóa ghi chép chi tiêu VN, số < 1000 không đơn vị được hiểu là nghìn (k)
                if (val < 1000 && (unit == null || unit.isEmpty())) {
                    val = val * 1000;
                }

                ParseAmountResult res = new ParseAmountResult();
                res.amount = BigDecimal.valueOf(val);
                res.sign = sign != null && !sign.isEmpty() ? sign.trim() : null;
                res.matchedText = rawMatcher.group(0);
                res.startIndex = rawMatcher.start();
                res.endIndex = rawMatcher.end();
                return res;
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    /**
     * Bóc tách toàn bộ thông điệp giao dịch thành DTO
     */
    public TelegramParsedTransaction parse(String messageText, List<Account> activeAccounts) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return TelegramParsedTransaction.builder()
                    .success(false)
                    .errorMessage("Tin nhắn trống.")
                    .build();
        }

        String raw = messageText.trim();

        // 1. Trích xuất số tiền
        ParseAmountResult amountResult = extractAmount(raw);
        if (amountResult == null || amountResult.amount == null || amountResult.amount.compareTo(BigDecimal.ZERO) <= 0) {
            return TelegramParsedTransaction.builder()
                    .success(false)
                    .rawInput(raw)
                    .errorMessage("Không nhận diện được số tiền hợp lệ. Ví dụ cú pháp: <code>50k cf</code>, <code>32500 banh mi</code>, <code>127.500 sieu thi</code>, <code>+15tr luong</code>.")
                    .build();
        }

        // 2. Loại bỏ phần số tiền khỏi chuỗi để lấy phần văn bản còn lại
        String remainingText = raw.substring(0, amountResult.startIndex) + " " + raw.substring(amountResult.endIndex);
        remainingText = remainingText.replaceAll("\\s+", " ").trim();

        // 3. Xác định loại giao dịch: EXPENSE vs INCOME
        int type = TransactionType.EXPENSE; // mặc định chi tiêu
        String lowerRaw = raw.toLowerCase();
        if ("+".equals(amountResult.sign) || lowerRaw.startsWith("+") || lowerRaw.startsWith("thu ") || lowerRaw.startsWith("nhan ")) {
            type = TransactionType.INCOME;
        } else if ("-".equals(amountResult.sign) || lowerRaw.startsWith("-") || lowerRaw.startsWith("chi ")) {
            type = TransactionType.EXPENSE;
        }

        // Bỏ các từ tiền tố "chi", "thu", "nhan" ở đầu remainingText nếu có
        remainingText = remainingText.replaceAll("^(?i)(chi|thu|nhan)\\s+", "");

        // 4. Tìm kiếm ví / tài khoản khớp với danh sách ví của người dùng
        String matchedAccountKeyword = null;
        if (activeAccounts != null && !activeAccounts.isEmpty()) {
            for (Account acc : activeAccounts) {
                String accName = acc.getName();
                List<String> keywords = generateAccountKeywords(accName);
                for (String kw : keywords) {
                    Pattern kwPattern = Pattern.compile("(?i)\\b" + Pattern.quote(kw) + "\\b");
                    Matcher m = kwPattern.matcher(remainingText);
                    if (m.find()) {
                        matchedAccountKeyword = acc.getName();
                        // Xóa từ khóa ví khỏi ghi chú để ghi chú sạch sẽ
                        remainingText = m.replaceFirst("").replaceAll("\\s+", " ").trim();
                        break;
                    }
                }
                if (matchedAccountKeyword != null) break;
            }
        }

        // 4.1 Nếu không khớp ví cụ thể nào của người dùng, kiểm tra các ví / ngân hàng phổ biến
        if (matchedAccountKeyword == null) {
            Map<String, String> knownWallets = getKnownWalletAliases();
            for (Map.Entry<String, String> entry : knownWallets.entrySet()) {
                String alias = entry.getKey();
                Pattern kwPattern = Pattern.compile("(?i)\\b" + Pattern.quote(alias) + "\\b");
                Matcher m = kwPattern.matcher(remainingText);
                if (m.find()) {
                    matchedAccountKeyword = entry.getValue();
                    remainingText = m.replaceFirst("").replaceAll("\\s+", " ").trim();
                    break;
                }
            }
        }

        // 5. Nhận diện danh mục thông minh
        String normalizedRemaining = removeDiacritics(remainingText.toLowerCase());
        int detectedCategory = detectCategory(normalizedRemaining, type);

        // Nếu từ khóa nhận diện được danh mục Thu nhập (như Lương, Thưởng, Lãi...), tự động chuyển type sang INCOME
        if (Category.isIncome(detectedCategory)) {
            type = TransactionType.INCOME;
        }

        // 6. Chuẩn hóa ghi chú (Description)
        String description = remainingText.trim();
        if (description.isEmpty()) {
            description = Category.getName(detectedCategory);
        } else {
            // Viết hoa chữ cái đầu tiên cho đẹp
            description = Character.toUpperCase(description.charAt(0)) + description.substring(1);
        }

        return TelegramParsedTransaction.builder()
                .success(true)
                .amount(amountResult.amount)
                .type(type)
                .category(detectedCategory)
                .categoryName(getCategoryVietnameseName(detectedCategory))
                .accountKeyword(matchedAccountKeyword)
                .description(description)
                .rawInput(raw)
                .build();
    }

    private Map<String, String> getKnownWalletAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("zalopay", "ZaloPay");
        map.put("zalo pay", "ZaloPay");
        map.put("momo", "MoMo");
        map.put("vietcombank", "Vietcombank");
        map.put("vcb", "Vietcombank");
        map.put("techcombank", "Techcombank");
        map.put("tcb", "Techcombank");
        map.put("mbbank", "MB Bank");
        map.put("mb bank", "MB Bank");
        map.put("mb", "MB Bank");
        map.put("tien mat", "Tiền mặt");
        map.put("tiền mặt", "Tiền mặt");
        map.put("cash", "Tiền mặt");
        map.put("tpbank", "TPBank");
        map.put("tpb", "TPBank");
        map.put("vpbank", "VPBank");
        map.put("vpb", "VPBank");
        map.put("bidv", "BIDV");
        map.put("acb", "ACB");
        map.put("shopeepay", "ShopeePay");
        map.put("agribank", "Agribank");
        map.put("vietinbank", "VietinBank");
        return map;
    }

    /**
     * Sinh danh sách từ khóa viết tắt cho tên tài khoản / ngân hàng
     */
    private List<String> generateAccountKeywords(String accountName) {
        Set<String> keywords = new LinkedHashSet<>();
        if (accountName == null) return new ArrayList<>();
        String lower = accountName.trim().toLowerCase();
        keywords.add(lower);

        String noSpace = lower.replaceAll("\\s+", "");
        keywords.add(noSpace);
        String withoutAccents = removeDiacritics(lower);
        keywords.add(withoutAccents);
        keywords.add(withoutAccents.replaceAll("\\s+", ""));

        // Sinh các từ viết tắt phổ biến
        if (noSpace.contains("vietcombank") || noSpace.contains("vcb")) {
            keywords.add("vcb");
            keywords.add("vietcombank");
            keywords.add("vietcom bank");
        }
        if (noSpace.contains("techcombank") || noSpace.contains("tcb")) {
            keywords.add("tcb");
            keywords.add("techcombank");
            keywords.add("techcom bank");
        }
        if (noSpace.contains("mbbank") || noSpace.equals("mb") || lower.contains("mb bank")) {
            keywords.add("mb");
            keywords.add("mbbank");
            keywords.add("mb bank");
        }
        if (noSpace.contains("tienmat") || noSpace.contains("cash")) {
            keywords.add("tien mat");
            keywords.add("tiền mặt");
            keywords.add("cash");
        }
        if (noSpace.contains("momo")) {
            keywords.add("momo");
        }
        if (noSpace.contains("zalopay") || noSpace.contains("zalo")) {
            keywords.add("zalopay");
            keywords.add("zalo pay");
            keywords.add("zalo");
        }
        if (noSpace.contains("vpbank") || noSpace.contains("vpb")) {
            keywords.add("vpb");
            keywords.add("vpbank");
            keywords.add("vp bank");
        }
        if (noSpace.contains("tpbank") || noSpace.contains("tpb")) {
            keywords.add("tpb");
            keywords.add("tpbank");
            keywords.add("tp bank");
        }
        if (noSpace.contains("bidv")) {
            keywords.add("bidv");
        }
        if (noSpace.contains("acb")) {
            keywords.add("acb");
        }
        if (noSpace.contains("shopeepay")) {
            keywords.add("shopeepay");
            keywords.add("shopee pay");
        }
        if (noSpace.contains("agribank")) {
            keywords.add("agribank");
            keywords.add("agri");
        }
        if (noSpace.contains("vietinbank") || noSpace.contains("ctg")) {
            keywords.add("vietinbank");
            keywords.add("vietin");
            keywords.add("ctg");
        }

        return new ArrayList<>(keywords);
    }

    /**
     * Bảng từ điển nhận diện danh mục tài chính thông minh
     */
    private int detectCategory(String text, int transactionType) {
        if (text == null || text.isEmpty()) {
            return transactionType == TransactionType.INCOME ? Category.OTHER_INCOME : Category.OTHER_EXPENSE;
        }

        // Danh mục Thu nhập
        if (matchesAny(text, "luong", "salary", "cty tra", "tinh luong", "ting ting", "cong ty", "luong cty")) {
            return Category.SALARY;
        }
        if (matchesAny(text, "thuong", "li xi", "qua", "bieu", "tang", "mung tuoi", "thuong tet", "thuong quy")) {
            return Category.GIFTS;
        }
        if (matchesAny(text, "lai", "co phieu", "chung khoan", "crypto", "vang", "tiet kiem", "co tuc", "invest", "bds")) {
            return Category.INVESTMENTS;
        }
        if (matchesAny(text, "ban hang", "kinh doanh", "doanh thu", "khach tra", "don hang", "tien hang")) {
            return Category.BUSINESS;
        }

        // Danh mục Chi tiêu
        if (matchesAny(text, "cf", "cafe", "ca phe", "com", "an", "trua", "toi", "sang", "tra sua", "pho", "bun", "mi", "banh", "lau", "nuong", "thit", "rau", "sieu thi", "cho", "winmart", "coopmart", "highlands", "starbucks", "phuc long", "snack", "do an", "nuoc", "uong", "bia", "nhau", "banh mi", "do an vat", "kem", "che", "hai san")) {
            return Category.FOOD;
        }
        if (matchesAny(text, "xang", "grab", "be", "gojek", "gui xe", "ve xe", "rua xe", "taxi", "ben xe", "bus", "buyt", "sua xe", "nhot", "cau duong", "phi cau duong", "thay nhot")) {
            return Category.TRANSPORT;
        }
        if (matchesAny(text, "xem phim", "rap", "cinema", "cgv", "netflix", "spotify", "game", "steam", "bida", "karaoke", "du lich", "bar", "concert", "di choi", "giai tri", "bowling")) {
            return Category.ENTERTAINMENT;
        }
        if (matchesAny(text, "dien", "nuoc", "wifi", "mang", "internet", "rac", "dien thoai", "cuoc", "nap the", "4g", "5g", "gas", "hoa don", "tien mang")) {
            return Category.UTILITIES;
        }
        if (matchesAny(text, "thuoc", "kham", "bac si", "gym", "benh vien", "nha khoa", "rang", "y te", "mat kinh", "yoga", "kham benh")) {
            return Category.HEALTHCARE;
        }
        if (matchesAny(text, "sach", "hoc phi", "khoa hoc", "course", "but", "vo", "tai lieu", "hoc tap", "hoc lai", "thi cu")) {
            return Category.EDUCATION;
        }
        if (matchesAny(text, "shopee", "lazada", "tiktok", "tiki", "quan ao", "giay", "dep", "do dung", "linh kien", "tai nghe", "chuot", "ban phim", "my pham", "son", "dong ho", "mua sam", "ao", "quan", "vay")) {
            return Category.SHOPPING;
        }
        if (matchesAny(text, "tien nha", "phong tro", "thue nha", "tien phong", "coc nha", "noi that", "do gia dung", "sua nha")) {
            return Category.HOUSING;
        }
        if (matchesAny(text, "ve may bay", "khach san", "resort", "homestay", "tour", "ve tau", "vali")) {
            return Category.TRAVELING;
        }
        if (matchesAny(text, "tra no", "no", "vay", "muon", "debt")) {
            return Category.DEBT;
        }

        // Mặc định
        return transactionType == TransactionType.INCOME ? Category.OTHER_INCOME : Category.OTHER_EXPENSE;
    }

    private boolean matchesAny(String text, String... keywords) {
        for (String kw : keywords) {
            Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(kw) + "\\b");
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Chuyển chuỗi tiếng Việt có dấu thành không dấu để so khớp
     */
    public String removeDiacritics(String str) {
        if (str == null) return "";
        String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String withoutAccents = pattern.matcher(nfdNormalizedString).replaceAll("");
        return withoutAccents.replace('đ', 'd').replace('Đ', 'D');
    }

    public String getCategoryVietnameseName(int categoryId) {
        return switch (categoryId) {
            case Category.FOOD -> "Ăn uống 🍜";
            case Category.TRANSPORT -> "Di chuyển 🛵";
            case Category.ENTERTAINMENT -> "Giải trí 🎮";
            case Category.UTILITIES -> "Hóa đơn / Tiện ích 💡";
            case Category.HEALTHCARE -> "Sức khỏe 💊";
            case Category.EDUCATION -> "Học tập 📚";
            case Category.SHOPPING -> "Mua sắm 🛍️";
            case Category.HOUSING -> "Nhà ở 🏠";
            case Category.DEBT -> "Sổ nợ 💳";
            case Category.TRAVELING -> "Du lịch ✈️";
            case Category.SALARY -> "Lương 💵";
            case Category.BUSINESS -> "Kinh doanh 💼";
            case Category.INVESTMENTS -> "Đầu tư / Lãi 📈";
            case Category.GIFTS -> "Thưởng / Quà 🎁";
            case Category.OTHER_INCOME -> "Thu nhập khác 💰";
            default -> "Chi tiêu khác 🏷️";
        };
    }
}
