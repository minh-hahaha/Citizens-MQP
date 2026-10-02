package edu.wpi.citizens.openbanking;

import java.util.UUID;

/**
 * A fake bank account. {@code accountNumber} stands in for the real number the bank holds.
 * It must never leave this service and must never be logged.
 */
public record FakeAccount(
        String accountId,
        UUID accountRef,
        String ownerUsername,
        String accountNumber,
        String accountNumberDisplay,
        String accountType,
        String nickname) {
}
