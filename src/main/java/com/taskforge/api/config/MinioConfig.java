package com.taskforge.api.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MinioConfig {

    @Value("${taskforge.minio.endpoint:http://minio:9000}")
    private String endpoint;

    @Value("${taskforge.minio.access-key:taskforgeadmin}")
    private String accessKey;

    @Value("${taskforge.minio.secret-key:taskforgepassword}")
    private String secretKey;

    @Value("${taskforge.minio.bucket:taskforge-artifacts}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO artifacts bucket: {}", bucket);
            } else {
                log.info("MinIO artifacts bucket {} already exists", bucket);
            }
            return client;
        } catch (Exception e) {
            log.warn("MinIO initialization warning: {}", e.getMessage());
            return MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
        }
    }
}
