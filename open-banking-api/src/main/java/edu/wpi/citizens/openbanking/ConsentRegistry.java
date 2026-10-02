package edu.wpi.citizens.openbanking;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** In-memory record of the active consent links. Lost on restart. */
@Component
public class ConsentRegistry {

    private record LinkKey(String userId, String clientId, String accountId) {
    }

    private final Map<LinkKey, ConsentLink> links = new ConcurrentHashMap<>();

    public Optional<ConsentLink> find(String userId, String clientId, String accountId) {
        return Optional.ofNullable(links.get(new LinkKey(userId, clientId, accountId)));
    }

    /** Stores the link unless one already exists for the same key. Returns the stored link. */
    public ConsentLink register(ConsentLink link) {
        LinkKey key = new LinkKey(link.userId(), link.clientId(), link.accountId());
        ConsentLink existing = links.putIfAbsent(key, link);
        return existing != null ? existing : link;
    }

    public List<ConsentLink> all() {
        return List.copyOf(links.values());
    }

    public void remove(ConsentLink link) {
        links.remove(new LinkKey(link.userId(), link.clientId(), link.accountId()), link);
    }
}
