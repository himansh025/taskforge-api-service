package com.taskforge.api.controller;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class FileUploadController {

    private final MinioClient minioClient;

    @Value("${taskforge.minio.bucket:taskforge-artifacts}")
    private String bucket;

    @PostMapping(value = "/workspaces/{workspaceId}/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadFile(
            @PathVariable UUID workspaceId,
            @RequestParam("file") MultipartFile file) {

        String fileId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "input.csv";
        String artifactKey = "workspaces/" + workspaceId + "/uploads/" + fileId + "/" + originalFilename;

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(artifactKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType() != null ? file.getContentType() : "text/csv")
                            .build()
            );
            log.info("Successfully uploaded artifact {} ({} bytes) to MinIO", artifactKey, file.getSize());
        } catch (Exception e) {
            log.error("Failed to store artifact in MinIO: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Storage error: " + e.getMessage()));
        }

        return ResponseEntity.ok(Map.of(
                "artifactKey", artifactKey,
                "filename", originalFilename,
                "size", file.getSize(),
                "fileId", fileId,
                "bucket", bucket
        ));
    }

    @GetMapping("/artifacts/**")
    public ResponseEntity<InputStreamResource> downloadArtifact(jakarta.servlet.http.HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String artifactKey = fullPath.substring("/api/v1/artifacts/".length());

        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(artifactKey)
                            .build()
            );

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifactKey.substring(artifactKey.lastIndexOf('/') + 1) + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            log.error("Failed to retrieve artifact {}: {}", artifactKey, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
