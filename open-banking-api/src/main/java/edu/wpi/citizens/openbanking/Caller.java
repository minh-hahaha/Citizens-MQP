package edu.wpi.citizens.openbanking;

/** Who is calling: the bank customer who consented, and the aggregator acting for them. */
public record Caller(String username, String clientId) {
}
