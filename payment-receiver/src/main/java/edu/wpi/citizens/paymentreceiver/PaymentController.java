package edu.wpi.citizens.paymentreceiver;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The whole Payment Receiver: a mock of the bank's ACH side. It takes a payment addressed
 * to a routing number and a token, asks the Token Service which account the token stands
 * for, and posts the payment to a fake ledger or rejects it.
 */
@RestController
@RequestMapping("/v1")
// Lets the aggregator UI, which runs in the browser, send test payments.
@CrossOrigin(origins = "http://localhost:5173")
public class PaymentController {

    /** Addressed the way ACH addresses a payment: routing number plus account identifier. */
    record PaymentRequest(String routingNumber, String accountIdentifier, BigDecimal amount) {
    }

    record PaymentResult(UUID paymentId, String status, String reason) {
    }

    record LedgerEntry(UUID paymentId, UUID accountRef, BigDecimal amount, Instant postedAt) {
    }

    record DetokenizeRequest(String routingNumber, String tokenValue) {
    }

    record Detokenized(UUID accountRef) {
    }

    /** Fake in-memory ledger. Entries are lost on restart. */
    private final List<LedgerEntry> ledger = new CopyOnWriteArrayList<>();
    private final RestClient tokenService;

    public PaymentController(RestClient.Builder builder, @Value("${payments.token-service-url}") String url) {
        this.tokenService = builder.baseUrl(url).build();
    }

    /** Resolves the token and posts the payment to the ledger, or rejects it. */
    @PostMapping("/payments")
    public ResponseEntity<PaymentResult> receive(@RequestBody PaymentRequest request) {
        UUID paymentId = UUID.randomUUID();
        Optional<UUID> accountRef = detokenize(request.routingNumber(), request.accountIdentifier());
        if (accountRef.isEmpty()) {
            // One reason for every rejection, so it does not reveal why the token was refused.
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new PaymentResult(paymentId, "REJECTED", "Unable to locate account"));
        }
        ledger.add(new LedgerEntry(paymentId, accountRef.get(), request.amount(), Instant.now()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new PaymentResult(paymentId, "POSTED", null));
    }

    @GetMapping("/ledger")
    public List<LedgerEntry> ledger() {
        return List.copyOf(ledger);
    }

    /** Fails closed: a refusal or any error from the Token Service means "not resolved". */
    private Optional<UUID> detokenize(String routingNumber, String tokenValue) {
        try {
            Detokenized detokenized = tokenService.post()
                    .uri("/v1/tokens/detokenize")
                    .body(new DetokenizeRequest(routingNumber, tokenValue))
                    .retrieve()
                    .body(Detokenized.class);
            return Optional.ofNullable(detokenized).map(Detokenized::accountRef);
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }
}
