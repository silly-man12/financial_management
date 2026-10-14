package com.example.financial_management.model.telegram;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin mã OTP và link kết nối Telegram an toàn")
public class TelegramLinkCodeResponse {

    @Schema(description = "Mã OTP 6 chữ số dùng để liên kết với bot", example = "849201")
    private String code;

    @Schema(description = "Thời gian hết hạn của mã (10 phút kể từ lúc tạo)", example = "2026-10-13T17:40:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiredAt;

    @Schema(description = "Đường dẫn Deep Link trực tiếp để mở bot và tự động điền mã", example = "https://t.me/bang_linh_bot?start=LINK_849201")
    private String deepLink;
}
