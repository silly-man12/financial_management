package com.example.financial_management.entity;

import com.example.financial_management.constant.Role;
import com.example.financial_management.constant.Status;
import com.example.financial_management.entity.base.EntityBase;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "users")
@NoArgsConstructor
public class User extends EntityBase {
    @Column(nullable = false, length = 100, columnDefinition = "nvarchar(100)")
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 255)
    private String passwordSalt;

    @Column(nullable = false)
    private int role = Role.USER;

    @Column(nullable = false)
    private int status = Status.ACTIVE;

    @Column(name = "telegram_chat_id", nullable = true)
    private Long telegramChatId;
}
