package com.example.financial_management.model.report.request;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ReportRequest {
    @Schema(description = "Tháng báo cáo (định dạng MM-yyyy, yyyy-MM hoặc MM/yyyy)", example = "09-2026")
    private String month;

    @Schema(description = "ID tài khoản ví (tùy chọn)", example = "304f29a9-5cf1-452d-ad25-44482a03fa1e")
    private UUID accountId;
}
