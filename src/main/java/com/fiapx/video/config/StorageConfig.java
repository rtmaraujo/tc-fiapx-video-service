package com.fiapx.video.config;

import com.fiapx.video.infrastructure.storage.MinioStorageAdapter;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    public MinioClient minioClient(StorageProperties properties) {
        return MinioClient.builder()
            .endpoint(properties.getEndpoint())
            .credentials(properties.getAccessKey(), properties.getSecretKey())
            .build();
    }

    @Bean
    public MinioStorageAdapter storagePort(MinioClient minioClient, StorageProperties properties) {
        return new MinioStorageAdapter(minioClient, properties);
    }
}
