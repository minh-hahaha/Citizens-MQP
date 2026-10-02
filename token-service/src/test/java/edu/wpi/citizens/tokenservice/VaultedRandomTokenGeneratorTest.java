package edu.wpi.citizens.tokenservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;

class VaultedRandomTokenGeneratorTest {

    private final VaultedRandomTokenGenerator generator = new VaultedRandomTokenGenerator();

    @RepeatedTest(200)
    void generatesTwelveDigitsWithNoLeadingZero() {
        String value = generator.next();

        assertThat(value).matches("[1-9][0-9]{11}");
    }
}
