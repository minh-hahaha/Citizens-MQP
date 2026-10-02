package edu.wpi.citizens.openbanking;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls the Token Service. This is the only way this service gets a token. */
@Component
public class TokenServiceClient {

    public record IssueRequest(UUID accountRef, String clientId, String consentId) {
    }

    public record IssuedToken(UUID tokenId, String tokenValue, String routingNumber) {
    }

    private final RestClient restClient;

    public TokenServiceClient(RestClient.Builder builder, OpenBankingProperties properties) {
        this.restClient = builder.baseUrl(properties.tokenServiceUrl()).build();
    }

    public IssuedToken issue(IssueRequest request, String interactionId) {
        return restClient.post()
                .uri("/v1/tokens")
                .header(InteractionIdFilter.HEADER, interactionId)
                .body(request)
                .retrieve()
                .body(IssuedToken.class);
    }
}
