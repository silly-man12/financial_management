package com.example.financial_management.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.tag.TagRequest;
import com.example.financial_management.model.tag.TagResponse;
import com.example.financial_management.model.tag.TagSummaryResponse;
import com.example.financial_management.services.TagService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tag API", description = "Tag Management for Transactions")
public class TagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "Lấy tất cả các thẻ tag của người dùng")
    public ResponseEntity<AbstractResponse<List<TagResponse>>> getAll(
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<List<TagResponse>>()
                .withData(() -> tagService.getAllTags(auth));
    }

    @GetMapping("/all")
    @Operation(summary = "Lấy tất cả các thẻ tag của người dùng (/all)")
    public ResponseEntity<AbstractResponse<List<TagResponse>>> getAllAlias(
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<List<TagResponse>>()
                .withData(() -> tagService.getAllTags(auth));
    }

    @PostMapping
    @Operation(summary = "Tạo thẻ tag mới")
    public ResponseEntity<AbstractResponse<TagResponse>> create(
            @Valid @RequestBody TagRequest request,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<TagResponse>()
                .withData(() -> tagService.createTag(request, auth));
    }

    @PostMapping("/create")
    @Operation(summary = "Tạo thẻ tag mới (/create)")
    public ResponseEntity<AbstractResponse<TagResponse>> createAlias(
            @Valid @RequestBody TagRequest request,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<TagResponse>()
                .withData(() -> tagService.createTag(request, auth));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin thẻ tag")
    public ResponseEntity<AbstractResponse<TagResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody TagRequest request,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<TagResponse>()
                .withData(() -> tagService.updateTag(id, request, auth));
    }

    @PostMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin thẻ tag qua POST")
    public ResponseEntity<AbstractResponse<TagResponse>> updatePost(
            @PathVariable UUID id,
            @Valid @RequestBody TagRequest request,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<TagResponse>()
                .withData(() -> tagService.updateTag(id, request, auth));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa thẻ tag")
    public ResponseEntity<AbstractResponse<Boolean>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<Boolean>()
                .withData(() -> tagService.deleteTag(id, auth));
    }

    @GetMapping("/summary")
    @Operation(summary = "Lấy bảng tổng hợp chi phí/thu nhập theo từng tag")
    public ResponseEntity<AbstractResponse<List<TagSummaryResponse>>> getSummaries(
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<List<TagSummaryResponse>>()
                .withData(() -> tagService.getTagsSummary(auth));
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "Lấy tổng kết chi phí/thu nhập của 1 tag cụ thể")
    public ResponseEntity<AbstractResponse<TagSummaryResponse>> getSummaryById(
            @PathVariable UUID id,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<TagSummaryResponse>()
                .withData(() -> tagService.getTagSummary(id, auth));
    }
}
