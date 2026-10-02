package edu.wpi.citizens.tokenservice;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tokens")
public record TokenProperties(
        @Pattern(regexp = "\\d{9}") String routingNumber,
        @Min(1) int ttlDays) {
}
