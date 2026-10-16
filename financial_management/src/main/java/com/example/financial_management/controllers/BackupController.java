package com.example.financial_management.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.backup.BackupExportRequest;
import com.example.financial_management.model.backup.BackupImportResponse;
import com.example.financial_management.model.backup.BackupValidateResponse;
import com.example.financial_management.services.BackupService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/backup")
@RequiredArgsConstructor
@Tag(name = "Backup & Restore", description = "Sao lưu và phục hồi dữ liệu 1-Click")
public class BackupController {

        private final BackupService backupService;

        /**
         * POST /backup/export
         * Xuất toàn bộ dữ liệu người dùng thành file JSON để tải về.
         */
        @PostMapping("/export")
        @Operation(summary = "Xuất backup dữ liệu", description = "Export toàn bộ dữ liệu (accounts, transactions, budgets, debts, ...) thành file JSON có thể tải về")
        public ResponseEntity<byte[]> exportBackup(
                        @RequestBody(required = false) BackupExportRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

                boolean includeImages = request != null && request.isIncludeImages();
                return backupService.exportBackup(auth, includeImages);
        }

        /**
         * POST /backup/validate
         * Kiểm tra file backup có hợp lệ không trước khi import.
         */
        @PostMapping(value = "/validate", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Kiểm tra file backup", description = "Validate file JSON backup: kiểm tra schema version, thống kê số record. Không thay đổi dữ liệu.")
        public ResponseEntity<AbstractResponse<BackupValidateResponse>> validateBackup(
                        @Parameter(description = "File backup JSON") @RequestPart("file") MultipartFile file,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

                return new AbstractResponse<BackupValidateResponse>()
                                .withData(() -> backupService.validateBackup(file));
        }

        /**
         * POST /backup/import
         * Import (restore) dữ liệu từ file backup.
         * mode=merge (mặc định): giữ data cũ, chỉ thêm record mới.
         * mode=overwrite: XOÁ TOÀN BỘ data cũ rồi restore (CỰC KỲ NGUY HIỂM).
         */
        @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Import backup dữ liệu", description = "Restore dữ liệu từ file backup JSON. " +
                        "Mode 'merge' giữ data hiện tại, 'overwrite' sẽ XOÁ TOÀN BỘ dữ liệu trước khi restore.")
        public ResponseEntity<AbstractResponse<BackupImportResponse>> importBackup(
                        @Parameter(description = "File backup JSON") @RequestPart("file") MultipartFile file,
                        @Parameter(description = "Chế độ import: 'merge' (mặc định) hoặc 'overwrite'") @RequestParam(value = "mode", defaultValue = "merge") String mode,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

                return new AbstractResponse<BackupImportResponse>()
                                .withData(() -> backupService.importBackup(auth, file, mode));
        }
}
