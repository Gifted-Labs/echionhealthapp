package com.giftedlabs.echoinhealthbackend.security;

/**
 * Identifies the super admin behind the current request, when it is an impersonated one.
 *
 * <p>Held per request thread rather than passed through every service signature: impersonation is
 * ambient context, and threading a nullable "real actor" argument through the whole domain would
 * be invasive and easy to forget at exactly the call site that matters. The filter sets it, the
 * audit service reads it, and the filter clears it — always, including on the error path.
 */
public final class ImpersonationContext {

    private static final ThreadLocal<Details> CURRENT = new ThreadLocal<>();

    /**
     * @param impersonatorUserId id of the super admin who started the session
     * @param impersonatorEmail  their email, recorded so audit rows need no extra lookup
     * @param impersonationId    unique per session, so one session can be revoked alone
     */
    public record Details(String impersonatorUserId, String impersonatorEmail, String impersonationId) {
    }

    private ImpersonationContext() {
    }

    public static void set(Details details) {
        CURRENT.set(details);
    }

    /** @return the impersonation details, or null when the request is an ordinary one */
    public static Details get() {
        return CURRENT.get();
    }

    public static boolean isImpersonating() {
        return CURRENT.get() != null;
    }

    /**
     * Must be called in a finally block. Request threads are pooled, so a context left behind
     * would leak one user's impersonation state onto an unrelated later request.
     */
    public static void clear() {
        CURRENT.remove();
    }
}
