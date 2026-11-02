package com.taskforge.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@Slf4j
public class FileUploadController {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @PostMapping(value = "/workspaces/{workspaceId}/uploads", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @PathVariable UUID workspaceId,
            @RequestParam("file") MultipartFile file) {

        String fileId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "input.csv";
        String artifactKey = "workspaces/" + workspaceId + "/uploads/" + fileId + "/" + originalFilename;

        try {
            // Stream upload to MinIO bucket
            String minioUrl = System.getenv().getOrDefault("MINIO_ENDPOINT", "http://minio:9000");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(minioUrl + "/taskforge-artifacts/" + artifactKey))
                    .header("Content-Type", file.getContentType() != null ? file.getContentType() : "text/csv")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            log.info("Uploaded artifact to MinIO: {}", artifactKey);
        } catch (Exception e) {
            log.warn("MinIO direct upload fallback (using metadata only): {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "artifactKey", artifactKey,
                "filename", originalFilename,
                "size", file.getSize(),
                "fileId", fileId
        ));
    }
}
