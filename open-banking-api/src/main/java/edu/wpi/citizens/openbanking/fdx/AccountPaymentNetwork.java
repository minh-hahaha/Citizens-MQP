package edu.wpi.citizens.openbanking.fdx;

/** FDX AccountPaymentNetwork. identifier carries the token, never the real account number. */
public record AccountPaymentNetwork(
        String bankId,
        String identifier,
        String identifierType,
        String type,
        boolean transferIn,
        boolean transferOut) {
}
