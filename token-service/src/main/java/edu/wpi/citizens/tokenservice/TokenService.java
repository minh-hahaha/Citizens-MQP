package edu.wpi.citizens.tokenservice;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    static final int MAX_ISSUE_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final TokenRepository repository;
    private final TokenGenerator generator;
    private final TokenProperties properties;
    private final Clock clock;

    public TokenService(TokenRepository repository, TokenGenerator generator,
                        TokenProperties properties, Clock clock) {
        this.repository = repository;
        this.generator = generator;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Returns the active token for the link, creating one if there is none.
     * Safe to call many times and from many threads: the database allows only
     * one active token per link, so a caller that loses the race gets the winner.
     */
    public AccountToken issue(TokenLink link) {
        Optional<AccountToken> existing = repository.findActive(link);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant expiresAt = clock.instant().plus(Duration.ofDays(properties.ttlDays()));
        for (int attempt = 1; attempt <= MAX_ISSUE_ATTEMPTS; attempt++) {
            try {
                AccountToken created = repository.insert(
                        generator.next(), properties.routingNumber(), link, expiresAt);
                log.info("issued token_id={} value={}", created.tokenId(), created.maskedValue());
                return created;
            } catch (DuplicateKeyException e) {
                Optional<AccountToken> winner = repository.findActive(link);
                if (winner.isPresent()) {
                    return winner.get();
                }
                log.warn("token value collision on attempt {} of {}", attempt, MAX_ISSUE_ATTEMPTS);
            }
        }
        throw new TokenIssueException("Could not generate a unique token value");
    }
}
