-- The token vault. It maps a token to an internal account reference, never to an account number.
CREATE TABLE account_token (
    token_value     VARCHAR(17) PRIMARY KEY,
    routing_number  CHAR(9)     NOT NULL,
    account_ref     UUID        NOT NULL,
    client_id       VARCHAR(64) NOT NULL,
    consent_id      VARCHAR(64) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ
);

-- At most one active token per account, aggregator and consent.
CREATE UNIQUE INDEX uq_active_token_per_link
    ON account_token (account_ref, client_id, consent_id)
    WHERE status = 'ACTIVE';
