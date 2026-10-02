package edu.wpi.citizens.paymentreceiver;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Mock of the bank's side of an ACH payment. It is not connected to any real payment
 * network. It exists to show that a payment addressed to a token can be resolved.
 */
@SpringBootApplication
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentReceiverApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentReceiverApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
