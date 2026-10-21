package com.example.financial_management.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.example.financial_management.entity.CategoryEntity;
import com.example.financial_management.model.category.response.CategoryResponse;
import com.example.financial_management.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Cacheable(value = "categories", key = "'allActive'")
    public List<CategoryResponse> getAllActiveCategories() {
        return categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "categories", key = "'byType:' + #transactionTypeId")
    public List<CategoryResponse> getCategoriesByType(Integer transactionTypeId) {
        return categoryRepository.findAllByTransactionTypeIdAndIsActiveTrueOrderByDisplayOrderAsc(transactionTypeId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "categories", key = "'id:' + #id")
    public CategoryResponse getCategoryById(Integer id) {
        if (id == null) {
            return null;
        }
        return categoryRepository.findById(id)
                .map(this::mapToResponse)
                .orElse(null);
    }

    public boolean isValidCategory(Integer id) {
        if (id == null) {
            return false;
        }
        return categoryRepository.existsById(id);
    }

    public boolean isExpense(Integer categoryId) {
        if (categoryId == null) {
            return false;
        }
        return categoryId >= 100 && categoryId < 200;
    }

    public boolean isIncome(Integer categoryId) {
        if (categoryId == null) {
            return false;
        }
        return categoryId >= 200 && categoryId < 300;
    }

    public String getCategoryName(Integer categoryId) {
        CategoryResponse cat = getCategoryById(categoryId);
        return cat != null ? cat.getName() : "Unknown";
    }

    public String getCategoryVietnameseName(Integer categoryId) {
        CategoryResponse cat = getCategoryById(categoryId);
        return cat != null ? cat.getNameVi() : "Không xác định";
    }

    public String getCategoryIcon(Integer categoryId) {
        CategoryResponse cat = getCategoryById(categoryId);
        return cat != null ? cat.getIcon() : "📦";
    }

    private CategoryResponse mapToResponse(CategoryEntity entity) {
        return CategoryResponse.builder()
                .id(entity.getId())
                .transactionTypeId(entity.getTransactionTypeId())
                .code(entity.getCode())
                .name(entity.getName())
                .nameVi(entity.getNameVi())
                .icon(entity.getIcon())
                .isSystem(entity.isSystem())
                .isActive(entity.isActive())
                .displayOrder(entity.getDisplayOrder())
                .build();
    }
}
