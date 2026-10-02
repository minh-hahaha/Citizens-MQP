# Citizens Tokenization Prototype

## Project context

WPI MQP (Fintech PQP A26) sponsored by Citizens Financial Group. Citizens runs an Open Banking API platform that serves data aggregators (Plaid, Finicity, Yodlee). Today the API can return real account and routing numbers to an aggregator. This project replaces the account number with a revocable token as data flows to aggregators.

Citizens production stack: RESTful APIs, Java, React, OpenShift, Jenkins, SonarQube, PostgreSQL, IBM API Connect, Ping Identity, Datadog.

This repo is a throwaway-quality prototype, not production code. It has two goals:
1. Teach the team the Citizens stack.
2. Demo end to end what the team could build over the 7-week project.

Deadline: October 7, 2026. Today is October 2, 2026. Five days remain, so scope ruthlessly.

Never use real Citizens data, real account numbers, or real credentials. Everything is fake test data.

## What the prototype proves

A mock aggregator links a customer's bank account through OAuth consent. The Open Banking API returns a token instead of an account number. A mock payment receiver resolves the token to the real account and posts to a fake ledger. When the customer revokes consent, the token dies and payments to it fail.

## Architecture

Request flow:
1. Mock aggregator (React) redirects the customer to Keycloak (stand-in for Ping Identity). Keycloak handles login and consent and issues a JWT access token (authorization code flow with PKCE).
2. Aggregator calls the API gateway with that token. The gateway validates signature and scopes, rate limits, and routes to the Open Banking API.
3. Open Banking API (Java) calls the Token Service for a token bound to `{account_ref, client_id, consent_id}` and returns an FDX v6.4.1 `GET /accounts/{accountId}/payment-networks` response with the token in `identifier` and `identifierType: TOKENIZED_ACCOUNT_NUMBER`, instead of the real account number (see "FDX alignment" below).
4. Aggregator sends a mock payment (routing number + token) to the Payment Receiver. The receiver calls the Token Service's internal detokenize endpoint and posts to a fake ledger, or rejects.
5. On consent revocation in Keycloak, the Token Service revokes the matching tokens, and step 4 starts failing.

Components the team builds: `aggregator-ui`, `open-banking-api`, `token-service`, `payment-receiver`.

Stand-ins behind configuration so the real products can swap in later:

| Citizens tool | Prototype stand-in |
|---|---|
| Ping Identity | Keycloak (bank-side login and consent server, not part of the aggregator) |
| IBM API Connect | Spring Cloud Gateway |
| OpenShift | Docker Compose first, OpenShift deployment only if time allows |
| HSM/KMS | App-level key from environment config, HashiCorp Vault transit only if time allows |
| Jenkins, SonarQube, PostgreSQL | The real tools in containers |
| Datadog | Micrometer metrics plus Datadog agent only if time allows |

Only the Token Service touches PostgreSQL. No other service reads the token vault.

## Repo layout (monorepo)

```
/aggregator-ui        React app, mock Plaid-like client
/open-banking-api     Spring Boot, FDX-shaped endpoint
/token-service        Spring Boot, the core deliverable
/payment-receiver     Spring Boot, mock ACH resolver and fake ledger
/gateway              Spring Cloud Gateway
/infra                docker-compose.yml, Keycloak realm JSON, Jenkinsfile
/docs/adr             Architecture decision records
```

## Tech decisions already made

- Java 21, Maven, Spring Boot 3.5.x (not 4.x milestones).
- Token Service dependencies: Spring Web, Spring JDBC (not JPA, explicit SQL and predictable unique-constraint errors), PostgreSQL driver, Flyway, Validation, Actuator, Testcontainers.
- Package root: `edu.wpi.citizens`.
- PostgreSQL 16.
- Token approach: vaulted random tokens. 12-digit numeric, no leading zero, generated with `java.security.SecureRandom`, unique index, retry on collision up to 5 attempts. Rejected FF1 vaultless as the primary approach because per-link scoping and instant revocation need state anyway. If time allows, benchmark FF1 against it behind one interface and record the result in an ADR.
- The vault stores an internal surrogate `account_ref` (UUID), not raw account numbers. The Open Banking API owns a seeded table of fake accounts mapping `account_ref` to a fake real account number and mask.
- Fake test routing number for the prototype: `123456780` (passes the ABA checksum, not a real bank). Make it a config value.
- Keycloak realm is exported to JSON in `/infra` so `docker compose up` gives every teammate identical config. Seed two or three test customers, each mapped to a fake account.
- Revocation: build a scheduled reconciliation job first (compare active tokens against Keycloak's active grants through its admin API, revoke tokens with no grant). Add a webhook only if time allows.
- `consent_id`: Keycloak has no per-account consent record, so the Open Banking API generates a `consent_id` the first time it sees a new grant and stores it with the chosen account.

## Token Service schema (Flyway `V1__init.sql`)

```sql
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
```

Audit table rule: the application DB role gets INSERT only on `token_audit`, no UPDATE or DELETE.

## Token Service API (internal REST)

| Method and path | Caller | Purpose |
|---|---|---|
| `POST /v1/tokens` | Open Banking API | Issue or return the active token for `{accountRef, clientId, consentId}` (idempotent) |
| `POST /v1/tokens/detokenize` | Payment Receiver only | Resolve `{routing, tokenValue}` to `accountRef` if status is ACTIVE, not expired, routing matches |
| `POST /v1/tokens/{tokenId}/revoke` | Reconciliation job, tooling | Set REVOKED with a reason |
| `POST /v1/tokens/revoke-by-consent` | Reconciliation job | Revoke every token for a `consentId` |
| `GET /v1/tokens/{tokenId}` | Support tooling | Status and metadata only, never the real number |

Behavior rules the tests must enforce:
1. Idempotent issuance. Repeating the same request returns the existing active token. Handle concurrent issue for the same link by catching the unique violation and returning the winner.
2. Fail closed. Any detokenize error, unknown token, status problem, or routing mismatch returns a generic denial and writes a DENIED audit row.
3. No detail leakage. Responses never distinguish "unknown token" from "revoked" from "wrong client."
4. Expiry. A scheduled job flips ACTIVE tokens past `expires_at` to EXPIRED. Detokenize also checks `expires_at` directly.
5. Never log full token values or any real account number. Log `token_id` and at most the last four characters.

Detokenize must run in a single transaction and write its audit row whether it succeeds or fails.

## FDX alignment (FDX API v6.4.1, Spring 2025 release)

Source of truth: the FDX OpenAPI files. Copy `fdxapi.core.yaml` and `fdxapi.components.yaml` into `/docs/fdx/` and read them before writing DTOs. The team's summary notes live in `/docs/fdx/FDX_Open_Banking_API_Notes.md`. Where the notes and the YAML disagree, the YAML wins. Where a YAML example disagrees with its own schema (the release has several), the schema wins.

We are FDX-aligned, not FDX-certified. Implement only the subset below and do not claim CSDF conformance.

**Open Banking API endpoints to implement**

| Endpoint | Purpose in the prototype |
|---|---|
| `GET /fdx/v6/accounts` | Account list for the aggregator UI. `AccountDescriptor` with `accountId`, `accountCategory: DEPOSIT_ACCOUNT`, `accountType: CHECKING`, `accountNumberDisplay` (masked), `nickname`, `status: OPEN` |
| `GET /fdx/v6/accounts/{accountId}/payment-networks` | Returns the token. This is the core endpoint |

`accountId` is a persistent identifier, never the account number. Do not return the deprecated `AccountDescriptor.accountNumber` or `Account.routingTransitNumber`. FDX moved those to the payment-networks endpoint.

**`AccountPaymentNetwork` fields** (from the FDX schema)

- `bankId`: routing number. Use the fake `123456780`.
- `identifier`: the token from the Token Service. Never the real account number.
- `identifierType`: always `TOKENIZED_ACCOUNT_NUMBER` in this prototype.
- `type`: `US_ACH` only for the prototype.
- `transferIn`, `transferOut`: `true`.
- `supportsRequestForPayment`, `transferLimits`: optional, omit for now.

Target response (confirm the wrapper property name against `fdxapi.core.yaml` before coding, the notes do not state it):

```json
{
  "paymentNetworks": [
    {
      "bankId": "123456780",
      "identifier": "482910375526",
      "identifierType": "TOKENIZED_ACCOUNT_NUMBER",
      "type": "US_ACH",
      "transferIn": true,
      "transferOut": true
    }
  ]
}
```

**Conventions on every Open Banking API call**

- `Authorization: Bearer <token>` in the header only, never the URL.
- `x-fapi-interaction-id` (UUID) required on requests and echoed on every response, including errors. Pass it through to the Token Service and log it as the `request_id` in `token_audit`.
- Errors use the FDX `Error` body: `code`, `message`, optional `debugMessage`. Use `701` (HTTP 404) for account not found, `602` (HTTP 403) for not authorized, `603` (HTTP 401) for authentication failed, `500` for internal errors. Check the YAML for whether `code` is a string or an integer.
- Required scope for payment-networks: `fdx:paymentsupport:read`. Accounts list: `fdx:accountbasic:read`. Do not use the deprecated `fdx:accountpayments:read`.

**Keycloak settings that follow FDX**

- Create client scopes named exactly `fdx:accountbasic:read` and `fdx:paymentsupport:read`.
- Access token lifetime 900 seconds or less.
- FDX puts the consent ID in a private JWT claim, `fdxConsentId`. Keycloak cannot mint a per-grant consent ID out of the box, so the prototype keeps generating `consent_id` in the Open Banking API (see Tech decisions). Name everything `consentId` / `fdxConsentId` so the swap to a token claim is mechanical later.
- FDX's real consent flow uses PAR plus RAR (`authorization_details` type `fdx_v1.0`). Out of scope for the prototype. Note it in the README as the target.

**Revocation timing gap**

The FDX Security Model expects tokens revoked immediately when consent is revoked. The reconciliation job introduces a delay. Document the measured delay in the demo and the ADR, and treat a push-based revocation path as the production requirement.

**Optional if time allows**

Write an OpenAPI file for the Open Banking API and lint it with the FDX Spectral ruleset (`fdx.spectral.ruleset.yaml` in the FDX Tools folder). It checks operationId prefixes, casing, and `x-fapi-interaction-id` on every response, which makes cheap conformance evidence for the demo.

## Build order (commit after each step)

1. `infra/docker-compose.yml` with Postgres. `token-service` skeleton with Flyway migration running on startup.
2. `AccountToken` record and `TokenRepository` (`insert`, `findActive`, `findByValue`) using JdbcClient or JdbcTemplate.
3. `TokenService.issue` with retry loop and idempotency, then `POST /v1/tokens`.
4. Testcontainers test: 20 parallel issue calls for one link produce exactly one active token.
5. `detokenize`, `revoke`, `revoke-by-consent`, audit inserts, expiry job.
6. Jenkinsfile (build, test, SonarQube gate, image build). Quality gate fails on secrets and critical issues.
7. Keycloak in Compose with the realm JSON and seeded users.
8. Gateway, then Open Banking API with `GET /fdx/v6/accounts` and `GET /fdx/v6/accounts/{accountId}/payment-networks` returning the token (see "FDX alignment"), including `x-fapi-interaction-id` handling and FDX error bodies.
9. Payment Receiver with fake ledger, calling detokenize.
10. React aggregator UI: link account, show what the aggregator stores, send a test payment, show revoke result.
11. Reconciliation job for revocation, then the end-to-end test that runs the whole flow. This test doubles as the demo script.
12. If time remains: token approach benchmark (vaulted vs FF1 behind one interface, measure detokenize latency), then Datadog and Vault.

Cut order if time runs short: Vault key management (use an app-level key and document the gap), breach comparison view, OpenShift deployment (keep Compose). Never cut the end-to-end flow, the revocation path, or the benchmark.

## Plan to October 7

- Oct 2 to 3: steps 1 to 6.
- Oct 4: steps 7 to 8.
- Oct 5: steps 9 to 11.
- Oct 6: step 12 or polish, freeze code, rehearse the demo, finish the ADR and README.
- Oct 7: deliver.

## Conventions

- Small commits, one step each, working tree green before moving on.
- Constructor injection, Java records for DTOs and rows, no Lombok.
- No secrets in the repo. Dev-only passwords live in Compose or `.env.example` and are clearly labeled.
- Every endpoint has an integration test. Every fail-closed branch has a unit test.
- Write an ADR in `/docs/adr` for each real decision (token approach, revocation mechanism, consent_id source).

## Open questions for the sponsors (Jeff Collemer, Glenn Morin)

Do not block on these. Build with the assumptions above and adjust.
1. What internal account identifier does the Open Banking API use, and can the vault store it instead of the account number?
2. Does the ACH or payments platform have a hook where inbound routing + account entries can call an external resolver?
3. What are the account number format rules, and can we reserve a token namespace?
4. Does the routing number stay shared or also tokenize?
5. How does Ping Identity expose consent revocation to downstream services?
6. What are the latency and availability targets for anything on the payment path?
7. Which HSM or KMS product does Citizens approve, and what does sandbox access look like?
8. Which Java framework and libraries does Citizens prefer, and what is the policy on open-source dependencies and AI-assisted code?
9. Which FDX version does Citizens' Open Banking API implement (5.4, 6.4, or older), and does it already return `TOKENIZED_ACCOUNT_NUMBER` anywhere? Can we get the API docs and sandbox credentials?
10. Does Citizens run the FDX Security Model v5.0 Green profile (OAuth 2.0) or Blue profile (FAPI 2.0), and do any aggregator agreements require message encryption or step-up authentication?
11. Does Citizens' consent flow use PAR plus RAR with an `fdxConsentId` claim, and how does Ping Identity propagate revocation?

## Known limitations to document in the README

- The prototype cannot touch a live ACH path. The Payment Receiver is a mock that proves the resolve flow end to end, and the handoff note describes what Citizens' ACH team would integrate.
- Tokens would not work for wires or paper checks in a real deployment.
- Keycloak consent is per client and scope, not per account. The prototype adds account selection on top.
- This design follows standard industry practice. Citizens' internal token architecture is not public, and nothing here is confirmed against it.