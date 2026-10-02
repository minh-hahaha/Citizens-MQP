package edu.wpi.citizens.openbanking;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Remembers the consentId for each (customer, aggregator, account) link.
 * A consentId is created the first time a link is seen. Kept in memory.
 */
@Component
public class ConsentRegistry {

    private record LinkKey(String username, String clientId, String accountId) {
    }

    private final Map<LinkKey, String> consentIds = new ConcurrentHashMap<>();

    public String consentIdFor(Caller caller, String accountId) {
        LinkKey key = new LinkKey(caller.username(), caller.clientId(), accountId);
        return consentIds.computeIfAbsent(key, k -> "consent-" + UUID.randomUUID());
    }
}
