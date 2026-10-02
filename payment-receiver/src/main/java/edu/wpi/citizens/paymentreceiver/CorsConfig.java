package edu.wpi.citizens.paymentreceiver;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets the aggregator UI in the browser send test payments and read the ledger. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final PaymentProperties properties;

    public CorsConfig(PaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
                .allowedOrigins(properties.allowedOrigin())
                .allowedMethods("GET", "POST");
    }
}
