package com.example.financial_management.entity;

import com.example.financial_management.entity.base.EntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.UUID;

@Entity
@Table(
        name = "bank_category_rules",
        indexes = {
                @Index(name = "idx_bcr_user_active_prio", columnList = "user_id, is_active, priority")
        }
)
@Getter
@Setter
@Accessors(chain = true)
public class BankCategoryRule extends EntityBase {

    @Column(name = "user_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID userId;

    @Column(name = "keyword", nullable = false, length = 150, columnDefinition = "nvarchar(150)")
    private String keyword;

    @Column(name = "category", nullable = false)
    private int category;

    @Column(name = "direction", length = 10)
    private String direction;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "description", length = 255, columnDefinition = "nvarchar(255)")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
