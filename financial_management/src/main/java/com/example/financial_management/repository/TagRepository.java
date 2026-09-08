package com.example.financial_management.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.financial_management.entity.Tag;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
    List<Tag> findAllByUserIdOrderByNameAsc(UUID userId);

    Optional<Tag> findByIdAndUserId(UUID id, UUID userId);

    Optional<Tag> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    List<Tag> findAllByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    List<Tag> findAllByUserIdAndNameIn(UUID userId, Collection<String> names);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    @Modifying
    @Query(value = "DELETE FROM transaction_tags WHERE tag_id = :tagId", nativeQuery = true)
    void unlinkTagFromAllTransactions(@Param("tagId") UUID tagId);
}
