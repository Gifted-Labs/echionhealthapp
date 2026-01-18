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

    private String type; // local or railway
    private long maxFileSize;
    private String allowedTypes;

    private Local local = new Local();
    private Railway railway = new Railway();

    @Data
    public static class Local {
        private String basePath;
    }

    @Data
    public static class Railway {
        private String endpoint;
        private String bucket;
        private String accessKey;
        private String secretKey;
        private String region;
    }
}
