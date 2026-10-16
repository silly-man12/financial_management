package com.example.financial_management.model.backup;

import lombok.Builder;
import lombok.Data;

/**
 * Response sau khi validate file backup (GET /backup/validate).
 */
@Data
@Builder
public class BackupValidateResponse {
    /** File có hợp lệ không */
    private boolean valid;
    /** Phiên bản schema của file backup */
    private String schemaVersion;
    /** Email user của file backup (để người dùng xác nhận đúng file) */
    private String userEmail;
    /** Thống kê số record trong file backup */
    private BackupImportResponse.ImportedCounts recordCounts;
    /** Thông báo lỗi nếu file không hợp lệ */
    private String errorMessage;
}
