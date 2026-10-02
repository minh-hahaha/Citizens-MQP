package edu.wpi.citizens.openbanking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

/** Seeded, in-memory accounts. Everything here is made-up test data. */
@Repository
public class FakeAccountRepository {

    private static final List<FakeAccount> ACCOUNTS = List.of(
            new FakeAccount("acc-1001", UUID.fromString("0b9d6c1e-1a0f-4c1b-9f6e-000000001001"),
                    "alice", "000111224321", "****4321", "CHECKING", "Everyday Checking"),
            new FakeAccount("acc-1002", UUID.fromString("0b9d6c1e-1a0f-4c1b-9f6e-000000001002"),
                    "alice", "000111228765", "****8765", "SAVINGS", "Rainy Day Savings"),
            new FakeAccount("acc-2001", UUID.fromString("0b9d6c1e-1a0f-4c1b-9f6e-000000002001"),
                    "bob", "000222331234", "****1234", "CHECKING", "Bob's Checking"),
            new FakeAccount("acc-3001", UUID.fromString("0b9d6c1e-1a0f-4c1b-9f6e-000000003001"),
                    "carol", "000333445678", "****5678", "CHECKING", "Carol's Checking"));

    public List<FakeAccount> findByOwner(String username) {
        return ACCOUNTS.stream()
                .filter(account -> account.ownerUsername().equals(username))
                .toList();
    }

    public Optional<FakeAccount> findByIdAndOwner(String accountId, String username) {
        return findByOwner(username).stream()
                .filter(account -> account.accountId().equals(accountId))
                .findFirst();
    }
}
