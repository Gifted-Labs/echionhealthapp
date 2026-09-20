package com.giftedlabs.echoinhealthbackend.config;

import com.giftedlabs.echoinhealthbackend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for JWT-based authentication
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    /**
     * Applied when {@code CORS_ALLOWED_ORIGINS} is not set. These are a fallback, not the
     * configuration: a deployment that leaves them in place is logged as a warning at startup,
     * because a front-end served from any origin not listed here fails the preflight and every
     * call it makes comes back as an unexplained HTTP 403, long before authentication runs.
     */
    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost:3000",
            "http://localhost:4200",
            "http://localhost:8000",
            "https://echionhealth.com",
            // The deployed API host: Swagger UI is served from it and calls the API from the
            // browser, so it is an origin in its own right.
            "https://echionhealthapp-production.up.railway.app");

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Value("${SWAGGER_ENABLED:false}")
    private String swaggerEnabled;

    /**
     * Comma-separated origin patterns. Entries may contain {@code *} wildcards so preview
     * deployments (for example {@code https://*.vercel.app}) can be admitted without turning the
     * allow-list off, which {@code allowCredentials} would forbid anyway.
     */
    @Value("${cors.allowed-origins:}")
    private String allowedOriginsProperty;

    /**
     * Configure security filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] publicMatchers = isSwaggerEnabled()
                ? new String[] {
                        "/auth/register",
                        "/auth/login",
                        "/auth/verify-email",
                        "/auth/resend-verification",
                        "/auth/refresh",
                        // Authenticates via a single-use stream token in the query string:
                        // the browser EventSource API cannot send an Authorization header.
                        "/collaboration/notifications/stream",
                        "/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/configuration/**",
                        "/webjars/**",
                        "/error" }
                : new String[] {
                        "/auth/register",
                        "/auth/login",
                        "/auth/verify-email",
                        "/auth/resend-verification",
                        "/auth/refresh",
                        // Authenticates via a single-use stream token in the query string:
                        // the browser EventSource API cannot send an Authorization header.
                        "/collaboration/notifications/stream",
                        "/error" };
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (no /api prefix needed - context path handles it)
                        .requestMatchers(publicMatchers)
                        .permitAll()

                        // Admin endpoints require tenant or platform admin role
                        .requestMatchers("/admin/**").hasAnyRole("HOSPITAL_ADMIN", "ADMIN", "SUPER_ADMIN")

                        // All other endpoints require authentication
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private boolean isSwaggerEnabled() {
        if (swaggerEnabled == null) {
            return false;
        }

        String normalized = swaggerEnabled.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        return Boolean.parseBoolean(normalized);
    }

    /**
     * CORS configuration.
     *
     * <p>Origins come from configuration rather than source, because the browser never sees a
     * rejected origin as an authorization problem: Spring's CORS filter answers the preflight with
     * HTTP 403 "Invalid CORS request" before the security filter chain runs, so a correctly
     * authenticated admin calling a correctly permitted endpoint still reads "Forbidden".
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = resolveAllowedOrigins();

        CorsConfiguration configuration = new CorsConfiguration();
        // Patterns rather than exact origins: allowCredentials(true) forbids the "*" wildcard, but
        // permits wildcards inside a pattern, which is what preview deployments need.
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Parses the configured origin list, falling back to the development defaults. The effective
     * list is logged either way so a misconfigured front-end origin is visible in the deploy log
     * instead of being diagnosed from a 403 weeks later.
     */
    List<String> resolveAllowedOrigins() {
        List<String> configured = Arrays.stream(allowedOriginsProperty == null ? new String[0]
                : allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();

        if (configured.isEmpty()) {
            log.warn("CORS_ALLOWED_ORIGINS is not set; falling back to development origins {}. "
                    + "A production front-end served from any other origin will receive HTTP 403 "
                    + "on every request.", DEFAULT_ALLOWED_ORIGINS);
            return DEFAULT_ALLOWED_ORIGINS;
        }

        log.info("CORS allowed origin patterns: {}", configured);
        return configured;
    }

    /**
     * Authentication provider with password encoder
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Authentication manager bean
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Password encoder using BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
