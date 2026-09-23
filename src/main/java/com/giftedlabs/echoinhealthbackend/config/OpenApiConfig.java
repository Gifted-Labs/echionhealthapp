package com.giftedlabs.echoinhealthbackend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration.
 *
 * <p><b>The security requirement is declared globally on purpose.</b> Declaring the scheme alone
 * only registers it under {@code components.securitySchemes}: Swagger UI then shows the Authorize
 * dialog and stores the token, but attaches the {@code Authorization} header <em>only</em> to
 * operations that also declare a matching requirement. Previously 12 of 117 operations did, so
 * every other "Try it out" was sent unauthenticated and came back 403 — while the identical
 * request from Postman, where the caller sets the header themselves, worked. The symptom looks
 * like a broken login and is really a documentation gap.
 *
 * <p>Declaring it here applies it to every operation, so the default for a new endpoint is
 * "authenticated" rather than "silently unauthenticated". The genuinely public endpoints opt out
 * with {@code @SecurityRequirements} (plural, empty), which is a visible, greppable exception
 * rather than an omission. {@code OpenApiSecurityCoverageTest} fails the build if an operation is
 * neither covered nor on that list.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Echoin Health API",
                version = "1.0",
                description = "HIPAA-Compliant Ultrasound Report Management System API",
                contact = @Contact(name = "Echoin Health", email = "support@echoinhealth.com")),
        // Production first: this document is served from the deployed host, and Swagger UI
        // preselects the first entry. Listing localhost first pointed every trial request at a
        // machine that is usually not running, on a port that is not even the default.
        servers = {
                @Server(url = "https://echionhealthapp-production.up.railway.app/api",
                        description = "Production Server"),
                @Server(url = "http://localhost:8080/api", description = "Local Development Server")
        },
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
@SecurityScheme(
        name = OpenApiConfig.BEARER_SCHEME,
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer",
        in = SecuritySchemeIn.HEADER,
        description = "Paste the access token returned by POST /auth/login. Do not include the "
                + "word \"Bearer\" — Swagger adds it.")
public class OpenApiConfig {

    /** Referenced by the global requirement and by the public-endpoint opt-outs. */
    public static final String BEARER_SCHEME = "Bearer Authentication";
}
