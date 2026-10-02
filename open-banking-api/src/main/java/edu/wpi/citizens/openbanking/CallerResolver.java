package edu.wpi.citizens.openbanking;

import org.springframework.stereotype.Component;

/** Works out who is calling. For now that is the configured demo caller. */
@Component
public class CallerResolver {

    private final Caller demoCaller;

    public CallerResolver(OpenBankingProperties properties) {
        this.demoCaller = properties.demoCaller();
    }

    public Caller currentCaller() {
        return demoCaller;
    }
}
