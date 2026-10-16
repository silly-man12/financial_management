package com.example.financial_management.model.backup;

import lombok.Builder;
import lombok.Data;

/**
 * Response sau khi import backup thành công.
 */
@Data
@Builder
public class BackupImportResponse {
    /** "success" | "partial" | "failed" */
    private String status;
    /** Số lượng record đã import theo từng entity */
    private ImportedCounts imported;
    /** Số record bị bỏ qua (trùng id hoặc conflict) */
    private int skipped;
    /** Số lỗi gặp phải khi import */
    private int errors;
    /** Mô tả lỗi (nếu có) */
    private java.util.List<String> errorMessages;
    /**
     * Token xác nhận (chỉ xuất hiện khi mode=overwrite để yêu cầu confirm thêm 1 bước).
     * Frontend cần gửi lại token này trong bước xác nhận cuối.
     */
    private String confirmationToken;

    @Data
    @Builder
    public static class ImportedCounts {
        private int accounts;
        private int transactions;
        private int recurringTransactions;
        private int budgets;
        private int savingGoals;
        private int savingGoalContributions;
        private int tags;
        private int debts;
        private int debtPayments;
    }
}
