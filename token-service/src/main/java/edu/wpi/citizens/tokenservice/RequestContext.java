package edu.wpi.citizens.tokenservice;

import java.util.UUID;

/** Who made the call and under which request ID. Both end up in the audit trail. */
public record RequestContext(String actor, String requestId) {

    public static final String ACTOR_HEADER = "x-actor";
    public static final String REQUEST_ID_HEADER = "x-fapi-interaction-id";

    private static final int MAX_LENGTH = 64;
    private static final String UNKNOWN_ACTOR = "unknown";

    /** Builds a context from raw header values, which may be missing or too long. */
    public static RequestContext fromHeaders(String actor, String requestId) {
        return new RequestContext(
                clean(actor, UNKNOWN_ACTOR),
                clean(requestId, UUID.randomUUID().toString()));
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.strip();
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH);
    }
}
