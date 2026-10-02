package edu.wpi.citizens.tokenservice;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import edu.wpi.citizens.tokenservice.AuditRepository.Event;
import edu.wpi.citizens.tokenservice.AuditRepository.Outcome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    static final int MAX_ISSUE_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final TokenRepository repository;
    private final AuditRepository audit;
    private final TokenGenerator generator;
    private final TokenProperties properties;
    private final Clock clock;

    public TokenService(TokenRepository repository, AuditRepository audit, TokenGenerator generator,
                        TokenProperties properties, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.generator = generator;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Returns the active token for the link, creating one if there is none.
     * Safe to call many times and from many threads: the database allows only
     * one active token per link, so a caller that loses the race gets the winner.
     */
    public AccountToken issue(TokenLink link, RequestContext context) {
        Optional<AccountToken> existing = repository.findActive(link);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant expiresAt = clock.instant().plus(Duration.ofDays(properties.ttlDays()));
        for (int attempt = 1; attempt <= MAX_ISSUE_ATTEMPTS; attempt++) {
            try {
                AccountToken created = repository.insert(
                        generator.next(), properties.routingNumber(), link, expiresAt);
                audit.insert(created.tokenId(), Event.ISSUE, context, Outcome.ALLOWED);
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

    /**
     * Resolves a token to the account it stands for, or returns empty.
     * Fails closed: an unknown token, a token that is not ACTIVE, an expired token and a
     * routing mismatch all give the same empty result. Every attempt writes an audit row
     * in the same transaction.
     */
    @Transactional
    public Optional<UUID> detokenize(String routingNumber, String tokenValue, RequestContext context) {
        Optional<AccountToken> found = tokenValue == null
                ? Optional.empty()
                : repository.findByValue(tokenValue);
        UUID tokenId = found.map(AccountToken::tokenId).orElse(null);
        Optional<String> denial = denialReason(found, routingNumber);
        if (denial.isPresent()) {
            audit.insert(tokenId, Event.DETOKENIZE, context, Outcome.DENIED);
            log.info("detokenize denied reason={} token_id={} request_id={}",
                    denial.get(), tokenId, context.requestId());
            return Optional.empty();
        }
        repository.markUsed(tokenId, clock.instant());
        audit.insert(tokenId, Event.DETOKENIZE, context, Outcome.ALLOWED);
        return found.map(AccountToken::accountRef);
    }

    /** The reason is for the service log only. It is never returned to the caller. */
    private Optional<String> denialReason(Optional<AccountToken> found, String routingNumber) {
        if (found.isEmpty()) {
            return Optional.of("unknown_token");
        }
        AccountToken token = found.get();
        if (token.status() != TokenStatus.ACTIVE) {
            return Optional.of("status_" + token.status());
        }
        if (!token.expiresAt().isAfter(clock.instant())) {
            return Optional.of("expired");
        }
        if (!token.routingNumber().equals(routingNumber)) {
            return Optional.of("routing_mismatch");
        }
        return Optional.empty();
    }
}
