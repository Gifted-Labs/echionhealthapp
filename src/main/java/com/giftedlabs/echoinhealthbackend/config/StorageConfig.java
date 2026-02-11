package com.giftedlabs.echoinhealthbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for file storage
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {

    private String type; // local or r2
    private long maxFileSize;
    private String allowedTypes;

    private Local local = new Local();
    private R2 r2 = new R2();

    @Data
    public static class Local {
        private String basePath;
    }

    @Data
    public static class R2 {
        private String endpoint;
        private String bucket;
        private String accessKey;
        private String secretKey;
        private String region;
    }
}
