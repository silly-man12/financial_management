package com.example.financial_management.model.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.example.financial_management.entity.Transaction;

import jakarta.persistence.criteria.Predicate;

public class TransactionSpecification {
    public static Specification<Transaction> filter(UUID userId,
            TransactionFilterRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Lọc theo user
            predicates.add(cb.equal(root.get("userId"), userId));

            if (request.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        request.getFromDate().atStartOfDay() // 00:00:00
                ));
            }
            if (request.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"),
                        request.getToDate().atTime(23, 59, 59) // 23:59:59
                ));
            }

            if (request.getAccountId() != null && !request.getAccountId().isEmpty()) {
                predicates.add(root.get("accountId").in(request.getAccountId()));
            }

            if (request.getCategory() != null && !request.getCategory().isEmpty()) {
                predicates.add(root.get("category").in(request.getCategory()));
            }

            if (request.getType() != null) { // giả sử null = không lọc
                predicates.add(cb.equal(root.get("type"), request.getType()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
