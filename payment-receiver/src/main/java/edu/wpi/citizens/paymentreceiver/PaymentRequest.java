package edu.wpi.citizens.paymentreceiver;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** An incoming payment addressed the way ACH addresses it: routing number plus account identifier. */
public record PaymentRequest(
        @NotNull @Pattern(regexp = "\\d{9}") String routingNumber,
        @NotNull @Pattern(regexp = "\\d{4,17}") String accountIdentifier,
        @NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal amount) {
}
