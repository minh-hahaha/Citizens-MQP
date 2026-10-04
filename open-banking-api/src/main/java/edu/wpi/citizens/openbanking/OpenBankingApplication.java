package edu.wpi.citizens.openbanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenBankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenBankingApplication.class, args);
    }
}
