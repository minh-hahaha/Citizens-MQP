package edu.wpi.citizens.paymentreceiver;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One credit posted to an account in the fake ledger. */
public record LedgerEntry(UUID paymentId, UUID accountRef, BigDecimal amount, Instant postedAt) {
}
