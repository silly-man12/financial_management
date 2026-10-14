package com.example.financial_management.model.telegram;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cập nhật cài đặt nhận thông báo Telegram")
public class TelegramSettingsRequest {

    @Schema(description = "Bật/tắt nhận báo cáo tổng kết chi tiêu 21:30 hàng ngày", example = "true")
    private Boolean dailyReportEnabled;

    @Schema(description = "Bật/tắt cảnh báo tức thì khi chi tiêu vượt quá 90% ngân sách", example = "true")
    private Boolean budgetAlertEnabled;
}
