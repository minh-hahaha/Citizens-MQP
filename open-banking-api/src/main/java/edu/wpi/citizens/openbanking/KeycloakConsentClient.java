package edu.wpi.citizens.openbanking;

import java.util.List;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Asks Keycloak whether a customer's consent for an aggregator still exists.
 * Uses a read-only service account. A fresh service token is fetched for every call,
 * which is fine at prototype volume.
 */
@Component
public class KeycloakConsentClient {

    private record TokenResponse(String access_token) {
    }

    private record UserConsent(String clientId, Long createdDate) {
    }

    private final RestClient restClient;
    private final OpenBankingProperties.Keycloak keycloak;

    public KeycloakConsentClient(RestClient.Builder builder, OpenBankingProperties properties) {
        this.keycloak = properties.keycloak();
        this.restClient = builder.baseUrl(keycloak.url()).build();
    }

    /** Returns when the customer's grant to the client was created, or empty if there is none. */
    public Optional<Long> grantCreatedAt(String userId, String clientId) {
        List<UserConsent> consents = restClient.get()
                .uri("/admin/realms/{realm}/users/{userId}/consents", keycloak.realm(), userId)
                .headers(headers -> headers.setBearerAuth(serviceToken()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        if (consents == null) {
            return Optional.empty();
        }
        return consents.stream()
                .filter(consent -> clientId.equals(consent.clientId()))
                .map(UserConsent::createdDate)
                .findFirst();
    }

    private String serviceToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", keycloak.clientId());
        form.add("client_secret", keycloak.clientSecret());
        TokenResponse response = restClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", keycloak.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.access_token() == null) {
            throw new IllegalStateException("Keycloak returned no service token");
        }
        return response.access_token();
    }
}
