package com.giftedlabs.echoinhealthbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live impersonation sessions so one can be ended before its token expires.
 *
 * <p>JWTs are self-contained and cannot be un-issued, so "stop impersonating" needs somewhere to
 * record that a particular session is over. Keyed on the token's {@code impersonationId} claim
 * rather than the user, so ending one session does not disturb the target user's own sign-in or a
 * second, unrelated impersonation of them.
 */
@Service
@Slf4j
public class ImpersonationSessionRegistry {

    private record Session(String impersonatorUserId, String targetUserId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, Session> active = new ConcurrentHashMap<>();

    public void register(String impersonationId, String impersonatorUserId, String targetUserId,
            Duration ttl) {
        purgeExpired();
        active.put(impersonationId, new Session(impersonatorUserId, targetUserId, Instant.now().plus(ttl)));
    }

    /**
     * @return true when the session is still live; false when it was stopped, expired, or was
     *         never registered — a token from before a restart, for instance, which should not be
     *         honoured either
     */
    public boolean isActive(String impersonationId) {
        if (impersonationId == null) {
            return false;
        }
        Session session = active.get(impersonationId);
        if (session == null) {
            return false;
        }
        if (session.isExpired()) {
            active.remove(impersonationId);
            return false;
        }
        return true;
    }

    /**
     * Ends a session immediately.
     *
     * @return true if a live session was ended, false if there was nothing to end
     */
    public boolean stop(String impersonationId) {
        if (impersonationId == null) {
            return false;
        }
        Session removed = active.remove(impersonationId);
        if (removed != null) {
            log.info("Impersonation session {} stopped (admin {} acting as user {})",
                    impersonationId, removed.impersonatorUserId(), removed.targetUserId());
            return true;
        }
        return false;
    }

    /** Ends every session started by one super admin. Used when that admin's own account is locked. */
    public void stopAllStartedBy(String impersonatorUserId) {
        active.entrySet().removeIf(entry -> entry.getValue().impersonatorUserId().equals(impersonatorUserId));
    }

    private void purgeExpired() {
        active.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
