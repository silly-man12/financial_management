package com.example.financial_management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.financial_management.entity.CategoryEntity;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Integer> {
    Optional<CategoryEntity> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Integer id);

    List<CategoryEntity> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    List<CategoryEntity> findAllByOrderByDisplayOrderAsc();

    List<CategoryEntity> findAllByTransactionTypeIdAndIsActiveTrueOrderByDisplayOrderAsc(Integer transactionTypeId);

    List<CategoryEntity> findAllByTransactionTypeIdOrderByDisplayOrderAsc(Integer transactionTypeId);

    @Query("SELECT MAX(c.id) FROM CategoryEntity c WHERE c.id >= :minId AND c.id <= :maxId")
    Integer findMaxIdInRange(@Param("minId") Integer minId, @Param("maxId") Integer maxId);

    @Query("SELECT COALESCE(MAX(c.displayOrder), 0) FROM CategoryEntity c WHERE c.transactionTypeId = :transactionTypeId")
    Integer findMaxDisplayOrderByTransactionTypeId(@Param("transactionTypeId") Integer transactionTypeId);
}
