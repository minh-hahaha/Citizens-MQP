package edu.wpi.citizens.openbanking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ConsentServiceTest {

    private static final Caller ALICE = new Caller("user-1", "alice", "aggregator-ui");

    private final ConsentRegistry registry = new ConsentRegistry();
    private final KeycloakConsentClient keycloak = mock(KeycloakConsentClient.class);
    private final ConsentService service = new ConsentService(registry, keycloak);

    @Test
    void createsAConsentIdTheFirstTimeAndReusesItAfterwards() {
        when(keycloak.grantCreatedAt("user-1", "aggregator-ui")).thenReturn(Optional.of(100L));

        String first = service.consentIdFor(ALICE, "acc-1001");
        String second = service.consentIdFor(ALICE, "acc-1001");

        assertThat(second).isEqualTo(first);
        verify(keycloak, times(1)).grantCreatedAt("user-1", "aggregator-ui");
    }

    @Test
    void givesDifferentConsentIdsToDifferentAccounts() {
        when(keycloak.grantCreatedAt("user-1", "aggregator-ui")).thenReturn(Optional.of(100L));

        String checking = service.consentIdFor(ALICE, "acc-1001");
        String savings = service.consentIdFor(ALICE, "acc-1002");

        assertThat(checking).isNotEqualTo(savings);
    }

    @Test
    void refusesToStartANewLinkWhenKeycloakHasNoGrant() {
        when(keycloak.grantCreatedAt("user-1", "aggregator-ui")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consentIdFor(ALICE, "acc-1001"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(registry.all()).isEmpty();
    }
}
