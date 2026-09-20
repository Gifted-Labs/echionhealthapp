package com.giftedlabs.echoinhealthbackend.security;

import com.giftedlabs.echoinhealthbackend.service.ImpersonationSessionRegistry;
import com.giftedlabs.echoinhealthbackend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter that validates tokens on each request
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ImpersonationSessionRegistry impersonationSessionRegistry;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Check if Authorization header is present and starts with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authentication is attempted separately from the chain call. Folding both into one try
        // would mean an exception thrown further down the chain was caught here and answered by
        // invoking the chain a second time.
        try {
            final String jwt = authHeader.substring(7);
            final String userEmail = jwtService.extractUsername(jwt);

            // If email is extracted and user is not already authenticated
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                // Validate token
                if (jwtService.isTokenValid(jwt, userDetails) && applyImpersonation(jwt)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("User authenticated: {}", userEmail);
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Request threads are pooled, so a context left behind would leak this request's
            // impersonation state onto an unrelated later request.
            ImpersonationContext.clear();
        }
    }

    /**
     * Establishes impersonation context for tokens that carry it.
     *
     * @return true when the request may proceed; false when the token claims impersonation but the
     *         session has been stopped or has expired, in which case the request is left
     *         unauthenticated and the security chain rejects it
     */
    private boolean applyImpersonation(String jwt) {
        String impersonatorId = jwtService.extractImpersonatedByUserId(jwt);
        if (impersonatorId == null) {
            return true;
        }

        String impersonationId = jwtService.extractImpersonationId(jwt);
        if (!impersonationSessionRegistry.isActive(impersonationId)) {
            log.warn("Rejected impersonation token for a session that is no longer active");
            return false;
        }

        ImpersonationContext.set(new ImpersonationContext.Details(
                impersonatorId,
                jwtService.extractImpersonatedByEmail(jwt),
                impersonationId));
        return true;
    }
}
