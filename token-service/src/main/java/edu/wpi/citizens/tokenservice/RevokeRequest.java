package edu.wpi.citizens.tokenservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeRequest(@NotBlank @Size(max = 32) String reason) {
}
