package com.example.financial_management.services;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.financial_management.entity.CategoryEntity;
import com.example.financial_management.model.category.request.CategoryRequest;
import com.example.financial_management.model.category.response.CategoryResponse;
import com.example.financial_management.repository.BudgetRepository;
import com.example.financial_management.repository.CategoryRepository;
import com.example.financial_management.repository.RecurringTransactionRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.TransactionTypeRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;

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

    public List<CategoryResponse> getAllCategories(Integer transactionTypeId, boolean includeInactive) {
        List<CategoryEntity> list;
        if (transactionTypeId != null) {
            list = includeInactive
                    ? categoryRepository.findAllByTransactionTypeIdOrderByDisplayOrderAsc(transactionTypeId)
                    : categoryRepository.findAllByTransactionTypeIdAndIsActiveTrueOrderByDisplayOrderAsc(transactionTypeId);
        } else {
            list = includeInactive
                    ? categoryRepository.findAllByOrderByDisplayOrderAsc()
                    : categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc();
        }
        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
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

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse createCategory(CategoryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Thông tin danh mục không được để trống");
        }

        // 1. Kiểm tra transactionTypeId hợp lệ
        if (!transactionTypeRepository.existsById(request.getTransactionTypeId())) {
            throw new IllegalArgumentException("Loại giao dịch không hợp lệ: " + request.getTransactionTypeId());
        }

        // 2. Xác định ID theo quy chuẩn Block ID (1xx Chi tiêu, 2xx Thu nhập, 3xx Chuyển khoản)
        Integer id = request.getId();
        if (id != null) {
            if (categoryRepository.existsById(id)) {
                throw new IllegalArgumentException("ID danh mục " + id + " đã tồn tại trong hệ thống.");
            }
        } else {
            id = generateNextIdForType(request.getTransactionTypeId());
        }

        // 3. Xác định Code hệ thống
        String code = request.getCode();
        if (code == null || code.trim().isEmpty()) {
            code = generateCodeFromName(request.getName(), id);
        } else {
            code = code.trim().toUpperCase();
            if (categoryRepository.existsByCode(code)) {
                throw new IllegalArgumentException("Mã code danh mục '" + code + "' đã tồn tại.");
            }
        }

        // 4. Các giá trị hiển thị mặc định
        String nameVi = (request.getNameVi() != null && !request.getNameVi().trim().isEmpty())
                ? request.getNameVi().trim() : request.getName().trim();

        String icon = (request.getIcon() != null && !request.getIcon().trim().isEmpty())
                ? request.getIcon().trim() : "bi-tag";

        String color = (request.getColor() != null && !request.getColor().trim().isEmpty())
                ? request.getColor().trim() : "#90A4AE";

        Integer displayOrder = request.getDisplayOrder();
        if (displayOrder == null || displayOrder <= 0) {
            displayOrder = categoryRepository.findMaxDisplayOrderByTransactionTypeId(request.getTransactionTypeId()) + 1;
        }

        CategoryEntity entity = CategoryEntity.builder()
                .id(id)
                .transactionTypeId(request.getTransactionTypeId())
                .code(code)
                .name(request.getName().trim())
                .nameVi(nameVi)
                .icon(icon)
                .color(color)
                .isSystem(Boolean.TRUE.equals(request.getIsSystem()))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .displayOrder(displayOrder)
                .createdAt(LocalDateTime.now())
                .build();

        CategoryEntity saved = categoryRepository.save(entity);
        log.info("Tạo mới danh mục thành công: ID={}, Code={}, NameVi={}", saved.getId(), saved.getCode(), saved.getNameVi());
        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse updateCategory(Integer id, CategoryRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("ID danh mục không được để trống");
        }

        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục có ID: " + id));

        if (request.getTransactionTypeId() != null && !request.getTransactionTypeId().equals(entity.getTransactionTypeId())) {
            if (!transactionTypeRepository.existsById(request.getTransactionTypeId())) {
                throw new IllegalArgumentException("Loại giao dịch không hợp lệ: " + request.getTransactionTypeId());
            }
            entity.setTransactionTypeId(request.getTransactionTypeId());
        }

        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            String newCode = request.getCode().trim().toUpperCase();
            if (categoryRepository.existsByCodeAndIdNot(newCode, id)) {
                throw new IllegalArgumentException("Mã code danh mục '" + newCode + "' đã tồn tại ở danh mục khác.");
            }
            entity.setCode(newCode);
        }

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            entity.setName(request.getName().trim());
        }

        if (request.getNameVi() != null && !request.getNameVi().trim().isEmpty()) {
            entity.setNameVi(request.getNameVi().trim());
        }

        if (request.getIcon() != null && !request.getIcon().trim().isEmpty()) {
            entity.setIcon(request.getIcon().trim());
        }

        if (request.getColor() != null && !request.getColor().trim().isEmpty()) {
            entity.setColor(request.getColor().trim());
        }

        if (request.getDisplayOrder() != null) {
            entity.setDisplayOrder(request.getDisplayOrder());
        }

        if (request.getIsActive() != null) {
            entity.setActive(request.getIsActive());
        }

        CategoryEntity saved = categoryRepository.save(entity);
        log.info("Cập nhật danh mục thành công: ID={}, Code={}", saved.getId(), saved.getCode());
        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse updateCategoryStatus(Integer id, boolean active) {
        if (id == null) {
            throw new IllegalArgumentException("ID danh mục không được để trống");
        }

        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục có ID: " + id));

        entity.setActive(active);
        CategoryEntity saved = categoryRepository.save(entity);
        log.info("Cập nhật trạng thái danh mục: ID={}, Active={}", saved.getId(), saved.isActive());
        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public boolean deleteCategory(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("ID danh mục không được để trống");
        }

        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục có ID: " + id));

        // 1. Không cho phép xóa cứng danh mục hệ thống mặc định
        if (entity.isSystem()) {
            throw new IllegalArgumentException("Không thể xóa danh mục mặc định của hệ thống (" + entity.getNameVi()
                    + "). Bạn có thể vô hiệu hóa danh mục này bằng cách đổi trạng thái hoạt động.");
        }

        // 2. Kiểm tra xem danh mục đã được dùng trong giao dịch, ngân sách hoặc giao dịch định kỳ chưa
        boolean inTransaction = transactionRepository.existsByCategory(id);
        boolean inBudget = budgetRepository.existsByCategory(id);
        boolean inRecurring = recurringTransactionRepository.existsByCategory(id);

        if (inTransaction || inBudget || inRecurring) {
            // Đã có dữ liệu phát sinh -> Chuyển sang xóa mềm (vô hiệu hóa) để bảo vệ toàn vẹn lịch sử giao dịch
            entity.setActive(false);
            categoryRepository.save(entity);
            log.info("Danh mục ID={} đã có ràng buộc dữ liệu. Đã tự động chuyển sang vô hiệu hóa (xóa mềm).", id);
            return true;
        }

        // 3. Chưa có dữ liệu phát sinh -> Cho phép xóa hoàn toàn khỏi DB
        categoryRepository.delete(entity);
        log.info("Đã xóa hoàn toàn danh mục ID={} khỏi CSDL.", id);
        return true;
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
        return cat != null ? cat.getIcon() : "bi-tag";
    }

    public String getCategoryColor(Integer categoryId) {
        CategoryResponse cat = getCategoryById(categoryId);
        return cat != null ? cat.getColor() : "#90A4AE";
    }

    private Integer generateNextIdForType(Integer transactionTypeId) {
        int minRange;
        int maxRange;

        if (transactionTypeId == 1) { // Expense
            minRange = 101;
            maxRange = 199;
        } else if (transactionTypeId == 2) { // Income
            minRange = 201;
            maxRange = 299;
        } else if (transactionTypeId == 3) { // Transfer
            minRange = 301;
            maxRange = 399;
        } else {
            minRange = 101;
            maxRange = 199;
        }

        Integer maxId = categoryRepository.findMaxIdInRange(minRange, maxRange);
        int nextId = (maxId == null) ? minRange : maxId + 1;

        if (nextId > maxRange) {
            throw new IllegalStateException("Đã sử dụng hết dải số ID cho nhóm này (" + minRange + " - " + maxRange + ")");
        }
        return nextId;
    }

    private String generateCodeFromName(String name, Integer id) {
        if (name == null || name.trim().isEmpty()) {
            return "CAT_" + id;
        }
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        if (normalized.isEmpty()) {
            return "CAT_" + id;
        }

        if (categoryRepository.existsByCode(normalized)) {
            return normalized + "_" + id;
        }
        return normalized;
    }

    private CategoryResponse mapToResponse(CategoryEntity entity) {
        return CategoryResponse.builder()
                .id(entity.getId())
                .transactionTypeId(entity.getTransactionTypeId())
                .code(entity.getCode())
                .name(entity.getName())
                .nameVi(entity.getNameVi())
                .icon(entity.getIcon())
                .color(entity.getColor())
                .isSystem(entity.isSystem())
                .isActive(entity.isActive())
                .displayOrder(entity.getDisplayOrder())
                .build();
    }
}
