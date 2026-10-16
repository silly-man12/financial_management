package com.example.financial_management.model.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body cho POST /backup/export
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupExportRequest {
    /**
     * Format file xuất: "json" (mặc định).
     * Hiện tại chỉ hỗ trợ JSON. CSV có thể mở rộng sau.
     */
    private String format;

    /**
     * Có bao gồm đường dẫn ảnh giao dịch không (mặc định = false).
     * Nếu true, imagePath sẽ được đưa vào backup.
     */
    private boolean includeImages;
}
