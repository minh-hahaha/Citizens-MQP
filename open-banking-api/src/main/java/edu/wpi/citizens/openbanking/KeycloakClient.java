package edu.wpi.citizens.openbanking;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Asks Keycloak's admin API whether a customer's consent for an aggregator still exists. */
@Component
public class KeycloakClient {

    private record UserConsent(String clientId, Long createdDate) {
    }

    private static final String CLIENT_ID = "open-banking-api";

    private final RestClient restClient;
    private final String clientSecret;

    public KeycloakClient(RestClient.Builder builder,
                          @Value("${openbanking.keycloak-url}") String url,
                          @Value("${openbanking.keycloak-client-secret}") String clientSecret) {
        this.restClient = builder.baseUrl(url).build();
        this.clientSecret = clientSecret;
    }

    /** When the customer consented to the aggregator, or empty if there is no consent. */
    public Optional<Long> consentCreatedAt(String userId, String aggregatorClientId) {
        List<UserConsent> consents = restClient.get()
                .uri("/admin/realms/citizens/users/{userId}/consents", userId)
                .headers(headers -> headers.setBearerAuth(serviceToken()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return consents.stream()
                .filter(consent -> aggregatorClientId.equals(consent.clientId()))
                .map(UserConsent::createdDate)
                .findFirst();
    }

    /** Logs this service in to Keycloak with its own client ID and secret. */
    private String serviceToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", CLIENT_ID);
        form.add("client_secret", clientSecret);
        Map<String, Object> response = restClient.post()
                .uri("/realms/citizens/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return (String) response.get("access_token");
    }
}
