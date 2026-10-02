package edu.wpi.citizens.openbanking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "openbanking")
public record OpenBankingProperties(
        @NotBlank String tokenServiceUrl,
        @NotNull @Valid Keycloak keycloak) {

    /** How this service reaches Keycloak's admin API to check which consents still exist. */
    public record Keycloak(
            @NotBlank String url,
            @NotBlank String realm,
            @NotBlank String clientId,
            @NotBlank String clientSecret) {
    }
}
