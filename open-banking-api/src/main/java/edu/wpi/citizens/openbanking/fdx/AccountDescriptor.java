package edu.wpi.citizens.openbanking.fdx;

/**
 * FDX AccountDescriptor. accountId is a persistent ID, never the account number.
 * The deprecated accountNumber field is deliberately absent.
 */
public record AccountDescriptor(
        String accountCategory,
        String accountId,
        String accountType,
        String accountNumberDisplay,
        String nickname,
        String status) {
}
