package edu.wpi.citizens.tokenservice;

import java.util.UUID;

/** The thing a token is bound to: one account, shared with one client, under one consent. */
public record TokenLink(UUID accountRef, String clientId, String consentId) {
}
