package edu.wpi.citizens.tokenservice;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The whole Token Service: issue a token, resolve it, revoke it. */
@RestController
@RequestMapping("/v1/tokens")
public class TokenController {

    record IssueRequest(UUID accountRef, String clientId, String consentId) {
    }

    record IssuedToken(String tokenValue, String routingNumber) {
    }

    record DetokenizeRequest(String routingNumber, String tokenValue) {
    }

    record RevokeByConsentRequest(String consentId) {
    }

    private static final long SMALLEST_12_DIGIT_NUMBER = 100_000_000_000L;
    private static final long COUNT_OF_12_DIGIT_NUMBERS = 900_000_000_000L;

    private final JdbcClient jdbc;
    private final String routingNumber;
    private final SecureRandom random = new SecureRandom();

    public TokenController(JdbcClient jdbc, @Value("${tokens.routing-number}") String routingNumber) {
        this.jdbc = jdbc;
        this.routingNumber = routingNumber;
    }

    /** Returns the active token for this account, aggregator and consent. Creates it the first time. */
    @PostMapping
    public IssuedToken issue(@RequestBody IssueRequest request) {
        Optional<String> existing = jdbc.sql("""
                        SELECT token_value FROM account_token
                        WHERE account_ref = :accountRef AND client_id = :clientId
                          AND consent_id = :consentId AND status = 'ACTIVE'
                        """)
                .param("accountRef", request.accountRef())
                .param("clientId", request.clientId())
                .param("consentId", request.consentId())
                .query(String.class)
                .optional();
        if (existing.isPresent()) {
            return new IssuedToken(existing.get(), routingNumber);
        }
        // A random 12-digit number with no leading zero, so it looks like an account number.
        String tokenValue = String.valueOf(SMALLEST_12_DIGIT_NUMBER + random.nextLong(COUNT_OF_12_DIGIT_NUMBERS));
        jdbc.sql("""
                        INSERT INTO account_token (token_value, routing_number, account_ref, client_id, consent_id)
                        VALUES (:tokenValue, :routingNumber, :accountRef, :clientId, :consentId)
                        """)
                .param("tokenValue", tokenValue)
                .param("routingNumber", routingNumber)
                .param("accountRef", request.accountRef())
                .param("clientId", request.clientId())
                .param("consentId", request.consentId())
                .update();
        return new IssuedToken(tokenValue, routingNumber);
    }

    /**
     * Resolves a token to the account it stands for. This is what the bank's payment side
     * would call. An unknown token and a revoked token get the same answer.
     */
    @PostMapping("/detokenize")
    public ResponseEntity<?> detokenize(@RequestBody DetokenizeRequest request) {
        Optional<UUID> accountRef = jdbc.sql("""
                        SELECT account_ref FROM account_token
                        WHERE token_value = :tokenValue AND routing_number = :routingNumber AND status = 'ACTIVE'
                        """)
                .param("tokenValue", request.tokenValue())
                .param("routingNumber", request.routingNumber())
                .query(UUID.class)
                .optional();
        if (accountRef.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Token could not be resolved"));
        }
        return ResponseEntity.ok(Map.of("accountRef", accountRef.get()));
    }

    /** Revokes every token issued under a consent. */
    @PostMapping("/revoke-by-consent")
    public Map<String, Integer> revokeByConsent(@RequestBody RevokeByConsentRequest request) {
        int revokedCount = jdbc.sql("""
                        UPDATE account_token SET status = 'REVOKED', revoked_at = now()
                        WHERE consent_id = :consentId AND status = 'ACTIVE'
                        """)
                .param("consentId", request.consentId())
                .update();
        return Map.of("revokedCount", revokedCount);
    }
}
