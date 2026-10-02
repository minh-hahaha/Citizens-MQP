# Citizens Tokenization Prototype

WPI MQP prototype, sponsored by Citizens Financial Group. It shows a data aggregator receiving a revocable **token** instead of a bank account number.

Everything here is fake test data. No real Citizens data, account numbers or credentials are used. This is a prototype, not production code.

## The flow

1. The customer logs in at the bank and consents to share an account with the aggregator.
2. The aggregator lists the customer's accounts through the Open Banking API.
3. The aggregator asks for the account's payment details and gets a token, not the account number.
4. The aggregator sends a payment to routing number + token. The bank resolves the token and posts it. **Payment succeeds.**
5. The customer revokes consent at the bank. The token is revoked.
6. The aggregator sends the same payment again. **Payment fails.**

## Run it

You need Docker. Then, from the repo root:

```bash
docker compose -f infra/docker-compose.yml up --build -d
```

The first build takes a few minutes. When it is up, open **http://localhost:5173** and follow the six steps on the page.

- Fake customers: `alice`, `bob`, `carol`. Password: `password`.
- For step 5, the page links to the bank's own "Applications" page. Open **Mock Aggregator** and choose **Remove access**.
- To run it again, choose **Start over**. Linking again gives a new token, and the old one stays dead.

To stop everything: `docker compose -f infra/docker-compose.yml down`.

## Check that it works

```bash
e2e/flow.sh
```

This script runs the whole flow against the running stack with `curl` (it needs `jq` and `openssl`) and prints one line per check. It logs in and consents at Keycloak, gets a token, pays, revokes consent, and confirms that the payment then fails. It also prints how long revocation took.

Unit and integration tests:

```bash
mvn verify                               # Java services (needs Docker for the database tests)
cd aggregator-ui && npm ci && npm test   # UI
```

## What is in the repo

| Folder | What it is | Citizens tool it stands in for |
|---|---|---|
| `token-service` | Creates, resolves and revokes tokens. The only service that uses PostgreSQL. | |
| `open-banking-api` | The two FDX endpoints, and the job that turns consent revocation into token revocation. | |
| `gateway` | Checks the access token and FDX scope, then forwards to the Open Banking API. | IBM API Connect |
| `payment-receiver` | Mock of the bank's side of an ACH payment, with a fake in-memory ledger. | |
| `aggregator-ui` | The mock aggregator the demo is driven from (React). | |
| `infra` | `docker-compose.yml` and the Keycloak realm (bank login and consent). | Ping Identity, OpenShift |
| `e2e` | `flow.sh`, the end-to-end check, and `login.sh`, which logs in with `curl`. | |
| `docs/fdx` | The FDX v6.4.1 OpenAPI files and the team's notes. | |

Ports on your machine:

| Port | Service |
|---|---|
| 5173 | Aggregator UI |
| 8080 | Keycloak (admin console login: `admin` / `dev-only-admin-password`) |
| 8081 | Gateway, the only way to reach the Open Banking API |
| 8084 | Payment Receiver |
| 5433 | PostgreSQL |

The Token Service and the Open Banking API have no port on your machine. They are reachable only from the other containers.

## The FDX part

The Open Banking API follows FDX API v6.4.1 for the two endpoints it has. It is FDX-aligned, not FDX-certified.

- `GET /fdx/v6/accounts` needs scope `fdx:accountbasic:read` and returns the `Accounts` schema.
- `GET /fdx/v6/accounts/{accountId}/payment-networks` needs scope `fdx:paymentsupport:read` and returns `AccountPaymentNetworkList`:

```json
{
  "page": { "totalElements": 1 },
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

- `x-fapi-interaction-id` (a UUID) is required on every request and echoed on every response.
- Errors use the FDX `Error` body with a string `code`: `701` account not found, `602` not authorized, `603` authentication failed, `401` invalid input, `500` internal error.

## How tokens behave

- A token is 12 random digits with no leading zero. The routing number is the fake `123456780`.
- One active token per account, aggregator and consent. Asking again returns the same token.
- Resolving a token fails the same way whether the token is unknown, revoked, expired, or sent with the wrong routing number. The caller cannot tell which.
- Every attempt to resolve a token is written to an append-only audit table.
- Logs show a token's ID and last four digits only.

## Known limitations

- **Revocation is not instant.** A job checks Keycloak every 5 seconds (`RECONCILE_INTERVAL_MS`). In test runs the payment started failing 1 to 4 seconds after consent was removed. FDX expects immediate revocation, so a real build needs the identity provider to push the event.
- **No login between the internal services.** The Token Service trusts any caller on the Compose network, and the caller name in the audit table is self-declared.
- **The Open Banking API keeps consent links in memory.** If it restarts, it forgets them. The next call then issues a new token, and the old token stays active until it expires.
- **The Payment Receiver is a mock.** It is not connected to any payment network and accepts payments from anyone.
- **Consent in Keycloak is per aggregator, not per account.** Removing access revokes the tokens for all of that customer's accounts with that aggregator.
- **The real FDX consent flow is not implemented.** FDX uses PAR plus RAR with an `fdxConsentId` claim in the access token. Here the Open Banking API makes up its own consent IDs.
- **Not built:** gateway rate limiting, Jenkins and SonarQube, the FF1 benchmark, architecture decision records, Datadog, Vault, OpenShift deployment.

## Changing the Keycloak realm

`infra/keycloak/citizens-realm.json` is a Keycloak realm export with the fake users added. To change it, edit the realm in the admin console at http://localhost:8080, export it again, and replace the file. Keycloak reads the file only when it starts with an empty database, so recreate the container afterwards:

```bash
docker compose -f infra/docker-compose.yml up -d --force-recreate keycloak
```
