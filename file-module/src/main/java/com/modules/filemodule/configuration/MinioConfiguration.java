package com.modules.filemodule.configuration;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfiguration {
    @Value("${bucket.minio.url}")
    private String MINIO_URL;
    @Value("${bucket.minio.accesskey}")
    private String ACCESS_KEY;
    @Value("${bucket.minio.secretkey}")
    private String SECRET_KEY;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(MINIO_URL)
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();
    }
}
