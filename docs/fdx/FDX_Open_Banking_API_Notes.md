# FDX Open Banking API Notes (Spring 2025 Release, API v6.4.1)

Source: every file in `FDX Spring Release 2025/` (all 15 OpenAPI YAML files, all 11 PDFs, the CSDF Reference List spreadsheet, and the Tools folder). Facts below come from those files. Lines marked **Inference** are my reading, not FDX text.

## 1. What the release contains

| Folder | File | What it is |
|---|---|---|
| API/FDX API v6.4.1 | `fdxapi.core.yaml` | Accounts, transactions, statements, payment networks, T&Cs, rewards (14 GET operations, 146 schemas) |
| | `fdxapi.components.yaml` | Shared schemas, headers, parameters, error responses, security schemes |
| | `fdxapi.consent.yaml` | Consent grant retrieval and revocation, JWT and introspection profiles |
| | `fdxapi.customer.yaml` | Customer and account-holder data |
| | `fdxapi.money-movement.yaml` | Payees, bill payments, recurring payments, internal transfers, payment initiation parties |
| | `fdxapi.meta.yaml` | Availability, capability, certification metrics |
| | `fdxapi.event-notifications.yaml` | Webhook subscriptions and notifications |
| | `fdxapi.registry.yaml`, `fdxapi.recipient-registration.yaml` | Ecosystem registry and dynamic client registration |
| | `fdxapi.fraud.yaml` | Suspected fraud reporting |
| | `fdxapi.payroll.yaml` | Paystubs, VOE / VOIE reports |
| | `fdxapi.tax.yaml`, `fdxapi.tax1065k3.yaml` | US tax forms (out of scope for us) |
| | `fdxapi.extensions.yaml` | Defines the CSDF tagging extensions only, not part of the API |
| | `fdxapi.template.yaml` | Template for authoring a new FDX-style API |
| | API Specification Reference v6.4.1 (PDF) | Change log, status codes, full error code table |
| | Consent API Behavioral Specification v1.0 (PDF) | How consent creation, retrieval, revocation work with OAuth |
| | CSDF Tagging Guide v1.0 (PDF) | How to read the `x-fdx-csdf-*` tags |
| | Tools/ | Spectral lint ruleset, yamllint config, build scripts |
| Consensus Standard Data Format | CSDF v1.0 (PDF), CSDF Reference List v6.4.1 (xlsx) | The CFPB 1033 conformance subset |
| Security | FDX CSDF API Security Model v5.0 (PDF) | Normative security profiles |
| User Experience | UX Guidelines v3.0, Data Minimization Guidelines v1.0 | Consent journeys, dashboards, data clusters |
| Tax | 4 PDFs | Tax-document auth, QR codes, PDF embedded data, server info form |

All API files share the placeholder base URL `https://api.fi.com/fdx/v6`. The registry uses `https://api.registryhost.com/fdx/v6`.

## 2. The regulatory frame (CSDF and CFPB 1033)

In January 2025 the CFPB recognized FDX as a standard-setting body under the Personal Financial Data Rights rule (Section 1033). The **Consensus Standard Data Format (CSDF) v1** is the subset of the FDX API a data provider implements to show conformance.

A conformant data provider must meet two requirements:

1. **Schema alignment.** Implement the applicable resources from Core, Customer, Money-Movement and Meta at version 5.4 or 6.4, plus whatever they need from Components. Alignment covers endpoints, methods, parameters, headers, response format, status codes, error responses, pagination and data types.
2. **Security.** Implement one approved security profile from the Security Model v5.0, either Green (OAuth 2.0) or Blue (FAPI 2.0). Message encryption and step-up authentication apply only if the parties' agreements call for them.

Scope of the Reference List: all FDX data elements for **Regulation E accounts** (deposit), **Regulation Z credit card accounts**, and **digital wallets**, plus "technical" elements such as IDs, pagination and errors. A provider does not have to share every element. It must format correctly whatever it does share, and it must implement at least one endpoint that serves each element it shares. Consent management is explicitly **not** part of CSDF v1. FDX will run a conformance test that reports which fields were tested and whether they were formatted correctly.

The YAML tags each element on the Reference List:

| Tag | Meaning |
|---|---|
| `x-fdx-csdf-account-categories: [ANY]` | Applies to both Reg E and Reg Z (and digital wallets) |
| `x-fdx-csdf-account-categories: [REG_E]` | Deposit accounts and funds transfers |
| `x-fdx-csdf-account-categories: [REG_Z]` | Credit cards, lines of credit, BNPL |
| `x-fdx-csdf-technical: true` | Supporting element (pagination, errors, links, availability) |
| (no tag) | Not on the Reference List |

The Tagging Guide gives a nine-step recipe for trimming the YAML down to a CSDF-only API: pick REG_E and/or REG_Z, keep the tagged paths in Core, Customer, Meta and Money-Movement, then add back the untagged elements that tagged operations need, such as the 200 response and the required headers.

The spreadsheet has 935 rows, each marked True/False for Reg E, Reg Z and Technical, plus a "TROC" legend: Technical, Required, Conditional, Optional. Investment, loan, annuity, insurance and commercial accounts are all marked False. **Inference:** for a bank like Citizens, the in-scope account types are DepositAccount, LineOfCreditAccount (cards), BuyNowPayLaterAccount and DigitalWallet.

## 3. Request and response conventions (all APIs)

**Headers on every path:**

| Header | Required | Notes |
|---|---|---|
| `Authorization: Bearer <token>` | Yes | Token must go in the header, never the URL |
| `x-fapi-interaction-id` | Yes | UUID, 36 characters, echoed in **every** response |
| `FDX-API-Actor-Type` | No | `USER` (customer present) or `BATCH` |
| `FDX-API-Data-Provider-Id` | No | Required when a service bureau hosts several institutions on one endpoint |
| `x-fapi-auth-date`, `x-fapi-customer-ip-address` | No | FAPI client provisions |
| `idempotency-key` | Yes on Money Movement writes | De-duplicates requests |

**Pagination.** `limit`, plus `pageKey` (opaque cursor). `offset` is deprecated. Responses extend `PaginatedArray`, which carries `page` (`nextPageKey`, `previousPageKey`, `totalElements`) and `links` (`next`, `prev`). Money Movement lists use `SynchronizableArray`, which adds `updates.nextUpdateId` and an `updatedSince` query for delta sync.

**Date filtering.** `startDate` / `endDate` (ISO 8601 `YYYY-MM-DD`). Send both or neither. The default is the last 7 days, inclusive. A start date later than the end date, earlier than the provider's supported history, or sent without its pair returns `703 Invalid date range` (HTTP 400). `startTime` / `endTime` exist but currently filter by date only.

**resultType.** `lightweight` (the default) returns descriptors only. `details` returns full entities.

**Types.** `DateString` is `YYYY-MM-DD`. `Timestamp` is RFC 3339 date-time. `Identifier` is a string of at most 256 characters. Enums are SCREAMING_SNAKE_CASE. Polymorphic entities use a `discriminator` on `accountCategory` or `securityCategory` with `oneOf`. FDX notes that some code generators mishandle `oneOf` and suggests swapping in `allOf` only for codegen.

**Success codes.** 200, 201, 204 and **206 Partial Content**. A 206 means some records came back (breadth) or some fields within records are missing (depth), for example because one system of record was down.

**Errors.** Every non-2xx response except 409 duplicates must carry an `Error` body with `code`, `message` and optional `debugMessage`. The key codes:

| Code | HTTP | Meaning |
|---|---|---|
| 401 | 400 | Invalid input |
| 403 | 403 | Forbidden |
| 500 / 501 / 503 | 500 / 500 / 503 | Internal error / subsystem unavailable / scheduled maintenance |
| 601 / 602 / 603 | 404 / 403 / 401 | Customer not found / not authorized / authentication failed |
| 701 | 404 | Account not found |
| 702 / 703 | 400 | Bad date format / invalid date range |
| 704 | 422 | Account type not supported |
| 705 | 409 | Account is closed |
| 800–808 | various | Payee, payment and recurring-payment errors |
| 901–911, 950 | various | Transfer errors (source/destination, amount, duplicate, insufficient funds, limits) |
| 1000 | 500 | Cannot retrieve key from JWKS endpoint |
| 1100 | 404 | Update ID not found, resync required |
| 1101–1104, 1108 | 404 | Reward program, categories, image or statement not found |
| 1106 | 501 | FdxVersion not supported |
| 1107 | 404 | No data for the supplied query filters |
| 1200–1207 | various | Tax errors, plus 1206 (405 Method Not Allowed) and 1207 (429 Too Many Requests) |
| 1300 | 400 | Statement still processing |

## 4. Core API (`fdxapi.core.yaml`)

| Method and path | operationId | CSDF tag | Notes |
|---|---|---|---|
| GET `/accounts` | searchForAccounts | ANY | Filter by `accountIds` (comma-separated). Returns `Accounts`; 206 allowed |
| GET `/accounts/{accountId}` | getAccount | ANY | Returns `AccountWithDetails` |
| GET `/accounts/{accountId}/transactions` | searchForAccountTransactions | ANY | Date filters and paging; 206 allowed |
| GET `/accounts/{accountId}/statements` | searchForAccountStatements | ANY | Statement list with HATEOAS links |
| GET `/accounts/{accountId}/statements/{statementId}` | getAccountStatement | ANY | PDF, GIF, JPEG, TIFF or PNG; 302 redirect allowed; 400/1300 while processing |
| GET `/accounts/{accountId}/contact` | getAccountContact | ANY | Account holders and their contacts |
| GET `/accounts/{accountId}/payment-networks` | getAccountPaymentNetworks | REG_E | Full or tokenized account number and routing number per network |
| GET `/accounts/{accountId}/terms-and-conditions` | getTermsAndConditions | ANY | JSON, PDF or ZIP via `Accept`; `Accept-Language` supported |
| GET `/accounts/{accountId}/transaction-images/{imageId}` | getAccountTransactionImages | ANY | Check or receipt images |
| GET `/accounts/{accountId}/reward-transactions` | searchForAccountRewardTransactions | ANY | Transactions that earned rewards |
| GET `/accounts/{accountId}/asset-transfer-networks` | getAccountAssetTransferNetworks | not tagged | ACATS, DTC, ATON details for brokerage transfers |
| GET `/reward-programs` | searchRewardPrograms | ANY | |
| GET `/reward-programs/{rewardProgramId}` | getRewardProgram | ANY | |
| GET `/reward-programs/{rewardProgramId}/categories` | getRewardProgramCategories | ANY | |

### Account model

`AccountDescriptor` is the lightweight base, discriminated on `accountCategory`. Its fields are `accountId` (a persistent ID, not the account number), `accountCategory`, `accountType`, `accountNumberDisplay` (masked), `productId`, `productName`, `nickname`, `status`, `description`, `accountOpenDate`, `accountCloseDate`, `currency`, `fiAttributes` and `error`.

`Account` adds `parentAccountId`, `lineOfBusinessType` (`COMMERCIAL`, `CONSUMER`, `SMALL_BUSINESS`, `OTHER`), `balanceType` (`ASSET` / `LIABILITY`), `transferIn` / `transferOut`, `billPayStatus`, `micrNumber`, `lastActivityDate`, `rewardProgramId`, `domicile`, and Canadian `bankInstitutionId` / `bankTransitId`.

There are nine account categories:

| accountCategory | Schema | CSDF | Key fields |
|---|---|---|---|
| `DEPOSIT_ACCOUNT` | DepositAccount | REG_E | `currentBalance`, `availableBalance`, `openingDayBalance`, `balanceAsOf`, `annualPercentageYield`, `interestYtd`, `earnedInterest`, `term`, `maturityDate`, `underArbitration`, overdraft fields (`overdraftOptIn`, `overdrafted`, `overdraftProtectionFunded`, `overdraftFundingSources`) |
| `LOC_ACCOUNT` | LineOfCreditAccount | REG_Z | `creditLine`, `availableCredit`, `currentBalance`, `principalBalance`, `bills[]`, `scheduledPayments[]`, `chargedInterest`, `purchasesApr`, `advancesApr`, `balanceTransfersApr`, `ppilApr`, `targetRateSaleApr`, `cashAdvanceLimit`, `availableCash`, `cashAdvanceBalance`, `transferBalance`, `ppilBalance`, `financeCharges`, `pastDueAmount`, `cardNetwork`, `cardArt`, `feeSchedule`, `underArbitration` |
| `BNPL_ACCOUNT` | BuyNowPayLaterAccount | REG_Z | `creditLine`, `availableCredit`, `principalBalance`, `currentBalance`, `financeCharges` |
| `DIGITAL_WALLET` | DigitalWallet | ANY | Balances, APY, `earnedInterest` |
| `LOAN_ACCOUNT` | LoanAccount | not tagged | Principal, escrow, payoff, `chargedInterest`, `annualPercentageRate`, student-loan `currentSchool` |
| `INVESTMENT_ACCOUNT` | InvestmentAccount | not tagged | Holdings, open orders, 401k contribution and vesting, loans, margin |
| `INSURANCE_ACCOUNT`, `ANNUITY_ACCOUNT`, `COMMERCIAL_ACCOUNT` | ... | not tagged | Commercial accounts use X9 BTRS (formerly BAI2) codes |

`AccountStatus` takes `OPEN`, `CLOSED`, `PENDINGOPEN`, `PENDINGCLOSE`, `PAID`, `DELINQUENT`, `NEGATIVECURRENTBALANCE` or `RESTRICTED`. `AccountType` has about 80 values, US and Canadian, including `CHECKING`, `SAVINGS`, `MONEYMARKET`, `CD`, `CREDITCARD`, `LINEOFCREDIT`, `BNPL`, `DIGITALWALLET` and `PREPAID`.

`Bills` covers `totalPaymentDue`, `minimumPaymentDue`, `dueDate`, `autoPayEnabled`, `autoPayAmount`, `autoPayDate`, `pastDueAmount`, `lastPaymentAmount`, `lastPaymentDate`, `statementBalance` and `statementDate`. `ScheduledPayment` covers `amount`, `date`, `type` (`AUTOPAY`, `ONE_TIME`, `REPEATING`) and `payToPrincipal`.

`InterestRate` covers `rate`, `rateAsOf`, `priorRate`, `type` (`FIXED`, `INDEXED`, `VARIABLE`), `index` and `compoundingPeriod`.

### Transaction model

`TransactionSummary` carries `accountId`, `transactionId`, `postedTimestamp`, `transactionTimestamp`, `description`, `transactionPayee`, `debitCreditMemo` (`DEBIT`, `CREDIT`, `MEMO`), `status` (`PENDING`, `POSTED`, `MEMO`, `AUTHORIZATION`), `amount`, `reward`, `fiAttributes` and `links`. `Transaction` adds `accountCategory` (the discriminator), `referenceTransactionId`, `cardNumberDisplay`, `memo`, `category` (preferably MCC or SIC), `subCategory`, `reference`, `foreignAmount`, `foreignCurrency`, `imageIds` and `lineItem[]`.

`TransactionPayee` holds `merchantName`, `merchantId`, `individualName`, `address` and `phone`.

Subtypes by category:

- **Deposit:** `transactionType` covers ATM, POS, ACH, check, fee, interest, P2P and preauthorized types. Also `checkNumber`.
- **LOC:** `transactionType` is one of `PURCHASE`, `PAYMENT`, `FEE`, `INTEREST`, `ADJUSTMENT`, `CHECK`, `WITHDRAWAL`. Also `paymentDetails`.
- **BNPL:** adds per-purchase installment details.
- **Digital wallet, loan, investment, insurance, commercial:** each has its own subtype.

### Payment networks (REG_E)

`AccountPaymentNetwork` has these fields:

- `bankId`: the routing number.
- `identifier` and `identifierType`: `ACCOUNT_NUMBER` or `TOKENIZED_ACCOUNT_NUMBER`.
- `type`: `US_ACH`, `US_FEDWIRE`, `US_FEDNOW`, `US_RTP`, `US_CHIPS`, `CA_ACSS` or `CA_LVTS`.
- `transferIn` / `transferOut` and `supportsRequestForPayment`.
- `transferLimits`: in and out limits per day, week, month, year and transaction, with max and remaining amounts and counts.

An unmasked account number is sensitive. FDX says to send it only encrypted or tokenized.

### Terms and conditions (new in v6.4)

The response is discriminated by account category:

- **Deposit:** monthly, closure, inactivity, wire, NSF, overdraft, stop-payment and other fees; overdraft waivers; `interestRateTiers` (APY by minimum balance).
- **LOC:** membership fees, `purchaseApr`, `penaltyApr`, late and overlimit fees, balance-transfer and cash-advance APRs and fees.
- **Both:** common fees, `transactionLimits`, `promotions`, `arbitrationProvision`, rewards T&Cs, and static `uris` to PDF or HTML documents.

### Deprecations to avoid in new builds

| Deprecated | Use instead |
|---|---|
| `transactions[]` on every account type (v6.3.1) | `searchForAccountTransactions` |
| `Account.contact` (v6.3.1) | `getAccountContact` |
| `AccountDescriptor.accountNumber`, `Account.routingTransitNumber` (v6.4) | `getAccountPaymentNetworks` |
| `Account.interestRate*` (v6.4) | Named rates on subtypes (`earnedInterest`, `chargedInterest`, APRs) |
| LOC `minimumPaymentAmount`, `lastPayment*`, `lastStmt*`, `statementAmountDueDate`, `nextPayment*` | `Bills` and `ScheduledPayments` |
| LOC `transfersApr` | `balanceTransfersApr` |
| Deposit / wallet `payee` | `transactionPayee` |
| `offset`, `nextOffset`, `prevOffset` | `pageKey`, `nextPageKey`, `previousPageKey` |
| Scope `fdx:accountpayments:read`, cluster `ACCOUNT_PAYMENTS` (removed in V7) | `fdx:paymentsupport:read`, `PAYMENT_SUPPORT` |

## 5. Customer, Meta, Money Movement, Notifications, Registry

**Customer** (`fdxapi.customer.yaml`, all tagged ANY):

- `GET /customers`: the holders related to permissioned accounts.
- `GET /customers/current`: the authenticated customer.
- `GET /customers/{customerId}`: returns 601 if not found.

`Customer` extends `Person` (`dateOfBirth`, `taxId` masked to the last four or sent in an encrypted payload, `taxIdCountry`, `governmentId`), which extends `Contacts` (emails, addresses, telephones). It adds `customerId`, `type`, `name`, `businessCustomer` and `accounts[]` with `relationship` (`PRIMARY`, `JOINT`, `AUTHORIZED_USER`, `AUTHORIZED_SIGNER`, `POWER_OF_ATTORNEY`, `TRUSTEE` and others).

**Meta** (`fdxapi.meta.yaml`, technical):

- `GET /availability`: status `ALIVE`, `PARTIAL`, `MAINTENANCE` or `DOWN`, plus planned windows.
- `GET /capability`: supported FDX versions, operations, connection limits, cut-off times and **`jwksUrl`** for payload encryption keys.
- `GET /certification-metrics`: response time and uptime.

**Money Movement** (`fdxapi.money-movement.yaml`, REG_E for payments and transfers). The CSDF scope here is mainly reading payment and transfer history.

- **Payees:** `/payees` (search, create, get, patch, delete). The merchant payee status is `ACTIVE`, `PENDING`, `DELETED` or `REJECTED`.
- **Payments:** `/payments` (search, schedule, get, patch, cancel). Status is `SCHEDULED`, `PROCESSING`, `PROCESSED`, `CANCELLED`, `FAILED` or `NOFUNDS`.
- **Recurring payments:** `/recurring-payments` (search, schedule, get, patch, cancel) and `/recurring-payments/{id}/payments`. Frequency runs from `ONETIME` through `ANNUALLY`. Duration is `NOEND` or `NUMBEROFTIMES`.
- **Transfers:** `/transfers` (search, request) and `/transfers/{id}` (get, cancel). These are internal transfers with a client-generated `transferId`.
- **Payment initiation parties:** `/payment-initiation-parties` and its payment-method registrations. These follow ISO 20022, use ISO 9362:2022 BIC, and include direct-deposit registrations.

**Event Notifications** (`fdxapi.event-notifications.yaml`, FAPI Baseline):

- `POST/GET/DELETE /notification-subscriptions` with a `callbackUrl`.
- `POST /notifications` (publish).
- `GET /notifications` (poll, optional `dataRecipientId`).

Categories are `CONSENT`, `FRAUD`, `MAINTENANCE`, `NEW_DATA` and `SECURITY`. Types are `CONSENT_REVOKED`, `CONSENT_UPDATED`, `RISK`, `PLANNED_OUTAGE`, `BALANCE`, `SERVICE` and `CUSTOM`. Each notification also carries a severity and a priority.

**Fraud:** `POST /fraud/suspected-incident` (for example `ACCOUNT_TAKEOVER`).

**Registry:**

- `GET /data-providers` and `GET /data-providers/{id}`.
- `GET /recipients` and `GET /recipients/{id}`.

Records use RFC 7591 snake_case fields: `scope`, `duration_type`, `duration_period`, `lookback_period`, `registry_references` (FDX, GLEIF, ICANN or PRIVATE) and `intermediaries`.

**Recipient Registration (DCR):**

- `POST /register` takes a `RecipientRequest` and returns `client_id`, `client_secret`, `grant_types`, `token_endpoint_auth_method` (e.g. `private_key_jwt`), `registration_client_uri`, `registration_access_token` and `status`.
- `GET /register/{clientId}`, `PUT /register/{clientId}` and `DELETE /register/{clientId}` manage the registration.

**Payroll:**

- `/payroll/paystubs` and `/payroll/paystubs/{id}` (JSON or Base64 PDF).
- `/payroll/reports` and `/payroll/reports/{id}` (`VOE` or `VOIE`).

## 6. Security (read this before trusting the YAML)

**The YAML and the normative security document disagree.** The YAML `securitySchemes` still declare FAPI 1.0:

- `OAuthFapi1Advanced`: authorization code with `/par` and `/token`. Used on Core, Customer, Money-Movement and Payroll.
- `OAuthFapi1Baseline`: client credentials. Used on Consent, Meta, Notifications, Registry and Recipient Registration.

The **Security Model v5.0** (May 2025) replaces all earlier FDX security models and makes the FAPI 1.0 adoption RFCs (0122, 0223, 0237) obsolete. The CSDF document says the Security Model wins over any conflicting FDX text. The two profiles it defines:

| | Green | Blue |
|---|---|---|
| Base | OAuth 2.0 (RFC 6749) | OIDF FAPI 2.0 (Feb 19, 2025) |
| Core, Customer, Money Movement, Meta, Notifications Get/Subscription | Client credentials grant | FAPI 2.0 + client credentials grant |
| Notifications Publishing | mTLS client auth (RFC 5246) or `private_key_jwt` (RFC 7521/7523) | Same, FAPI 2.0 must not be used |
| Authorization code flow listed for | "None" | "None" |
| Confidential clients only | Yes | (via FAPI 2.0) |
| Access token lifetime | ≤ 900 seconds | ≤ 900 seconds |
| Scopes in auth request must match consent request object, else fail | Yes | Yes |
| Resource server checks consent record on every request | Yes | Yes |
| Tokens revoked immediately on consent revocation | (Consent spec says so) | Yes, explicitly |
| Other Green rules | Referrer-Policy suppression, tokens only in headers, verify `iss`, distinct redirect URIs per AS, `nonce` if OIDC, refresh token rotation (RFC 9700 §4.14), audience-restricted tokens, least-privilege scopes | |

**Inference:** "authorization code flow: None", with client credentials listed for customer-data endpoints, reads oddly. The user still authorizes through PAR plus the authorize endpoint, per the Consent spec. My reading is that the profile is describing the grant used at the resource call. Flag this for Glenn or Jeff rather than assume.

**Message encryption** applies only if the parties agree to it. It uses a nested JWT (a JWS inside a JWE), either for the whole payload or for individual fields. It needs a signing key pair, an encryption or key-agreement key pair, and a symmetric content key. Private keys must sit in FIPS 140-2 Level 3 (or CC EAL4) storage with crypto-periods of two years or less. Algorithms follow the IANA JOSE recommendations, and keys are shared via the JWT header or JWKS (`/capability` `jwksUrl`).

**Step-up authentication** is also conditional. It follows RFC 9470: the resource server returns a challenge, and the client re-sends the user to the AS with `acr_values` or `max_age`.

**OAuth scopes ↔ data clusters.** The full list is the `FdxOauthScope` enum and the Consent spec. The YAML Advanced scheme lists only part of it.

| Scope | Data cluster | User-facing label | Key elements |
|---|---|---|---|
| `fdx:accountbasic:read` | ACCOUNT_BASIC | Account identifying information | Display name, masked number, type |
| `fdx:accountdetailed:read` | ACCOUNT_DETAILED | Account summary information | Basic + balances, credit limits, due dates, rates |
| `fdx:balances:read` | BALANCES | Account balances | Balance, available balance |
| `fdx:bills:read` | BILLS | Account billing information | Bill due, upcoming payment |
| `fdx:customercontact:read` | CUSTOMER_CONTACT | Account contact details | Names, address, email, phone of holders |
| `fdx:customerpersonal:read` | CUSTOMER_PERSONAL | Sensitive personal information | Contact + DoB, government ID |
| `fdx:socialsecurity:read` | SOCIAL_SECURITY_NUMBER | Social security number | SSN |
| `fdx:images:read` | IMAGES | Check images | |
| `fdx:investments:read` | INVESTMENTS | Investment account holding details | |
| `fdx:paymentsupport:read` | PAYMENT_SUPPORT | Account numbers | Full account and routing number |
| `fdx:rewards:read` | REWARDS | Reward program information | |
| `fdx:scheduledpayments:read` | SCHEDULED_PAYMENTS | Scheduled payment information | |
| `fdx:statements:read` | STATEMENTS | Account statements | |
| `fdx:tax:read` | TAX | Tax form data | |
| `fdx:termsconditions:read` | TERMS_AND_CONDITIONS | Account terms and conditions | APRs, fees, limits |
| `fdx:transactions:read` | TRANSACTIONS | Transaction data | Pending and posted |
| `fdx:transfers:read` / `:write` | TRANSFERS | Transfer information | Only read/write cluster |
| `fdx:notifications:subscribe` / `:publish` | none | | Webhooks |
| `openid`, `offline_access` | none | | Identity, refresh tokens |

## 7. Consent (Consent API Behavioral Spec v1.0 + `fdxapi.consent.yaml`)

This spec is **non-binding and not part of CSDF**, but it is the FDX design for consent.

The parties are the end user (EU), the data provider (DP) and the data recipient (DR). A data access platform (DAP), meaning an aggregator, acts as the DR at the protocol level. The DR must be pre-registered with the DP, both as an OAuth client and under a business agreement.

**Creation flow:**

1. The DR sends `POST /par` with `authorization_details: [{ "type": "fdx_v1.0", "consentRequest": {...} }]`. This is a Rich Authorization Request (RAR) inside a Pushed Authorization Request (PAR).
2. The DP returns 201 with a `request_uri`.
3. The DR redirects the user to `GET /authorize`.
4. The DP authenticates the user, who reviews the request and authorizes.
5. The DP issues the `ConsentGrant`.
6. The DP redirects back with a 302 and an authorization code.
7. The DR calls `POST /token`. The response includes `grant_id`, which equals the `consentId`.

Scope mismatches return `invalid_scope` on the redirect.

**`ConsentRequest`:**

- `durationType`: `ONE_TIME`, `PERSISTENT` or `TIME_BOUND`.
- `durationPeriod`: days.
- `lookbackPeriod`: days, measured from request time.
- `resources[]`: `resourceType` (`ACCOUNT`, `CUSTOMER` or `DOCUMENT`) plus `dataClusters[]`.

**`ConsentGrant`:**

- `id` and `status` (`ACTIVE`, `EXPIRED` or `REVOKED`).
- `parties[]`, which require `homeUri`, `registry`, `registeredEntityName` and `registeredEntityId`.
- `createdTime`, `expirationTime` and `updatedTime`.
- The duration and lookback fields.
- `resources[]` with a concrete `resourceId`.
- `links`.

**Operations** (FAPI Baseline in the YAML):

- `GET /consents/{consentId}`: returns 401 for bad credentials, and 404 if the grant is not permitted or does not exist.
- `PUT /consents/{consentId}/revocation`:
  - **Request body:** `reason` (`USER_ACTION` or `BUSINESS_RULE`) and `initiator` (a `PartyType`).
  - **Success:** 204, which may complete asynchronously.
  - **Errors:** 400, 401, 403 or 404, and 409 if the grant is already revoked or expired.
- `GET /consents/{consentId}/revocation`: the revocation history.

Revocation is final, and a modification is modeled as revoke-and-reissue.

**Sync:** notifications with category `CONSENT`, type `CONSENT_UPDATED` or `CONSENT_REVOKED`, `idType: CONSENT`, and a `customFields` entry `INITIATOR` set to `INDIVIDUAL`, `DATA_RECIPIENT` or `DATA_PROVIDER`.

**Token profile:** the JWT access token or introspection response carries `iss` (DP), `sub` (customerId), `aud` (DR), `exp`, `iat`, `jti`, `client_id`, `scope` and the private claim **`fdxConsentId`**.

## 8. UX and data minimization guidance (non-binding)

**UX Guidelines v3.0** is advisory, but it maps 1033 obligations onto screens.

The authorization disclosure must be clear, conspicuous and segregated. It must name the DR, any DAP and the DP; describe the product; list the data clusters; include a certification; give the duration, which **cannot exceed one year from the most recent authorization**; describe how to revoke; get an electronic signature; and appear in the user's language with an English link if needed.

The DP may optionally confirm the authorization and filter account selection by type, but it must not let the user alter the request.

Three integration patterns are covered:

- DR direct to the DP.
- A DAP running the authorization for the DR.
- The DR running the authorization over DAP passthrough.

On the dashboards:

- The DR must offer revocation that is "as easy to access and operate as the initial authorization."
- A DP consent dashboard is optional. If the DP has one, it can show all active authorizations and offer a full kill switch or per-recipient revocation, but it cannot revoke individual clusters.
- A reminder 30 days before expiry is recommended.
- Annual reauthorization may require the user to re-authenticate at the DP.

Section 1033.421 limits use of the data: no targeted advertising, cross-selling or sale without a separate authorization.

**Data Minimization v1.0:**

- Token scope must not exceed the DR's registered scope or the clusters shown to the user.
- DAPs must strip data down to each DR's need and delete excess PII immediately.
- One-time shares must not be persisted.
- Data must be deleted once its purpose ends.
- There should be a path for users to request deletion, carried from the DR through to the DAP.

## 9. Tax and Tools (reference only)

**Tax:**

- `GET /tax-forms` and `GET /tax-forms/{id}` accept FAPI Advanced with `fdx:tax:read`, **or** HTTP Basic using a document ID and passcode printed on the form. The Basic path has strict mitigations: one document per passcode, static data, a hashed passcode, expiry, and limits on request and failure counts.
- `POST` and `PUT` let vendors submit forms.
- Data can travel in a QR code (`TaxDataForQR` or `BasicAuthForQR`) or be embedded in a PDF (custom properties `fdxVersion`, `fdxSoftwareId`, `fdxJson`).
- Schedule K-3 is a separate file.

**Tools:**

- `fdx.spectral.ruleset.yaml` checks operationId prefixes (`get`/`search`, `create`/`schedule`, `update`, `delete`/`cancel`), camelCase properties, PascalCase schemas, required headers on every path and `x-fapi-interaction-id` on every response, alphabetical ordering, and the allowed HTTP methods.
- The yamllint configs and `lint.sh` run both checks.
- **Inference:** running this ruleset against any FDX-style spec we write is a cheap way to show conformance.

## 10. Inconsistencies found in the release

These are useful when validating a mock or sandbox, because a strict validator will reject the published examples:

1. **YAML security vs. Security Model v5.0.** Covered in section 6. The YAML still says FAPI 1.0, and `AccountPaymentNetwork` still points to "Security Model Part 4 End to End Encryption", which is now Part 2.
2. **YAML examples that don't match their own schemas:**
   - The `/accounts`, `/statements` and `/transactions` examples use `page.nextOffset` and `total`, but the schema has `nextPageKey` and `totalElements`.
   - The reward-transactions example uses `transactions` where the schema has `rewardTransactions`, and the categories example uses `categories` where the schema has `rewardCategories`.
   - The T&C examples use `limit` (schema: `maximumAmount`), `transfersAndAdvances` (schema: `balanceTransfersAndCashAdvances`) and `expiration` (schema: `expirationRules`).
3. **Consent spec examples vs. `fdxapi.consent.yaml`.** The examples use `ONETIME` and `TIME_BASED` where the enum has `ONE_TIME` and `TIME_BOUND`. `links` is shown as an object in one place and an array in another. `legalEntityName` is used where the schema has `registeredEntityName`. `DATA_RECIEPIENT` is misspelled.
4. **CSDF tags that look wrong:**
   - `Bills` and `ScheduledPayment` are tagged REG_E only, yet `LineOfCreditAccount` (REG_Z) embeds them.
   - `AccountCategoryDeposit` includes the annuity, insurance and investment categories.
   - The spreadsheet describes `PaymentNetworkTransferLimits.year` as "Employee total year to date contribution", which is a copy-paste error.
5. **A gap in the scopes list.** The YAML `OAuthFapi1Advanced` scope list leaves out `balances`, `scheduledpayments`, `socialsecurity`, `termsconditions` and `transfers`, even though they are in `FdxOauthScope`.

## 11. Implications for the Citizens MQP (Inference)

- Treat the **CSDF Reg E + Reg Z subset** as the core of any FDX-aligned Citizens build or mock: accounts, transactions, statements, payment networks, contact, T&Cs, rewards, customers, availability and capability, plus read-only payment and transfer history.
- **Confirm with Citizens whether they run Green or Blue**, and whether message encryption or step-up are in their bilateral agreements. That decision drives the token, client-auth and key-management design far more than the YAML does.
- Build consent on **PAR + RAR (`fdx_v1.0`)** with `fdxConsentId` in tokens, 900-second access tokens and immediate token revocation. Even though consent sits outside CSDF v1, FDX signals it for a future CSDF version.
- Use `x-fapi-interaction-id` end to end for traceability. The Spectral ruleset enforces it.
- Verify the current status of the CFPB 1033 rule and its compliance dates before relying on them. These documents reflect the state as of early 2025.

Not covered: the screenshots in the UX Guidelines and the IRS form images in the QR spec are pictures with no extractable text. I read their captions and recommendation text only.
