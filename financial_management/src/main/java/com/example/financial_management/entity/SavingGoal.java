package com.example.financial_management.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.example.financial_management.constant.SavingGoalStatus;
import com.example.financial_management.entity.base.EntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "saving_goals")
@Getter
@Setter
public class SavingGoal extends EntityBase {

    @Column(name = "user_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID userId;

    @Column(name = "name", nullable = false, length = 150, columnDefinition = "nvarchar(150)")
    private String name;

    @Column(name = "target_amount", nullable = false)
    private BigDecimal targetAmount;

    @Column(name = "current_amount", nullable = false)
    private BigDecimal currentAmount = BigDecimal.ZERO;

    @Column(name = "target_date")
    private LocalDate targetDate; // Hạn chót đạt mục tiêu

    @Column(name = "color", length = 30)
    private String color; // Mã màu hiển thị (vd: "#4CAF50", "blue")

    @Column(name = "description", length = 255, columnDefinition = "nvarchar(255)")
    private String description;

    @Column(name = "status", nullable = false)
    private int status = SavingGoalStatus.IN_PROGRESS;

}
