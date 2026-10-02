package edu.wpi.citizens.tokenservice;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Insert-only access to the token_audit table. */
@Repository
public class AuditRepository {

    public enum Event { ISSUE, DETOKENIZE, REVOKE, EXPIRE }

    public enum Outcome { ALLOWED, DENIED }

    private final JdbcClient jdbc;

    public AuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** tokenId may be null when the token is unknown. */
    public void insert(UUID tokenId, Event event, RequestContext context, Outcome outcome) {
        jdbc.sql("""
                        INSERT INTO token_audit (token_id, event_type, actor, request_id, outcome)
                        VALUES (:tokenId, :eventType, :actor, :requestId, :outcome)
                        """)
                .param("tokenId", tokenId, java.sql.Types.OTHER)
                .param("eventType", event.name())
                .param("actor", context.actor())
                .param("requestId", context.requestId())
                .param("outcome", outcome.name())
                .update();
    }
}
