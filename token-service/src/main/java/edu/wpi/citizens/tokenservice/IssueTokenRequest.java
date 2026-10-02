package edu.wpi.citizens.tokenservice;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IssueTokenRequest(
        @NotNull UUID accountRef,
        @NotBlank @Size(max = 64) String clientId,
        @NotBlank @Size(max = 64) String consentId) {

    public TokenLink toLink() {
        return new TokenLink(accountRef, clientId, consentId);
    }
}
