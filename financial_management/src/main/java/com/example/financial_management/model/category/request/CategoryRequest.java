package com.example.financial_management.model.category.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @Schema(description = "ID danh mục (Tùy chọn. Nếu không truyền, hệ thống sẽ tự sinh theo block ID: 1xx Chi tiêu, 2xx Thu nhập, 3xx Chuyển khoản)", example = "113")
    private Integer id;

    @NotNull(message = "Loại giao dịch (transactionTypeId) không được để trống")
    @Schema(description = "Loại giao dịch: 1: Chi tiêu, 2: Thu nhập, 3: Chuyển khoản", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer transactionTypeId;

    @Schema(description = "Mã hệ thống của danh mục (Tùy chọn, tự động sinh nếu để trống)", example = "PET_CARE")
    private String code;

    @NotBlank(message = "Tên danh mục (name) không được để trống")
    @Schema(description = "Tên tiếng Anh hoặc tên hiển thị chính", example = "Pet Care", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Tên tiếng Việt hiển thị trên giao diện", example = "Chăm sóc thú cưng")
    private String nameVi;

    @Schema(description = "Class Bootstrap Icon", example = "bi-tencent-qq")
    private String icon;

    @Schema(description = "Mã màu HEX hiển thị trên biểu đồ và badge", example = "#e91e63")
    private String color;

    @Schema(description = "Thứ tự hiển thị trong nhóm", example = "13")
    private Integer displayOrder;

    @Schema(description = "Đánh dấu là danh mục hệ thống (mặc định false)", example = "false")
    @Builder.Default
    private Boolean isSystem = false;

    @Schema(description = "Trạng thái kích hoạt (mặc định true)", example = "true")
    @Builder.Default
    private Boolean isActive = true;
}
