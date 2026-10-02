package edu.wpi.citizens.tokenservice;

import java.time.Instant;
import java.util.UUID;

public record IssueTokenResponse(
        UUID tokenId,
        String tokenValue,
        String routingNumber,
        TokenStatus status,
        Instant expiresAt) {

    public static IssueTokenResponse from(AccountToken token) {
        return new IssueTokenResponse(token.tokenId(), token.tokenValue(), token.routingNumber(),
                token.status(), token.expiresAt());
    }
}
