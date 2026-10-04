package edu.wpi.citizens.openbanking;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls the Token Service. This is the only way this service gets a token. */
@Component
public class TokenServiceClient {

    public record IssuedToken(String tokenValue, String routingNumber) {
    }

    private final RestClient restClient;

    public TokenServiceClient(RestClient.Builder builder, @Value("${openbanking.token-service-url}") String url) {
        this.restClient = builder.baseUrl(url).build();
    }

    public IssuedToken issue(UUID accountRef, String clientId, String consentId) {
        return restClient.post()
                .uri("/v1/tokens")
                .body(Map.of("accountRef", accountRef, "clientId", clientId, "consentId", consentId))
                .retrieve()
                .body(IssuedToken.class);
    }

    public void revokeByConsent(String consentId) {
        restClient.post()
                .uri("/v1/tokens/revoke-by-consent")
                .body(Map.of("consentId", consentId))
                .retrieve()
                .toBodilessEntity();
    }
}
