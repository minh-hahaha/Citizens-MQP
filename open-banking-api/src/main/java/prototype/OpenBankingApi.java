package prototype;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * The bank's API for aggregators. Spring checks the access token's signature before any
 * method here runs (see application.yml). The access token (a JWT from Keycloak) says who is calling.
 */
@SpringBootApplication
@RestController
public class OpenBankingApi {

    public record Account(String accountId, String nickname, String accountNumberDisplay) {
    }

    public record IssuedToken(String token) {
    }

    // Fake data. The aggregator only ever sees the masked number.
    private static final List<Account> ALICE_ACCOUNTS = List.of(
            new Account("acc-1001", "Everyday Checking", "****4321"),
            new Account("acc-1002", "Day Savings", "****8765"));

    private static final String ROUTING_NUMBER = "123456780";
    private static final String REALM = "prototype-app";
    private static final String KEYCLOAK_CLIENT_ID = "open-banking-api";

    private final String tokenServiceUrl;
    private final String keycloakUrl;
    private final String keycloakClientSecret;

    // The values come from environment variables set in docker-compose.yaml.
    public OpenBankingApi(@Value("${TOKEN_SERVICE_URL:http://localhost:8083}") String tokenServiceUrl,
                          @Value("${KEYCLOAK_URL:http://localhost:8080}") String keycloakUrl,
                          @Value("${KEYCLOAK_CLIENT_SECRET}") String keycloakClientSecret) {
        this.tokenServiceUrl = tokenServiceUrl;
        this.keycloakUrl = keycloakUrl;
        this.keycloakClientSecret = keycloakClientSecret;
    }

    public static void main(String[] args) {
        SpringApplication.run(OpenBankingApi.class, args);
    }

    /** The customer's accounts, with masked numbers only. */
    @GetMapping("/fdx/v6/accounts")
    public Map<String, Object> accounts(@AuthenticationPrincipal Jwt accessToken) {
        requireConsent(accessToken);
        return Map.of("accounts", ALICE_ACCOUNTS);
    }

    /** Returns a token in place of the account number. */
    @GetMapping("/fdx/v6/accounts/{accountId}/payment-networks")
    public Map<String, Object> paymentNetworks(@PathVariable String accountId,
                                               @AuthenticationPrincipal Jwt accessToken) {
        requireConsent(accessToken);
        // "sub" in the access token is the customer's ID at the bank.
        IssuedToken issued = RestClient.create(tokenServiceUrl).post()
                .uri("/tokens")
                .body(Map.of("accountId", accountId, "customerId", accessToken.getSubject()))
                .retrieve()
                .body(IssuedToken.class);
        return Map.of("paymentNetworks", List.of(Map.of(
                "bankId", ROUTING_NUMBER,
                "identifier", issued.token(),
                "identifierType", "TOKENIZED_ACCOUNT_NUMBER",
                "type", "US_ACH")));
    }

    /**
     * Refuses the request, and revokes the customer's tokens, if their consent is gone.
     * Needed because the access token keeps passing the signature check after the customer
     * removes access, until it expires.
     */
    private void requireConsent(Jwt accessToken) {
        RestClient keycloak = RestClient.create(keycloakUrl);

        // 1. Log this service in to Keycloak with its own ID and secret.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", KEYCLOAK_CLIENT_ID);
        form.add("client_secret", keycloakClientSecret);
        Map<String, Object> login = keycloak.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", REALM)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });

        // 2. Ask which apps the customer has consented to.
        List<Map<String, Object>> consents = keycloak.get()
                .uri("/admin/realms/{realm}/users/{id}/consents", REALM, accessToken.getSubject())
                .headers(headers -> headers.setBearerAuth((String) login.get("access_token")))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });

        // 3. "azp" in the access token is the aggregator that is calling.
        String aggregator = accessToken.getClaimAsString("azp");
        boolean hasConsent = consents.stream().anyMatch(consent -> aggregator.equals(consent.get("clientId")));
        if (hasConsent) {
            return;
        }
        RestClient.create(tokenServiceUrl).post()
                .uri("/tokens/revoke")
                .body(Map.of("customerId", accessToken.getSubject()))
                .retrieve()
                .toBodilessEntity();
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Consent revoked");
    }
}
