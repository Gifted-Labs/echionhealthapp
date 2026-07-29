package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.config.StorageConfig;
import com.giftedlabs.echoinhealthbackend.entity.StorageType;
import com.giftedlabs.echoinhealthbackend.exception.InvalidTokenException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Service for handling file storage operations (Local and Cloudflare R2 S3)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final StorageConfig storageConfig;
    private S3Client s3Client;
    private Path localStoragePath;

    @PostConstruct
    public void init() {
        if ("r2".equalsIgnoreCase(storageConfig.getType())) {
            initializeS3Client();
        } else {
            initializeLocalStorage();
        }
    }

    private void initializeLocalStorage() {
        try {
            this.localStoragePath = Paths.get(storageConfig.getLocal().getBasePath()).toAbsolutePath().normalize();
            Files.createDirectories(this.localStoragePath);
            log.info("Initialized local storage at: {}", this.localStoragePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize local storage", e);
        }
    }

    private void initializeS3Client() {
        try {
            // Cloudflare R2 S3 compatible storage requires endpoint override
            String endpoint = storageConfig.getR2().getEndpoint();
            // Ensure endpoint has protocol
            if (!endpoint.startsWith("http")) {
                endpoint = "https://" + endpoint;
            }

            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of(storageConfig.getR2().getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    storageConfig.getR2().getAccessKey(),
                                    storageConfig.getR2().getSecretKey())))
                    .build();
            log.info("Initialized R2/S3 storage client");
        } catch (Exception e) {
            log.error("Failed to initialize S3 client, falling back to local storage", e);
            initializeLocalStorage();
        }
    }

    /**
     * Store a file and return its path/identifier
     */
    public String storeFile(MultipartFile file, String organizationId, String userId) {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String tenantPrefix = organizationId != null ? organizationId : "platform";
        String filename = tenantPrefix + "/" + userId + "/" + UUID.randomUUID() + "_" + originalFilename;

        try {
            if (isR2Storage()) {
                return uploadToS3(filename, file);
            } else {
                return saveLocally(filename, file);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file " + filename, e);
        }
    }

    private String saveLocally(String filename, MultipartFile file) throws IOException {
        // Ensure user directory exists
        Path targetPath = this.localStoragePath.resolve(filename);
        Files.createDirectories(targetPath.getParent());

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }

    private String uploadToS3(String key, MultipartFile file) throws IOException {
        PutObjectRequest putOb = PutObjectRequest.builder()
                .bucket(storageConfig.getR2().getBucket())
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putOb, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return key;
    }

    /**
     * Removes a stored object so it stops consuming the tenant's storage quota.
     *
     * <p>Returns whether the blob is now gone. Deletion failures are logged rather than thrown:
     * a soft-deleted record whose blob could not be removed should keep counting against the
     * quota, which is exactly what a {@code false} return causes the caller to record.
     */
    public boolean deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return true;
        }
        try {
            if (isR2Storage()) {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(storageConfig.getR2().getBucket())
                        .key(filePath)
                        .build());
            } else {
                Files.deleteIfExists(this.localStoragePath.resolve(filePath));
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete stored file {}; it will keep counting toward storage usage",
                    filePath, e);
            return false;
        }
    }

    public StorageType getCurrentStorageType() {
        return isR2Storage() ? StorageType.R2 : StorageType.LOCAL;
    }

    private boolean isR2Storage() {
        return "r2".equalsIgnoreCase(storageConfig.getType()) && s3Client != null;
    }

    /**
     * Get a direct download URL (presigned if S3, direct path if local - though for
     * local we usually stream it)
     * For local, returning the absolute path or a handle for the controller to
     * stream.
     */
    public String getFileUrl(String filePath) {
        if (isR2Storage()) {
            // Generate presigned URL (or public URL if public read)
            // For simplicity in this demo, usually we'd generate a presigned URL.
            // But software.amazon.awssdk.s3.S3Utilities is simpler if bucket is public,
            // otherwise we need S3Presigner.
            // Let's assume for now we return the key and the controller handles the
            // redirect or stream proxy.
            // Actually, let's implement presigned URL generation properly if needed,
            // or just return the key for the controller to use S3Client to getObject.
            // Let's return the key for now and let the download endpoint stream the
            // content.
            return filePath;
        } else {
            return this.localStoragePath.resolve(filePath).toString();
        }
    }

    /**
     * Get input stream for file content
     */
    public InputStream getFileStream(String filePath, StorageType storageType) throws IOException {
        if (storageType == StorageType.R2 && s3Client != null) {
            return s3Client.getObject(builder -> builder
                    .bucket(storageConfig.getR2().getBucket())
                    .key(filePath));
        } else {
            Path path = this.localStoragePath.resolve(filePath);
            if (!Files.exists(path)) {
                throw new IOException("File not found: " + filePath);
            }
            return Files.newInputStream(path);
        }
    }

    /**
     * Download file content as byte array
     */
    public byte[] downloadFile(String filePath) throws IOException {
        if (isR2Storage()) {
            try (InputStream stream = s3Client.getObject(builder -> builder
                    .bucket(storageConfig.getR2().getBucket())
                    .key(filePath))) {
                return stream.readAllBytes();
            }
        } else {
            Path path = this.localStoragePath.resolve(filePath);
            if (!Files.exists(path)) {
                throw new IOException("File not found: " + filePath);
            }
            return Files.readAllBytes(path);
        }
    }
}
