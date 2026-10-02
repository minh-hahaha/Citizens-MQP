package edu.wpi.citizens.tokenservice;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** Random 12-digit numeric token with no leading zero, so it looks like an account number. */
@Component
public class VaultedRandomTokenGenerator implements TokenGenerator {

    static final int TOKEN_LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String next() {
        StringBuilder value = new StringBuilder(TOKEN_LENGTH);
        value.append(1 + random.nextInt(9));
        for (int i = 1; i < TOKEN_LENGTH; i++) {
            value.append(random.nextInt(10));
        }
        return value.toString();
    }
}
