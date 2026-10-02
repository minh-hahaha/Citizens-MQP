package edu.wpi.citizens.tokenservice;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TokenRepository {

    private static final RowMapper<AccountToken> ROW_MAPPER = TokenRepository::mapRow;

    private final JdbcClient jdbc;

    public TokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Throws DuplicateKeyException if the token value or the active link already exists. */
    public AccountToken insert(String tokenValue, String routingNumber, TokenLink link, Instant expiresAt) {
        return jdbc.sql("""
                        INSERT INTO account_token
                            (token_value, routing_number, account_ref, client_id, consent_id, expires_at)
                        VALUES (:tokenValue, :routingNumber, :accountRef, :clientId, :consentId, :expiresAt)
                        RETURNING *
                        """)
                .param("tokenValue", tokenValue)
                .param("routingNumber", routingNumber)
                .param("accountRef", link.accountRef())
                .param("clientId", link.clientId())
                .param("consentId", link.consentId())
                .param("expiresAt", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                .query(ROW_MAPPER)
                .single();
    }

    public Optional<AccountToken> findActive(TokenLink link) {
        return jdbc.sql("""
                        SELECT * FROM account_token
                        WHERE account_ref = :accountRef
                          AND client_id = :clientId
                          AND consent_id = :consentId
                          AND status = 'ACTIVE'
                        """)
                .param("accountRef", link.accountRef())
                .param("clientId", link.clientId())
                .param("consentId", link.consentId())
                .query(ROW_MAPPER)
                .optional();
    }

    public Optional<AccountToken> findByValue(String tokenValue) {
        return jdbc.sql("SELECT * FROM account_token WHERE token_value = :tokenValue")
                .param("tokenValue", tokenValue)
                .query(ROW_MAPPER)
                .optional();
    }

    public Optional<AccountToken> findById(UUID tokenId) {
        return jdbc.sql("SELECT * FROM account_token WHERE token_id = :tokenId")
                .param("tokenId", tokenId)
                .query(ROW_MAPPER)
                .optional();
    }

    /** Revokes the token if it is not already revoked. Returns true if this call changed it. */
    public boolean revoke(UUID tokenId, String reason, Instant revokedAt) {
        return jdbc.sql("""
                        UPDATE account_token
                        SET status = 'REVOKED', revoked_at = :revokedAt, revoke_reason = :reason
                        WHERE token_id = :tokenId AND status <> 'REVOKED'
                        """)
                .param("revokedAt", OffsetDateTime.ofInstant(revokedAt, ZoneOffset.UTC))
                .param("reason", reason)
                .param("tokenId", tokenId)
                .update() > 0;
    }

    /** Revokes every token under a consent. Returns the IDs of the tokens this call changed. */
    public List<UUID> revokeByConsent(String consentId, String reason, Instant revokedAt) {
        return jdbc.sql("""
                        UPDATE account_token
                        SET status = 'REVOKED', revoked_at = :revokedAt, revoke_reason = :reason
                        WHERE consent_id = :consentId AND status <> 'REVOKED'
                        RETURNING token_id
                        """)
                .param("revokedAt", OffsetDateTime.ofInstant(revokedAt, ZoneOffset.UTC))
                .param("reason", reason)
                .param("consentId", consentId)
                .query(UUID.class)
                .list();
    }

    /** Flips ACTIVE tokens past their expiry to EXPIRED. Returns the IDs that were flipped. */
    public List<UUID> expireDue(Instant now) {
        return jdbc.sql("""
                        UPDATE account_token
                        SET status = 'EXPIRED'
                        WHERE status = 'ACTIVE' AND expires_at <= :now
                        RETURNING token_id
                        """)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .query(UUID.class)
                .list();
    }

    public void markUsed(UUID tokenId, Instant usedAt) {
        jdbc.sql("UPDATE account_token SET last_used_at = :usedAt WHERE token_id = :tokenId")
                .param("usedAt", OffsetDateTime.ofInstant(usedAt, ZoneOffset.UTC))
                .param("tokenId", tokenId)
                .update();
    }

    private static AccountToken mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AccountToken(
                rs.getObject("token_id", UUID.class),
                rs.getString("token_value"),
                rs.getString("routing_number"),
                rs.getObject("account_ref", UUID.class),
                rs.getString("client_id"),
                rs.getString("consent_id"),
                TokenStatus.valueOf(rs.getString("status")),
                instant(rs, "issued_at"),
                instant(rs, "expires_at"),
                instant(rs, "revoked_at"),
                rs.getString("revoke_reason"),
                instant(rs, "last_used_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
