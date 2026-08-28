package com.example.financial_management.model.report.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Báo cáo tổng hợp KPI và xu hướng dòng tiền")
public class AnalyticsReportResponse {

    @Schema(description = "Chỉ số KPI tổng hợp")
    private AnalyticsKpiResponse kpi;

    @Schema(description = "Danh sách điểm dữ liệu vẽ biểu đồ")
    private List<AnalyticsChartPoint> chart;
}
