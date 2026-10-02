package edu.wpi.citizens.tokenservice;

import java.time.Instant;
import java.util.UUID;

/** What support tooling may see about a token: its state, not its full value. */
public record TokenMetadata(
        UUID tokenId,
        String tokenValueMasked,
        String clientId,
        String consentId,
        TokenStatus status,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokeReason,
        Instant lastUsedAt) {

    public static TokenMetadata from(AccountToken token) {
        return new TokenMetadata(token.tokenId(), token.maskedValue(), token.clientId(), token.consentId(),
                token.status(), token.issuedAt(), token.expiresAt(), token.revokedAt(),
                token.revokeReason(), token.lastUsedAt());
    }
}
