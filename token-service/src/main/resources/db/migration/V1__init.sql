CREATE TYPE token_status AS ENUM ('ACTIVE', 'SUSPENDED', 'REVOKED', 'EXPIRED');

CREATE TABLE account_token (
    token_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_value     VARCHAR(17)  NOT NULL,
    routing_number  CHAR(9)      NOT NULL,
    account_ref     UUID         NOT NULL,
    client_id       VARCHAR(64)  NOT NULL,
    consent_id      VARCHAR(64)  NOT NULL,
    status          token_status NOT NULL DEFAULT 'ACTIVE',
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoke_reason   VARCHAR(32),
    last_used_at    TIMESTAMPTZ,
    CONSTRAINT uq_token_value UNIQUE (token_value)
);

CREATE UNIQUE INDEX uq_active_token_per_link
    ON account_token (account_ref, client_id, consent_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_token_consent ON account_token (consent_id);

CREATE TABLE token_audit (
    audit_id     BIGSERIAL PRIMARY KEY,
    token_id     UUID,
    event_type   VARCHAR(24) NOT NULL,
    actor        VARCHAR(64) NOT NULL,
    request_id   VARCHAR(64) NOT NULL,
    outcome      VARCHAR(16) NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
