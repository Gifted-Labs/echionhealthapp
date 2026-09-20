package com.giftedlabs.echoinhealthbackend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service for JWT token generation, validation, and claims extraction
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    @Value("${jwt.issuer}")
    private String issuer;

    /**
     * Extract username (email) from JWT token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractOrganizationId(String token) {
        return extractClaim(token, claims -> claims.get("organizationId", String.class));
    }

    /**
     * Extract a specific claim from the token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /** Claim naming the super admin behind an impersonated session. */
    public static final String CLAIM_IMPERSONATED_BY_ID = "impersonatedByUserId";

    /** Claim carrying that super admin's email, so audit rows need no extra lookup. */
    public static final String CLAIM_IMPERSONATED_BY_EMAIL = "impersonatedByEmail";

    /** Unique per impersonation session, so one session can be revoked without affecting others. */
    public static final String CLAIM_IMPERSONATION_ID = "impersonationId";

    /**
     * Generate access token for user
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(buildPrincipalClaims(userDetails), userDetails);
    }

    /**
     * Generate access token with extra claims
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    /**
     * Generate refresh token
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(buildPrincipalClaims(userDetails), userDetails, refreshExpiration);
    }

    /**
     * Mints an access token that acts as {@code userDetails} while recording who is really behind
     * it.
     *
     * <p>Deliberately short-lived and issued without a matching refresh token, so an impersonated
     * session cannot be extended or quietly kept alive. The {@code impersonatedBy} claims travel
     * with every request made under this token, which is what lets the audit trail name both
     * identities and the UI show a persistent banner.
     *
     * @param userDetails          the user being impersonated
     * @param impersonatorUserId   id of the super admin
     * @param impersonatorEmail    email of the super admin
     * @param ttlMillis            lifetime, capped by the caller
     */
    public String generateImpersonationToken(UserDetails userDetails, String impersonatorUserId,
            String impersonatorEmail, long ttlMillis) {
        Map<String, Object> claims = buildPrincipalClaims(userDetails);
        claims.put(CLAIM_IMPERSONATED_BY_ID, impersonatorUserId);
        claims.put(CLAIM_IMPERSONATED_BY_EMAIL, impersonatorEmail);
        claims.put(CLAIM_IMPERSONATION_ID, java.util.UUID.randomUUID().toString());
        return buildToken(claims, userDetails, ttlMillis);
    }

    public String extractImpersonatedByUserId(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_IMPERSONATED_BY_ID, String.class));
    }

    public String extractImpersonatedByEmail(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_IMPERSONATED_BY_EMAIL, String.class));
    }

    /**
     * Per-session identifier, so stopping impersonation can revoke exactly this token rather than
     * every token ever issued for the target user.
     */
    public String extractImpersonationId(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_IMPERSONATION_ID, String.class));
    }

    private Map<String, Object> buildPrincipalClaims(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        if (userDetails instanceof com.giftedlabs.echoinhealthbackend.security.AuthenticatedUser principal) {
            claims.put("userId", principal.getUserId());
            claims.put("organizationId", principal.getOrganizationId());
            claims.put("role", principal.getRole().name());
        }
        return claims;
    }

    /**
     * Build JWT token with specified expiration
     */
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuer(issuer)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validate token against user details
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final Claims claims = extractAllClaims(token);
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername())
                && issuer.equals(claims.getIssuer())
                && !claims.getExpiration().before(new Date());
    }

    /**
     * Check if token is expired
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extract expiration date from token
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extract all claims from token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(secretKey)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get signing key from secret
     */
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64URL.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Get JWT expiration time in milliseconds
     */
    public Long getJwtExpiration() {
        return jwtExpiration;
    }
}
