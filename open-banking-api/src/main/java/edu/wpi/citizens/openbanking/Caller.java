package edu.wpi.citizens.openbanking;

import jakarta.validation.constraints.NotBlank;

/** Who is calling: the bank customer who consented, and the aggregator acting for them. */
public record Caller(@NotBlank String username, @NotBlank String clientId) {
}
