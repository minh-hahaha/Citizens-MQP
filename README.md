# Citizens Tokenization Prototype

WPI MQP prototype, sponsored by Citizens Financial Group. A data aggregator asks the bank for a customer's account number and gets a revocable **token** instead. Payments sent to the token work until the customer revokes consent.

Everything here is fake test data. This is a prototype, not production code.

## Run it

You only need Docker. From the repo root:

```bash
docker compose up --build -d
```

The first build takes a few minutes. Then open **http://localhost:5173** and follow the six steps on the page:

1. **Connect your bank.** Log in as `alice` (password: `password`) and choose **Yes** on the consent page.
2. **Pick an account.**
3. **Get the account number.** The page shows a 12-digit token, not the real account number.
4. **Send a test payment.** The bank resolves the token and posts the payment.
5. **Revoke consent.** The page links to the bank's "Applications" page. Open **Mock Aggregator** and choose **Remove access**.
6. **Send the same payment again.** Wait about 5 seconds first. The bank now rejects it.

The request log at the bottom of the page lists every call the aggregator made.

To go again, choose **Start over**. A new consent gets a new token. The old one stays dead.

Useful commands:

```bash
# Look inside the token vault
docker compose exec postgres psql -U vault -c 'select * from account_token'

# The fake ledger: every payment the bank posted
curl http://localhost:8084/v1/ledger

docker compose logs -f open-banking-api   # watch the revocation happen
docker compose down                       # stop everything and wipe the data
```

If you pulled this change while the old version was running, run `docker compose down --remove-orphans` once before starting.

## How it works

```
browser (aggregator-ui) ──login + consent──▶ Keycloak
   │        │
   │        │ GET /fdx/v6/accounts/{id}/payment-networks  (with the access token)
   │        ▼
   │     gateway ──▶ open-banking-api ──▶ token-service ──▶ PostgreSQL
   │                       │                    ▲
   │                       │                    │ detokenize
   │ POST /v1/payments     │                    │
   └───────────────────────┼──────▶ payment-receiver ──▶ fake ledger
                           │
                           └── every 5 s: "is the consent still there?" ──▶ Keycloak
```

1. The UI sends the customer to Keycloak. They log in and consent. Keycloak gives the UI an access token (a JWT).
2. The UI calls the Open Banking API through the gateway, with the access token.
3. The Open Banking API checks the token, finds the customer's account, asks Keycloak when the customer consented (that becomes the `consentId`), and asks the Token Service for a token for `{account, aggregator, consentId}`.
4. The Token Service returns the existing active token for those three things, or stores a new random 12-digit one.
5. The UI sends a payment, addressed to the routing number and the token, to the Payment Receiver. It asks the Token Service which account the token stands for. If the token is active, the payment goes on the fake ledger. If not, it is rejected.
6. Every 5 seconds the Open Banking API asks Keycloak whether each consent still exists. If one is gone, it tells the Token Service to revoke that consent's tokens. From then on step 5 is rejected.

## Where to read the code

About 630 lines of Java and 430 of TypeScript. Read in this order:

| File | What it does |
|---|---|
| `docker-compose.yml` | The seven containers and how they connect. |
| `aggregator-ui/src/App.tsx` | The page with the six steps. `auth.ts` is the Keycloak login, `api.ts` the three calls it makes. |
| `gateway/src/main/resources/application.yml` | The whole gateway: forward `/fdx/v6/**` to the Open Banking API. |
| `open-banking-api/.../AccountsController.java` | The two FDX endpoints. **Start here for the Java.** |
| `open-banking-api/.../ConsentService.java` | Builds the `consentId` and runs the revocation check. |
| `open-banking-api/.../KeycloakClient.java` | Asks Keycloak whether a consent exists. |
| `open-banking-api/.../TokenServiceClient.java` | Calls the Token Service. |
| `open-banking-api/.../SecurityConfig.java` | Requires a valid access token with the right FDX scope, and echoes `x-fapi-interaction-id`. |
| `open-banking-api/.../Fdx.java`, `FakeAccount.java` | The FDX response shapes and the seeded fake accounts. |
| `token-service/.../TokenController.java` | The whole Token Service: issue, detokenize, revoke. |
| `token-service/.../db/migration/V1__init.sql` | The one table in the vault. |
| `payment-receiver/.../PaymentController.java` | The whole Payment Receiver: resolve the token, post to the fake ledger or reject. |
| `infra/keycloak/citizens-realm.json` | Keycloak setup: the customer alice, the clients, the FDX scopes. Only what differs from Keycloak's defaults, plus the few built-in pieces the flow needs. |

Ports on your machine: UI 5173, Keycloak 8080 (admin console login `admin` / `dev-only-admin-password`), gateway 8081, Token Service 8083, Payment Receiver 8084, PostgreSQL 5433.

## The FDX part

The Open Banking API follows FDX API v6.4.1 for its two endpoints. It is FDX-aligned, not FDX-certified.

- `GET /fdx/v6/accounts` needs scope `fdx:accountbasic:read`.
- `GET /fdx/v6/accounts/{accountId}/payment-networks` needs scope `fdx:paymentsupport:read` and returns:

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

- `x-fapi-interaction-id` is echoed on every response.
- Errors use the FDX `Error` body: `701` account not found, `602` not authorized, `603` authentication failed.

## Known limitations

This version is deliberately small so it is quick to read. It is not robust.

- **No tests.**
- **The payment is a mock.** The Payment Receiver proves the resolve flow. It does not touch a real ACH path, and its ledger is in memory.
- **Revocation is not instant.** The check runs every 5 seconds. FDX expects immediate revocation, so a real build needs the identity provider to push the event.
- **Only the Open Banking API checks the access token.** The gateway just forwards. The Token Service and the Payment Receiver trust any caller.
- **The Open Banking API remembers consents in memory.** If it restarts before a consent is revoked, that consent's tokens are never revoked.
- **Two requests at the same instant for a brand-new link can fail once.** The database allows one active token per link, and the loser is not retried.
- **No audit trail, no token expiry.** Earlier commits on this branch had both, plus the tests.
- **Consent in Keycloak is per aggregator, not per account.** Removing access revokes the tokens for all of that customer's accounts with that aggregator.
- **The real FDX consent flow (PAR plus RAR with an `fdxConsentId` claim) is not implemented.**
