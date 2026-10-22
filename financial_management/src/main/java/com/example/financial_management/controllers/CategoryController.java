package com.example.financial_management.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.category.request.CategoryRequest;
import com.example.financial_management.model.category.response.CategoryResponse;
import com.example.financial_management.services.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Category API", description = "Quản lý và tra cứu Danh mục Thu / Chi / Chuyển khoản (CRUD)")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Lấy danh sách các danh mục (hỗ trợ lọc theo transactionTypeId và trạng thái kích hoạt)")
    public ResponseEntity<AbstractResponse<List<CategoryResponse>>> getAllCategories(
            @Parameter(description = "Lọc theo loại giao dịch (1: Chi tiêu, 2: Thu nhập, 3: Chuyển khoản)")
            @RequestParam(name = "typeId", required = false) Integer typeId,
            @Parameter(description = "Bao gồm cả danh mục đã bị vô hiệu hóa (mặc định false)")
            @RequestParam(name = "includeInactive", required = false, defaultValue = "false") boolean includeInactive) {
        return new AbstractResponse<List<CategoryResponse>>()
                .withData(() -> categoryService.getAllCategories(typeId, includeInactive));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết một danh mục theo ID")
    public ResponseEntity<AbstractResponse<CategoryResponse>> getById(
            @Parameter(description = "ID của danh mục", example = "101")
            @PathVariable("id") Integer id) {
        return new AbstractResponse<CategoryResponse>()
                .withData(() -> categoryService.getCategoryById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo mới một danh mục")
    public ResponseEntity<AbstractResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CategoryRequest request) {
        return new AbstractResponse<CategoryResponse>()
                .withData(() -> categoryService.createCategory(request));
    }

    @PostMapping("/create")
    @Operation(summary = "Tạo mới một danh mục (alias /create)")
    public ResponseEntity<AbstractResponse<CategoryResponse>> createCategoryAlias(
            @Valid @RequestBody CategoryRequest request) {
        return new AbstractResponse<CategoryResponse>()
                .withData(() -> categoryService.createCategory(request));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.POST})
    @Operation(summary = "Cập nhật thông tin danh mục (hỗ trợ cả phương thức PUT và POST)")
    public ResponseEntity<AbstractResponse<CategoryResponse>> updateCategory(
            @Parameter(description = "ID của danh mục cần cập nhật", example = "101")
            @PathVariable("id") Integer id,
            @Valid @RequestBody CategoryRequest request) {
        return new AbstractResponse<CategoryResponse>()
                .withData(() -> categoryService.updateCategory(id, request));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Cập nhật trạng thái kích hoạt (Active/Inactive) của danh mục")
    public ResponseEntity<AbstractResponse<CategoryResponse>> updateCategoryStatus(
            @Parameter(description = "ID của danh mục", example = "101")
            @PathVariable("id") Integer id,
            @Parameter(description = "Trạng thái mới (true: Kích hoạt, false: Vô hiệu hóa)")
            @RequestParam("active") boolean active) {
        return new AbstractResponse<CategoryResponse>()
                .withData(() -> categoryService.updateCategoryStatus(id, active));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa danh mục (Tự động xóa mềm nếu đã phát sinh giao dịch/ngân sách; Xóa cứng nếu chưa từng dùng)")
    public ResponseEntity<AbstractResponse<Boolean>> deleteCategory(
            @Parameter(description = "ID của danh mục cần xóa", example = "101")
            @PathVariable("id") Integer id) {
        return new AbstractResponse<Boolean>()
                .withData(() -> categoryService.deleteCategory(id));
    }
}
