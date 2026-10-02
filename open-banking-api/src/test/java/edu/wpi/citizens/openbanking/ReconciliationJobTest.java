package edu.wpi.citizens.openbanking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class ReconciliationJobTest {

    private static final ConsentLink LINK =
            new ConsentLink("consent-1", "user-1", "alice", "aggregator-ui", "acc-1001", 100L);

    private final ConsentRegistry registry = new ConsentRegistry();
    private final KeycloakConsentClient keycloak = mock(KeycloakConsentClient.class);
    private final TokenServiceClient tokenService = mock(TokenServiceClient.class);
    private final ReconciliationJob job = new ReconciliationJob(registry, keycloak, tokenService);

    @BeforeEach
    void registerLink() {
        registry.register(LINK);
    }

    @Test
    void leavesTheLinkAloneWhileTheGrantStillExists() {
        when(keycloak.grantCreatedAt("user-1", "aggregator-ui")).thenReturn(Optional.of(100L));

        job.run();

        verify(tokenService, never()).revokeByConsent(any(), any(), any());
        assertThat(registry.all()).containsExactly(LINK);
    }

    @Test
    void revokesTheTokensAndForgetsTheLinkWhenTheGrantIsGone() {
        when(keycloak.grantCreatedAt("user-1", "aggregator-ui")).thenReturn(Optional.empty());

        job.run();

        verify(tokenService).revokeByConsent(eq("consent-1"), eq("CONSENT_REVOKED"), any());
        assertThat(registry.all()).isEmpty();
    }

    @Test
    void treatsARevokeAndRegrantBetweenRunsAsARevocation() {
        when(keycloak.grantCreatedAt("user-1", "aggregator-ui")).thenReturn(Optional.of(200L));

        job.run();

        verify(tokenService).revokeByConsent(eq("consent-1"), eq("CONSENT_REVOKED"), any());
        assertThat(registry.all()).isEmpty();
    }

    @Test
    void doesNotRevokeWhenKeycloakCannotBeReached() {
        when(keycloak.grantCreatedAt(any(), any())).thenThrow(new ResourceAccessException("down"));

        job.run();

        verify(tokenService, never()).revokeByConsent(any(), any(), any());
        assertThat(registry.all()).containsExactly(LINK);
    }

    @Test
    void keepsTheLinkToRetryWhenTheTokenServiceCannotBeReached() {
        when(keycloak.grantCreatedAt("user-1", "aggregator-ui")).thenReturn(Optional.empty());
        doThrow(new ResourceAccessException("down")).when(tokenService).revokeByConsent(any(), any(), any());

        job.run();

        assertThat(registry.all()).containsExactly(LINK);
    }
}
