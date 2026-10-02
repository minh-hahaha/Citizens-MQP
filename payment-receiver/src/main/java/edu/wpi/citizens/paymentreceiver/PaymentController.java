package edu.wpi.citizens.paymentreceiver;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class PaymentController {

    private final PaymentService payments;
    private final Ledger ledger;

    public PaymentController(PaymentService payments, Ledger ledger) {
        this.payments = payments;
        this.ledger = ledger;
    }

    @PostMapping("/payments")
    public ResponseEntity<PaymentResult> receive(@Valid @RequestBody PaymentRequest request) {
        PaymentResult result = payments.receive(request);
        HttpStatus status = result.isPosted() ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/ledger")
    public List<LedgerEntry> ledger() {
        return ledger.entries();
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidRequest(Exception e) {
        return Map.of("error", "Invalid payment request");
    }
}
