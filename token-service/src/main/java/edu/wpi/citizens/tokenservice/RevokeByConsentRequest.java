package edu.wpi.citizens.tokenservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeByConsentRequest(
        @NotBlank @Size(max = 64) String consentId,
        @NotBlank @Size(max = 32) String reason) {
}
