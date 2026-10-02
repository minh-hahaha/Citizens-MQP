package edu.wpi.citizens.paymentreceiver;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final TokenServiceClient tokenService;
    private final Ledger ledger;
    private final Clock clock;

    public PaymentService(TokenServiceClient tokenService, Ledger ledger, Clock clock) {
        this.tokenService = tokenService;
        this.ledger = ledger;
        this.clock = clock;
    }

    /** Resolves the token and posts the payment to the ledger, or rejects it. */
    public PaymentResult receive(PaymentRequest request) {
        UUID paymentId = UUID.randomUUID();
        Optional<UUID> accountRef = tokenService.detokenize(
                request.routingNumber(), request.accountIdentifier(), paymentId.toString());
        if (accountRef.isEmpty()) {
            log.info("payment rejected payment_id={}", paymentId);
            return PaymentResult.rejected(paymentId);
        }
        ledger.post(new LedgerEntry(paymentId, accountRef.get(), request.amount(), clock.instant()));
        log.info("payment posted payment_id={}", paymentId);
        return PaymentResult.posted(paymentId);
    }
}
