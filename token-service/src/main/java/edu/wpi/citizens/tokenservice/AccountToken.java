package edu.wpi.citizens.tokenservice;

import java.time.Instant;
import java.util.UUID;

/** One row of the account_token table. */
public record AccountToken(
        UUID tokenId,
        String tokenValue,
        String routingNumber,
        UUID accountRef,
        String clientId,
        String consentId,
        TokenStatus status,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokeReason,
        Instant lastUsedAt) {

    private static final int VISIBLE_SUFFIX_LENGTH = 4;

    /** Safe form for logs: never log the full token value. */
    public String maskedValue() {
        return "****" + tokenValue.substring(tokenValue.length() - VISIBLE_SUFFIX_LENGTH);
    }
}
