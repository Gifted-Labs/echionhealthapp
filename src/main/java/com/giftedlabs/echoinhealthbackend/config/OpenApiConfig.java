package com.giftedlabs.echoinhealthbackend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Echoin Health API", version = "1.0", description = "HIPAA-Compliant Ultrasound Report Management System API", contact = @Contact(name = "Echoin Health", email = "support@echoinhealth.com")), servers = {
        @Server(url = "http://localhost:8080/api", description = "Local Development Server"),
        @Server(url = "https://echionhealthapp.onrender.com/api", description = "Production Server")
})
@SecurityScheme(name = "Bearer Authentication", type = SecuritySchemeType.HTTP, bearerFormat = "JWT", scheme = "bearer", in = SecuritySchemeIn.HEADER)
public class OpenApiConfig {
}
