package com.giftedlabs.echoinhealthbackend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every documented operation must either require a bearer token or be a deliberate exception.
 *
 * <p>This exists because of a failure mode that looks nothing like its cause. Declaring a
 * {@code @SecurityScheme} registers it under {@code components.securitySchemes} and makes the
 * Authorize button appear — but Swagger UI attaches the {@code Authorization} header only to
 * operations that also declare a matching <em>requirement</em>. With the scheme declared and the
 * requirement missing, the UI accepts a token, stores it, and sends every request without it. The
 * user sees 403 from Swagger and success from Postman for the identical call, and concludes the
 * token or the login is broken.
 *
 * <p>Nothing about that is visible in a controller. It cannot be caught by reading the code, only
 * by reading the generated document — which is what this does. 12 of 117 operations were covered
 * when this test was written.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "SWAGGER_ENABLED=true",
                "springdoc.api-docs.enabled=true"
        })
class OpenApiSecurityCoverageTest {

    /**
     * Operations reachable without a token, matching the {@code permitAll} matchers in
     * {@code SecurityConfig}. Adding an entry here is a deliberate act with a reviewer attached;
     * forgetting the annotation is not.
     */
    private static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "POST /auth/register",
            "POST /auth/login",
            "POST /auth/refresh",
            "POST /auth/verify-email",
            "POST /auth/resend-verification",
            // Authenticated by a single-use token in the query string; EventSource cannot send
            // an Authorization header.
            "GET /collaboration/notifications/stream");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    private static JsonNode document;

    @BeforeAll
    static void resetDocument() {
        document = null;
    }

    private JsonNode apiDocs() throws Exception {
        if (document == null) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/api/api-docs"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode())
                    .as("the OpenAPI document must be reachable for this test to mean anything")
                    .isEqualTo(200);
            document = MAPPER.readTree(response.body());
        }
        return document;
    }

    @Test
    @DisplayName("the bearer scheme is registered, or the Authorize button cannot exist")
    void bearerSchemeIsRegistered() throws Exception {
        JsonNode schemes = apiDocs().path("components").path("securitySchemes");
        assertThat(schemes.has(OpenApiConfig.BEARER_SCHEME))
                .as("securitySchemes must declare '%s'", OpenApiConfig.BEARER_SCHEME)
                .isTrue();
        assertThat(schemes.path(OpenApiConfig.BEARER_SCHEME).path("scheme").asText()).isEqualTo("bearer");
    }

    @Test
    @DisplayName("every operation either requires the bearer token or is a declared public endpoint")
    void everyOperationIsCoveredOrDeliberatelyPublic() throws Exception {
        JsonNode doc = apiDocs();
        boolean globalSecurity = doc.has("security") && doc.path("security").isArray()
                && !doc.path("security").isEmpty();

        List<String> unprotected = new ArrayList<>();
        int total = 0;

        for (var pathEntry : doc.path("paths").properties()) {
            String path = pathEntry.getKey();
            for (var opEntry : pathEntry.getValue().properties()) {
                String verb = opEntry.getKey();
                if (!Set.of("get", "post", "put", "patch", "delete").contains(verb)) {
                    continue;
                }
                total++;
                String id = verb.toUpperCase() + " " + path;
                JsonNode operation = opEntry.getValue();

                boolean optedOut = operation.has("security") && operation.path("security").isEmpty();
                boolean declaresOwn = operation.has("security") && !operation.path("security").isEmpty();
                boolean covered = declaresOwn || (globalSecurity && !optedOut);

                if (covered == PUBLIC_OPERATIONS.contains(id)) {
                    // Covered-and-public, or uncovered-and-not-public: both are wrong.
                    unprotected.add(id + (covered ? "  (public endpoint still demands a token)"
                            : "  (no security requirement — Swagger will not send the token)"));
                }
            }
        }

        assertThat(total).as("the document should describe the whole API").isGreaterThan(100);
        assertThat(unprotected)
                .as("""
                        Operations whose documented security does not match reality.

                        An operation with no security requirement makes Swagger UI send "Try it \
                        out" without the Authorization header, which returns 403 while the same \
                        call from Postman succeeds. Either the endpoint is genuinely public — add \
                        it to PUBLIC_OPERATIONS and annotate it @SecurityRequirements — or it is \
                        not, and the global requirement in OpenApiConfig should already cover it.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the first server entry is the deployed host, which is what Swagger preselects")
    void productionServerIsListedFirst() throws Exception {
        JsonNode servers = apiDocs().path("servers");
        assertThat(servers).isNotEmpty();
        assertThat(servers.get(0).path("url").asText()).startsWith("https://");
    }
}
