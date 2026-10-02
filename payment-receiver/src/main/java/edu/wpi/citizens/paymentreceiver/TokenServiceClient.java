package edu.wpi.citizens.paymentreceiver;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Asks the Token Service which account a token stands for. */
@Component
public class TokenServiceClient {

    private static final String ACTOR = "payment-receiver";

    private static final Logger log = LoggerFactory.getLogger(TokenServiceClient.class);

    private record DetokenizeRequest(String routingNumber, String tokenValue) {
    }

    private record DetokenizeResponse(UUID accountRef) {
    }

    private final RestClient restClient;

    public TokenServiceClient(RestClient.Builder builder, PaymentProperties properties) {
        this.restClient = builder.baseUrl(properties.tokenServiceUrl()).build();
    }

    /** Fails closed: any denial or error from the Token Service means "not resolved". */
    public Optional<UUID> detokenize(String routingNumber, String tokenValue, String requestId) {
        try {
            DetokenizeResponse response = restClient.post()
                    .uri("/v1/tokens/detokenize")
                    .header("x-actor", ACTOR)
                    .header("x-fapi-interaction-id", requestId)
                    .body(new DetokenizeRequest(routingNumber, tokenValue))
                    .retrieve()
                    .body(DetokenizeResponse.class);
            return Optional.ofNullable(response).map(DetokenizeResponse::accountRef);
        } catch (RestClientException e) {
            log.info("token not resolved request_id={} cause={}", requestId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
