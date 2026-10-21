package com.example.financial_management.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.category.response.CategoryResponse;
import com.example.financial_management.services.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Category API", description = "Quản lý và tra cứu Danh mục Thu / Chi / Chuyển khoản")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả các danh mục đang hoạt động (có thể lọc theo transactionTypeId)")
    public ResponseEntity<AbstractResponse<List<CategoryResponse>>> getAllCategories(
            @RequestParam(name = "typeId", required = false) Integer typeId) {
        return new AbstractResponse<List<CategoryResponse>>()
                .withData(() -> typeId != null ? categoryService.getCategoriesByType(typeId)
                        : categoryService.getAllActiveCategories());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết của một danh mục theo ID")
    public ResponseEntity<AbstractResponse<CategoryResponse>> getById(@PathVariable("id") Integer id) {
        return new AbstractResponse<CategoryResponse>()
                .withData(() -> categoryService.getCategoryById(id));
    }
}
