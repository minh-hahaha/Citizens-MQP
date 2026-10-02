package edu.wpi.citizens.openbanking;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "openbanking")
public record OpenBankingProperties(@NotBlank String tokenServiceUrl) {
}
