package edu.wpi.citizens.tokenservice;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically marks tokens past their expiry as EXPIRED. */
@Component
public class ExpiryJob {

    private static final String ACTOR = "expiry-job";

    private static final Logger log = LoggerFactory.getLogger(ExpiryJob.class);

    private final TokenService tokenService;

    public ExpiryJob(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Scheduled(fixedDelayString = "${tokens.expiry-check-ms:60000}")
    public void run() {
        int expired = tokenService.expireDue(new RequestContext(ACTOR, UUID.randomUUID().toString()));
        if (expired > 0) {
            log.info("expired {} token(s)", expired);
        }
    }
}
