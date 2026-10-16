package com.example.financial_management.model.backup;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cấu trúc file JSON backup.
 * Chứa toàn bộ dữ liệu của user dưới dạng raw entity data để có thể restore được.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupFileData {
    /** Phiên bản schema của backup file — dùng để kiểm tra tương thích khi restore */
    private String schemaVersion;
    /** Thời điểm export */
    private LocalDateTime exportedAt;
    /** ID của user sở hữu dữ liệu */
    private String userId;
    /** Email của user (để xác nhận khi restore) */
    private String userEmail;

    /** Danh sách tài khoản */
    private List<Map<String, Object>> accounts;
    /** Danh sách giao dịch */
    private List<Map<String, Object>> transactions;
    /** Danh sách giao dịch định kỳ */
    private List<Map<String, Object>> recurringTransactions;
    /** Danh sách ngân sách */
    private List<Map<String, Object>> budgets;
    /** Danh sách mục tiêu tiết kiệm */
    private List<Map<String, Object>> savingGoals;
    /** Danh sách lần đóng góp tiết kiệm */
    private List<Map<String, Object>> savingGoalContributions;
    /** Danh sách tag */
    private List<Map<String, Object>> tags;
    /** Danh sách khoản nợ */
    private List<Map<String, Object>> debts;
    /** Danh sách thanh toán nợ */
    private List<Map<String, Object>> debtPayments;
}
