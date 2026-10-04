package edu.wpi.citizens.openbanking;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Ties tokens to the customer's consent in Keycloak. A consentId names one consent the
 * customer gave to one aggregator. If the customer revokes it and consents again later,
 * that is a new consent with a new consentId, so it gets new tokens.
 */
@Service
public class ConsentService {

    private record Consent(String userId, String aggregatorClientId) {
    }

    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);

    /** The consents tokens were issued under, by consentId. In memory, so lost on restart. */
    private final Map<String, Consent> consentsWithTokens = new ConcurrentHashMap<>();
    private final KeycloakClient keycloak;
    private final TokenServiceClient tokenService;

    public ConsentService(KeycloakClient keycloak, TokenServiceClient tokenService) {
        this.keycloak = keycloak;
        this.tokenService = tokenService;
    }

    /** The consentId to issue a token under. Refuses if the customer's consent is gone. */
    public String consentIdFor(String userId, String aggregatorClientId) {
        String consentId = currentConsentId(userId, aggregatorClientId)
                .orElseThrow(Fdx.ErrorException::notAuthorized);
        consentsWithTokens.put(consentId, new Consent(userId, aggregatorClientId));
        return consentId;
    }

    /**
     * Turns consent revocation at the bank into token revocation. Every few seconds it
     * checks each consent against Keycloak and revokes the tokens of the ones that are gone.
     */
    @Scheduled(fixedDelayString = "${openbanking.reconcile-interval-ms}")
    public void revokeTokensOfRevokedConsents() {
        consentsWithTokens.forEach((consentId, consent) -> {
            boolean stillConsented = currentConsentId(consent.userId(), consent.aggregatorClientId())
                    .equals(Optional.of(consentId));
            if (!stillConsented) {
                tokenService.revokeByConsent(consentId);
                consentsWithTokens.remove(consentId);
                log.info("consent revoked in Keycloak, tokens revoked consent_id={}", consentId);
            }
        });
    }

    /** Keycloak has no consent ID of its own, so one is built from who consented and when. */
    private Optional<String> currentConsentId(String userId, String aggregatorClientId) {
        return keycloak.consentCreatedAt(userId, aggregatorClientId)
                .map(createdAt -> userId + "-" + createdAt);
    }
}
