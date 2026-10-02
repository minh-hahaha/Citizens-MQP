package edu.wpi.citizens.openbanking;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Hands out the consentId for a link, creating one the first time a link is seen. */
@Service
public class ConsentService {

    private final ConsentRegistry registry;
    private final KeycloakConsentClient keycloak;

    public ConsentService(ConsentRegistry registry, KeycloakConsentClient keycloak) {
        this.registry = registry;
        this.keycloak = keycloak;
    }

    /**
     * A new link is only created while the customer's grant exists in Keycloak. This stops
     * an access token that is still valid after revocation from starting a new link.
     */
    public String consentIdFor(Caller caller, String accountId) {
        Optional<ConsentLink> existing = registry.find(caller.userId(), caller.clientId(), accountId);
        if (existing.isPresent()) {
            return existing.get().consentId();
        }
        long grantCreatedAt = keycloak.grantCreatedAt(caller.userId(), caller.clientId())
                .orElseThrow(() -> new AccessDeniedException("The customer has not consented"));
        ConsentLink link = new ConsentLink("consent-" + UUID.randomUUID(), caller.userId(),
                caller.username(), caller.clientId(), accountId, grantCreatedAt);
        return registry.register(link).consentId();
    }
}
