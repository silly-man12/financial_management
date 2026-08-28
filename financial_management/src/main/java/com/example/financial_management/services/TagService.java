package com.example.financial_management.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.financial_management.constant.Category;
import com.example.financial_management.entity.Tag;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import com.example.financial_management.mapper.TagMapper;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.tag.TagRequest;
import com.example.financial_management.model.tag.TagResponse;
import com.example.financial_management.model.tag.TagSummaryResponse;
import com.example.financial_management.repository.TagRepository;
import com.example.financial_management.repository.TransactionRepository;
import com.example.financial_management.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final TagMapper tagMapper;

    private User validateUser(Auth auth) {
        if (auth == null || auth.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Người dùng chưa đăng nhập");
        }
        return userRepository.findById(UUID.fromString(auth.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }


    private String cleanTagName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        while (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed;
    }

    public List<TagResponse> getAllTags(Auth auth) {
        User user = validateUser(auth);
        return tagRepository.findAllByUserIdOrderByNameAsc(user.getId())
                .stream()
                .map(tagMapper::toResponse)
                .toList();
    }

    @Transactional
    public TagResponse createTag(TagRequest request, Auth auth) {
        User user = validateUser(auth);
        String cleanName = cleanTagName(request.getName());
        if (cleanName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên tag không hợp lệ");
        }

        if (tagRepository.existsByUserIdAndNameIgnoreCase(user.getId(), cleanName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thẻ Tag '" + cleanName + "' đã tồn tại");
        }

        Tag tag = tagMapper.toEntity(request, user.getId());
        tag.setName(cleanName);
        if (tag.getColor() == null || tag.getColor().isBlank()) {
            tag.setColor("#6366f1");
        }

        Tag saved = tagRepository.save(tag);
        return tagMapper.toResponse(saved);
    }

    @Transactional
    public TagResponse updateTag(UUID id, TagRequest request, Auth auth) {
        User user = validateUser(auth);
        Tag tag = tagRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tag"));

        String cleanName = cleanTagName(request.getName());
        if (cleanName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên tag không hợp lệ");
        }

        if (!tag.getName().equalsIgnoreCase(cleanName) &&
                tagRepository.existsByUserIdAndNameIgnoreCase(user.getId(), cleanName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thẻ Tag '" + cleanName + "' đã tồn tại");
        }

        tag.setName(cleanName);
        if (request.getColor() != null && !request.getColor().isBlank()) {
            tag.setColor(request.getColor());
        }

        Tag saved = tagRepository.save(tag);
        return tagMapper.toResponse(saved);
    }

    @Transactional
    public boolean deleteTag(UUID id, Auth auth) {
        User user = validateUser(auth);
        Tag tag = tagRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tag"));

        List<Transaction> transactionsWithTag = transactionRepository.findAllByUserIdAndTagId(user.getId(), id);
        for (Transaction t : transactionsWithTag) {
            t.getTags().remove(tag);
            transactionRepository.save(t);
        }

        tagRepository.delete(tag);
        return true;
    }

    public List<TagSummaryResponse> getTagsSummary(Auth auth) {
        User user = validateUser(auth);
        List<Tag> tags = tagRepository.findAllByUserIdOrderByNameAsc(user.getId());
        List<TagSummaryResponse> summaries = new ArrayList<>();

        for (Tag tag : tags) {
            summaries.add(calculateSummaryForTag(tag, user.getId()));
        }

        return summaries;
    }

    public TagSummaryResponse getTagSummary(UUID tagId, Auth auth) {
        User user = validateUser(auth);
        Tag tag = tagRepository.findByIdAndUserId(tagId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tag"));

        return calculateSummaryForTag(tag, user.getId());
    }

    private TagSummaryResponse calculateSummaryForTag(Tag tag, UUID userId) {
        List<Transaction> transactions = transactionRepository.findAllByUserIdAndTagId(userId, tag.getId());

        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;

        for (Transaction t : transactions) {
            if (t.getCategory() == Category.TRANSFER) {
                continue;
            }
            if (t.getType() == 0) { // Chi
                totalExpense = totalExpense.add(t.getAmount());
            } else if (t.getType() == 1) { // Thu
                totalIncome = totalIncome.add(t.getAmount());
            }
        }

        BigDecimal balance = totalIncome.subtract(totalExpense);

        return TagSummaryResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .color(tag.getColor())
                .totalExpense(totalExpense)
                .totalIncome(totalIncome)
                .balance(balance)
                .transactionCount(transactions.size())
                .createdAt(tag.getCreatedAt())
                .build();
    }

    @Transactional
    public Set<Tag> resolveTags(List<String> rawTags, UUID userId) {
        if (rawTags == null || rawTags.isEmpty()) {
            return new HashSet<>();
        }

        Set<Tag> resolvedTags = new HashSet<>();
        for (String raw : rawTags) {
            if (raw == null) continue;
            String clean = cleanTagName(raw);
            if (clean.isBlank()) continue;

            // Kiểm tra xem có phải UUID không
            try {
                UUID tagId = UUID.fromString(clean);
                Optional<Tag> tagById = tagRepository.findByIdAndUserId(tagId, userId);
                if (tagById.isPresent()) {
                    resolvedTags.add(tagById.get());
                    continue;
                }
            } catch (IllegalArgumentException ignored) {
                // Không phải UUID, tiếp tục tìm theo tên
            }

            Optional<Tag> tagByName = tagRepository.findByUserIdAndNameIgnoreCase(userId, clean);
            if (tagByName.isPresent()) {
                resolvedTags.add(tagByName.get());
            } else {
                Tag newTag = new Tag();
                newTag.setUserId(userId);
                newTag.setName(clean);
                newTag.setColor(generateDefaultColor(clean));
                resolvedTags.add(tagRepository.save(newTag));
            }

        }

        return resolvedTags;
    }

    private String generateDefaultColor(String name) {
        String[] colors = new String[] {
                "#6366f1", "#ec4899", "#f59e0b", "#10b981", "#3b82f6",
                "#8b5cf6", "#14b8a6", "#f97316", "#06b6d4", "#84cc16"
        };
        int hash = Math.abs(name.hashCode());
        return colors[hash % colors.length];
    }
}
