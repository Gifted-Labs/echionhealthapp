package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.config.StorageConfig;
import com.giftedlabs.echoinhealthbackend.entity.StorageType;
import com.giftedlabs.echoinhealthbackend.exception.StorageOperationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceR2Test {

    private HttpServer server;
    private FileStorageService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void uploadsWithR2CompatiblePathAndKnownContentLength() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> transferEncoding = new AtomicReference<>();
        AtomicReference<String> contentLength = new AtomicReference<>();
        AtomicReference<byte[]> receivedBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
            contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            receivedBody.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, "");
        });
        server.start();

        byte[] body = "R2 upload regression test".getBytes(StandardCharsets.UTF_8);
        service = new FileStorageService(r2Config(server.getAddress().getPort(), "test-bucket"));
        service.init();

        String key = service.storeFile(
                new MockMultipartFile("file", "report.txt", "text/plain", body),
                "org-1",
                "user-1");

        assertThat(service.getCurrentStorageType()).isEqualTo(StorageType.R2);
        assertThat(key).startsWith("org-1/user-1/").endsWith("_report.txt");
        assertThat(method.get()).isEqualTo("PUT");
        assertThat(path.get()).isEqualTo("/test-bucket/" + key);
        assertThat(transferEncoding.get()).isNull();
        assertThat(contentLength.get()).isEqualTo(String.valueOf(body.length));
        assertThat(receivedBody.get()).isEqualTo(body);
    }

    @Test
    void mapsR2UploadFailuresToRetryableStorageException() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, 503, "unavailable"));
        server.start();

        service = new FileStorageService(r2Config(server.getAddress().getPort(), "test-bucket"));
        service.init();

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.txt", "text/plain", "test".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.storeFile(file, "org-1", "user-1"))
                .isInstanceOf(StorageOperationException.class)
                .hasMessage("File storage is temporarily unavailable. Please retry the upload.");
    }

    @Test
    void rejectsIncompleteR2ConfigurationInsteadOfFallingBackToLocalDisk() {
        StorageConfig config = r2Config(1, "");
        service = new FileStorageService(config);

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("storage.r2.bucket");
    }

    private StorageConfig r2Config(int port, String bucket) {
        StorageConfig config = new StorageConfig();
        config.setType("r2");
        config.getR2().setEndpoint("http://127.0.0.1:" + port);
        config.getR2().setBucket(bucket);
        config.getR2().setAccessKey("test-access-key");
        config.getR2().setSecretKey("test-secret-key");
        config.getR2().setRegion("auto");
        return config;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
