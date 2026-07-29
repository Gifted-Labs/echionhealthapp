package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.exception.AuthenticationRateLimitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private record AttemptState(int failures, Instant lockedUntil) {}

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    @Value("${app.auth.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.auth.lock-minutes:15}")
    private long lockMinutes;

    public void assertNotBlocked(String identifier) {
        String key = normalize(identifier);
        AttemptState state = attempts.get(key);
        if (state != null && state.lockedUntil() != null && Instant.now().isBefore(state.lockedUntil())) {
            throw new AuthenticationRateLimitException("Too many failed login attempts. Try again later.");
        }
        if (state != null && state.lockedUntil() != null && Instant.now().isAfter(state.lockedUntil())) {
            attempts.remove(key);
        }
    }

    public void recordFailure(String identifier) {
        String key = normalize(identifier);
        attempts.compute(key, (ignored, current) -> {
            int failures = current == null ? 1 : current.failures() + 1;
            Instant lockedUntil = failures >= maxFailedAttempts
                    ? Instant.now().plus(Duration.ofMinutes(lockMinutes))
                    : null;
            return new AttemptState(failures, lockedUntil);
        });
    }

    public void clearFailures(String identifier) {
        attempts.remove(normalize(identifier));
    }

    private String normalize(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase();
    }
}
