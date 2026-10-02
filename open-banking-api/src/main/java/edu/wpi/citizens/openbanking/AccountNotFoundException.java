package edu.wpi.citizens.openbanking;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String accountId) {
        super("No account " + accountId + " for this customer");
    }
}
