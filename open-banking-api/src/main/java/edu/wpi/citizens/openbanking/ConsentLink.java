package edu.wpi.citizens.openbanking;

/**
 * One account a customer has shared with one aggregator, under one consent.
 * {@code grantCreatedAt} is when Keycloak recorded the customer's grant. If the customer
 * revokes and grants again, Keycloak records a new time, so a change means a new grant.
 */
public record ConsentLink(
        String consentId,
        String userId,
        String username,
        String clientId,
        String accountId,
        long grantCreatedAt) {
}
