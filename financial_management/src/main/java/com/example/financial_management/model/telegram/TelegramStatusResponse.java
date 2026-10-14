package com.example.financial_management.model.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin trạng thái kết nối Telegram của người dùng")
public class TelegramStatusResponse {

    @JsonProperty("isLinked")
    @Schema(description = "Trạng thái đã liên kết Telegram hay chưa", example = "true")
    private boolean isLinked;

    @Schema(description = "Chat ID Telegram của người dùng", example = "987654321")
    private Long telegramChatId;

    @Schema(description = "Tên tài khoản (username) của bot Telegram", example = "bang_linh_bot")
    private String botUsername;

    @Schema(description = "Bật/tắt nhận báo cáo tổng kết chi tiêu 21:30 hàng ngày", example = "true")
    private boolean dailyReportEnabled;

    @Schema(description = "Bật/tắt cảnh báo tức thì khi chi tiêu vượt quá 90% ngân sách", example = "true")
    private boolean budgetAlertEnabled;

    @Schema(description = "Thời gian gửi báo cáo tổng kết hàng ngày", example = "21:30")
    private String reportTime;
}
