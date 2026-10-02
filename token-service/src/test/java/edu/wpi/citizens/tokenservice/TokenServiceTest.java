package edu.wpi.citizens.tokenservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final TokenLink LINK = new TokenLink(UUID.randomUUID(), "aggregator-ui", "consent-1");

    private static final RequestContext CONTEXT = new RequestContext("test", "request-1");

    private final TokenRepository repository = mock(TokenRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final TokenGenerator generator = mock(TokenGenerator.class);
    private final TokenService service = new TokenService(repository, audit, generator,
            new TokenProperties("123456780", 365), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void returnsExistingActiveTokenWithoutInserting() {
        AccountToken existing = token("482910375526");
        when(repository.findActive(LINK)).thenReturn(Optional.of(existing));

        AccountToken result = service.issue(LINK, CONTEXT);

        assertThat(result).isEqualTo(existing);
        verify(repository, never()).insert(any(), any(), any(), any());
    }

    @Test
    void retriesWithANewValueWhenTheTokenValueCollides() {
        AccountToken created = token("222222222222");
        when(repository.findActive(LINK)).thenReturn(Optional.empty());
        when(generator.next()).thenReturn("111111111111", "222222222222");
        when(repository.insert(eq("111111111111"), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("uq_token_value"));
        when(repository.insert(eq("222222222222"), any(), any(), any())).thenReturn(created);

        AccountToken result = service.issue(LINK, CONTEXT);

        assertThat(result).isEqualTo(created);
    }

    @Test
    void returnsTheWinnerWhenAnotherCallerIssuedTheSameLinkFirst() {
        AccountToken winner = token("333333333333");
        when(repository.findActive(LINK)).thenReturn(Optional.empty(), Optional.of(winner));
        when(generator.next()).thenReturn("111111111111");
        when(repository.insert(any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("uq_active_token_per_link"));

        AccountToken result = service.issue(LINK, CONTEXT);

        assertThat(result).isEqualTo(winner);
    }

    @Test
    void givesUpAfterFiveCollisions() {
        when(repository.findActive(LINK)).thenReturn(Optional.empty());
        when(generator.next()).thenReturn("111111111111");
        when(repository.insert(any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("uq_token_value"));

        assertThatThrownBy(() -> service.issue(LINK, CONTEXT)).isInstanceOf(TokenIssueException.class);
        verify(repository, times(TokenService.MAX_ISSUE_ATTEMPTS)).insert(any(), any(), any(), any());
    }

    private static AccountToken token(String value) {
        return new AccountToken(UUID.randomUUID(), value, "123456780", LINK.accountRef(),
                LINK.clientId(), LINK.consentId(), TokenStatus.ACTIVE, NOW, NOW.plusSeconds(60),
                null, null, null);
    }
}
