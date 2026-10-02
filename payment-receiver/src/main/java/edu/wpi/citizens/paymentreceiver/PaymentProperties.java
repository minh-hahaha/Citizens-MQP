package edu.wpi.citizens.paymentreceiver;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payments")
public record PaymentProperties(@NotBlank String tokenServiceUrl, @NotBlank String allowedOrigin) {
}
