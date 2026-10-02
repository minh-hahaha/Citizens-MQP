package edu.wpi.citizens.openbanking;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Turns consent revocation at the bank into token revocation. Every few seconds it asks
 * Keycloak whether each link's grant still exists and revokes the tokens of the ones that
 * are gone. The time between two runs is the revocation delay.
 */
@Component
public class ReconciliationJob {

    static final String REVOKE_REASON = "CONSENT_REVOKED";

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ConsentRegistry registry;
    private final KeycloakConsentClient keycloak;
    private final TokenServiceClient tokenService;

    public ReconciliationJob(ConsentRegistry registry, KeycloakConsentClient keycloak,
                             TokenServiceClient tokenService) {
        this.registry = registry;
        this.keycloak = keycloak;
        this.tokenService = tokenService;
    }

    @Scheduled(fixedDelayString = "${openbanking.reconcile-interval-ms}",
            initialDelayString = "${openbanking.reconcile-interval-ms}")
    public void run() {
        registry.all().forEach(this::reconcile);
    }

    /** A failure leaves the link in place so the next run tries again. */
    private void reconcile(ConsentLink link) {
        try {
            Optional<Long> grantCreatedAt = keycloak.grantCreatedAt(link.userId(), link.clientId());
            boolean sameGrant = grantCreatedAt.isPresent() && grantCreatedAt.get() == link.grantCreatedAt();
            if (sameGrant) {
                return;
            }
            tokenService.revokeByConsent(link.consentId(), REVOKE_REASON, UUID.randomUUID().toString());
            registry.remove(link);
            log.info("consent gone, tokens revoked consent_id={} client_id={}", link.consentId(), link.clientId());
        } catch (RuntimeException e) {
            log.warn("could not reconcile consent_id={}, will retry: {}", link.consentId(), e.toString());
        }
    }
}
