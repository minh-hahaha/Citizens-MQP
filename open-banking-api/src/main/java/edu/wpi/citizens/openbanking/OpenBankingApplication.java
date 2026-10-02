package edu.wpi.citizens.openbanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OpenBankingProperties.class)
public class OpenBankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenBankingApplication.class, args);
    }
}
