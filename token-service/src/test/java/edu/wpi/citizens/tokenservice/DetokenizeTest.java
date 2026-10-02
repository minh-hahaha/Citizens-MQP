package edu.wpi.citizens.tokenservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import edu.wpi.citizens.tokenservice.AuditRepository.Event;
import edu.wpi.citizens.tokenservice.AuditRepository.Outcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** One test per fail-closed branch of detokenize. */
class DetokenizeTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final String ROUTING = "123456780";
    private static final String VALUE = "482910375526";
    private static final RequestContext CONTEXT = new RequestContext("payment-receiver", "request-1");

    private final TokenRepository repository = mock(TokenRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final TokenService service = new TokenService(repository, audit, mock(TokenGenerator.class),
            new TokenProperties(ROUTING, 365), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void resolvesAnActiveTokenAndRecordsTheUse() {
        AccountToken token = token(TokenStatus.ACTIVE, NOW.plusSeconds(60));
        when(repository.findByValue(VALUE)).thenReturn(Optional.of(token));

        Optional<UUID> result = service.detokenize(ROUTING, VALUE, CONTEXT);

        assertThat(result).contains(token.accountRef());
        verify(repository).markUsed(token.tokenId(), NOW);
        verify(audit).insert(token.tokenId(), Event.DETOKENIZE, CONTEXT, Outcome.ALLOWED);
    }

    @Test
    void deniesAnUnknownToken() {
        when(repository.findByValue(VALUE)).thenReturn(Optional.empty());

        Optional<UUID> result = service.detokenize(ROUTING, VALUE, CONTEXT);

        assertThat(result).isEmpty();
        verify(audit).insert(null, Event.DETOKENIZE, CONTEXT, Outcome.DENIED);
    }

    @Test
    void deniesAMissingTokenValue() {
        Optional<UUID> result = service.detokenize(ROUTING, null, CONTEXT);

        assertThat(result).isEmpty();
        verify(audit).insert(null, Event.DETOKENIZE, CONTEXT, Outcome.DENIED);
    }

    @ParameterizedTest
    @EnumSource(value = TokenStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void deniesATokenThatIsNotActive(TokenStatus status) {
        AccountToken token = token(status, NOW.plusSeconds(60));
        when(repository.findByValue(VALUE)).thenReturn(Optional.of(token));

        Optional<UUID> result = service.detokenize(ROUTING, VALUE, CONTEXT);

        assertDenied(result, token);
    }

    @Test
    void deniesAnActiveTokenPastItsExpiryEvenIfTheExpiryJobHasNotRun() {
        AccountToken token = token(TokenStatus.ACTIVE, NOW);
        when(repository.findByValue(VALUE)).thenReturn(Optional.of(token));

        Optional<UUID> result = service.detokenize(ROUTING, VALUE, CONTEXT);

        assertDenied(result, token);
    }

    @Test
    void deniesARoutingNumberThatDoesNotMatch() {
        AccountToken token = token(TokenStatus.ACTIVE, NOW.plusSeconds(60));
        when(repository.findByValue(VALUE)).thenReturn(Optional.of(token));

        Optional<UUID> result = service.detokenize("999999999", VALUE, CONTEXT);

        assertDenied(result, token);
    }

    @Test
    void deniesAMissingRoutingNumber() {
        AccountToken token = token(TokenStatus.ACTIVE, NOW.plusSeconds(60));
        when(repository.findByValue(VALUE)).thenReturn(Optional.of(token));

        Optional<UUID> result = service.detokenize(null, VALUE, CONTEXT);

        assertDenied(result, token);
    }

    private void assertDenied(Optional<UUID> result, AccountToken token) {
        assertThat(result).isEmpty();
        verify(repository, never()).markUsed(any(), any());
        verify(audit).insert(token.tokenId(), Event.DETOKENIZE, CONTEXT, Outcome.DENIED);
    }

    private static AccountToken token(TokenStatus status, Instant expiresAt) {
        return new AccountToken(UUID.randomUUID(), VALUE, ROUTING, UUID.randomUUID(),
                "aggregator-ui", "consent-1", status, NOW.minusSeconds(60), expiresAt, null, null, null);
    }
}
