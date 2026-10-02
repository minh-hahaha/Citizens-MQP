package edu.wpi.citizens.openbanking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConsentRegistryTest {

    private final ConsentRegistry registry = new ConsentRegistry();
    private final Caller alice = new Caller("alice", "aggregator-ui");

    @Test
    void returnsTheSameConsentIdForTheSameLink() {
        String first = registry.consentIdFor(alice, "acc-1001");
        String second = registry.consentIdFor(alice, "acc-1001");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void returnsDifferentConsentIdsForDifferentAccountsAndClients() {
        String account1 = registry.consentIdFor(alice, "acc-1001");
        String account2 = registry.consentIdFor(alice, "acc-1002");
        String otherClient = registry.consentIdFor(new Caller("alice", "other-app"), "acc-1001");

        assertThat(account1).isNotEqualTo(account2).isNotEqualTo(otherClient);
    }
}
