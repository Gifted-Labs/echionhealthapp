package com.giftedlabs.echoinhealthbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues short-lived, single-use tokens that authenticate a server-sent-events subscription.
 *
 * <p>The notification stream sits behind the JWT filter, but the browser's {@code EventSource} API
 * cannot set request headers, so the front-end had no way to present its bearer token and every
 * connection attempt was rejected. A query parameter is the only channel {@code EventSource}
 * offers, and putting the real access token there would write a long-lived credential into
 * browser history, proxy logs and referrer headers.
 *
 * <p>These tokens are therefore deliberately weak-by-design in scope and strong in lifetime
 * limits: valid for one minute, usable exactly once, and good only for opening a stream.
 */
@Service
@Slf4j
public class StreamTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Grant> grants = new ConcurrentHashMap<>();

    private record Grant(String userId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Mints a token for the given user. The caller is already authenticated by the normal bearer
     * token when they ask for this.
     */
    public String issue(String userId) {
        purgeExpired();

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        grants.put(token, new Grant(userId, Instant.now().plus(TOKEN_TTL)));
        return token;
    }

    /**
     * Redeems a token, returning the user it was issued to.
     *
     * <p>Removal happens whether or not the token turns out to be valid, so a token cannot be
     * replayed and an expired one cannot linger.
     *
     * @return the user id, or null when the token is unknown or expired
     */
    public String redeem(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        Grant grant = grants.remove(token);
        if (grant == null) {
            return null;
        }
        if (grant.isExpired()) {
            log.debug("Rejected expired stream token");
            return null;
        }
        return grant.userId();
    }

    private void purgeExpired() {
        grants.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
