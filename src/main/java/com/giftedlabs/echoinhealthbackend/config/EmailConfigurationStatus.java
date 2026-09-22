package com.giftedlabs.echoinhealthbackend.config;

/**
 * The outcome of checking outbound email configuration.
 *
 * <p>A value rather than an exception, so the same check can drive three different reactions:
 * abort startup, log and carry on, or report the component as unhealthy to an operator. Deciding
 * which of those to do is not the checker's job.
 *
 * @param valid        whether email can be expected to send at all
 * @param problem      what is wrong, in terms an operator can act on; {@code null} when valid
 * @param warning      something worth saying that is not disqualifying; {@code null} when there is none
 * @param senderDomain the domain parsed out of the sender address; {@code null} when unparseable
 * @param fromAddress  the configured sender, echoed back for diagnostics
 */
public record EmailConfigurationStatus(
        boolean valid,
        String problem,
        String warning,
        String senderDomain,
        String fromAddress) {

    public static EmailConfigurationStatus ok(String senderDomain, String fromAddress) {
        return new EmailConfigurationStatus(true, null, null, senderDomain, fromAddress);
    }

    public static EmailConfigurationStatus okWithWarning(String warning, String senderDomain, String fromAddress) {
        return new EmailConfigurationStatus(true, null, warning, senderDomain, fromAddress);
    }

    public static EmailConfigurationStatus invalid(String problem, String fromAddress) {
        return new EmailConfigurationStatus(false, problem, null, null, fromAddress);
    }

    /** One line suitable for a health endpoint. */
    public String summary() {
        if (!valid) {
            return "misconfigured: " + problem;
        }
        return warning == null
                ? "sending from " + fromAddress
                : "sending from " + fromAddress + " (" + warning + ")";
    }
}
