package edu.wpi.citizens.openbanking;

import java.util.List;
import java.util.UUID;

/**
 * A fake bank account. accountNumber stands in for the real number the bank holds and
 * never leaves this service. accountRef is the internal ID the token vault stores.
 */
public record FakeAccount(String accountId, UUID accountRef, String ownerUsername, String accountNumber,
                          String accountNumberDisplay, String accountType, String nickname) {

    /** Seeded, made-up test data. */
    private static final List<FakeAccount> ALL = List.of(
            new FakeAccount("acc-1001", UUID.fromString("0b9d6c1e-1a0f-4c1b-9f6e-000000001001"),
                    "alice", "000111224321", "****4321", "CHECKING", "Everyday Checking"),
            new FakeAccount("acc-1002", UUID.fromString("0b9d6c1e-1a0f-4c1b-9f6e-000000001002"),
                    "alice", "000111228765", "****8765", "SAVINGS", "Alice's Day Savings"));


    public static List<FakeAccount> ownedBy(String username) {
        return ALL.stream()
                .filter(account -> account.ownerUsername().equals(username))
                .toList();
    }
}
