#!/usr/bin/env bash
# End-to-end check of the demo flow against the running Compose stack.
# Usage: docker compose -f infra/docker-compose.yml up --build -d && e2e/flow.sh
set -euo pipefail

API_URL="${API_URL:-http://localhost:8081}"
export KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
E2E_DIR="$(cd "$(dirname "$0")" && pwd)"
PAYMENTS_URL="${PAYMENTS_URL:-http://localhost:8084}"
# DEV-ONLY Keycloak admin login, from infra/docker-compose.yml.
KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-dev-only-admin-password}"
REVOCATION_TIMEOUT_SECONDS=30
ACCOUNT_ID="${ACCOUNT_ID:-acc-1001}"
FAKE_ACCOUNT_NUMBER="000111224321"
WAIT_SECONDS=90

pass() { echo "  ok    $1"; }
fail() { echo "  FAIL  $1"; exit 1; }
new_id() { uuidgen | tr '[:upper:]' '[:lower:]'; }

wait_for() {
  local name="$1" url="$2"
  for _ in $(seq "$WAIT_SECONDS"); do
    if curl -fs -o /dev/null "$url"; then return 0; fi
    sleep 1
  done
  fail "$name did not come up at $url"
}

# Calls the Open Banking API through the gateway with the given access token
# (empty for none). Sets BODY, STATUS and ECHOED_ID.
fdx_get() {
  local path="$1" access_token="${2-$ACCESS_TOKEN}" interaction_id headers
  local auth=()
  [ -z "$access_token" ] || auth=(-H "Authorization: Bearer $access_token")
  interaction_id="$(new_id)"
  headers="$(mktemp)"
  BODY="$(curl -s -D "$headers" -o - -H "x-fapi-interaction-id: $interaction_id" \
    ${auth[@]+"${auth[@]}"} "$API_URL$path")"
  STATUS="$(awk 'NR==1 {print $2}' "$headers")"
  ECHOED_ID="$(awk 'tolower($1)=="x-fapi-interaction-id:" {print $2}' "$headers" | tr -d '\r')"
  rm -f "$headers"
  [ "$ECHOED_ID" = "$interaction_id" ] || fail "x-fapi-interaction-id not echoed on $path"
}

# Sends a payment to routing number + account identifier. Sets BODY and STATUS.
pay() {
  local routing="$1" identifier="$2" out
  out="$(curl -s -w '\n%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"routingNumber\": \"$routing\", \"accountIdentifier\": \"$identifier\", \"amount\": 25.00}" \
    "$PAYMENTS_URL/v1/payments")"
  STATUS="$(echo "$out" | tail -n 1)"
  BODY="$(echo "$out" | sed '$d')"
}

echo "Waiting for services"
wait_for "keycloak" "$KEYCLOAK_URL/realms/citizens/.well-known/openid-configuration"
wait_for "gateway" "$API_URL/actuator/health"
wait_for "payment-receiver" "$PAYMENTS_URL/actuator/health"

echo "Flow step 1: the customer logs in at the bank and consents"
ACCESS_TOKEN="$("$E2E_DIR/login.sh" alice password)"
[ -n "$ACCESS_TOKEN" ] || fail "no access token from Keycloak"
pass "alice logged in and consented, aggregator holds an access token"

echo "Flow step 2: the aggregator lists the customer's accounts through the gateway"
fdx_get "/fdx/v6/accounts" ""
[ "$STATUS" = "401" ] && [ "$(echo "$BODY" | jq -r '.code')" = "603" ] \
  || fail "a call without an access token did not return FDX error 603 (HTTP $STATUS)"
pass "a call without an access token returns FDX error 603"

fdx_get "/fdx/v6/accounts"
[ "$STATUS" = "200" ] || fail "accounts returned HTTP $STATUS"
echo "$BODY" | jq -e --arg id "$ACCOUNT_ID" '.accounts[] | select(.accountId == $id)' > /dev/null \
  || fail "account $ACCOUNT_ID not in the list"
[ "$(echo "$BODY" | jq '[.accounts[] | select(.accountId | startswith("acc-1") | not)] | length')" = "0" ] \
  || fail "the list contains another customer's accounts"
pass "alice's accounts listed, including $ACCOUNT_ID, and nobody else's"

echo "Flow step 3: the aggregator gets a token, not the account number"
fdx_get "/fdx/v6/accounts/$ACCOUNT_ID/payment-networks"
[ "$STATUS" = "200" ] || fail "payment-networks returned HTTP $STATUS"
TOKEN="$(echo "$BODY" | jq -r '.paymentNetworks[0].identifier')"
ROUTING="$(echo "$BODY" | jq -r '.paymentNetworks[0].bankId')"
[ "$(echo "$BODY" | jq -r '.paymentNetworks[0].identifierType')" = "TOKENIZED_ACCOUNT_NUMBER" ] \
  || fail "identifierType is not TOKENIZED_ACCOUNT_NUMBER"
[[ "$TOKEN" =~ ^[1-9][0-9]{11}$ ]] || fail "identifier is not a 12-digit token"
[[ "$BODY" != *"$FAKE_ACCOUNT_NUMBER"* ]] || fail "response contains the real account number"
pass "got token ending ${TOKEN: -4} with routing number $ROUTING"

fdx_get "/fdx/v6/accounts/$ACCOUNT_ID/payment-networks"
[ "$(echo "$BODY" | jq -r '.paymentNetworks[0].identifier')" = "$TOKEN" ] \
  || fail "second call returned a different token"
pass "asking again returns the same token"

fdx_get "/fdx/v6/accounts/does-not-exist/payment-networks"
[ "$STATUS" = "404" ] && [ "$(echo "$BODY" | jq -r '.code')" = "701" ] \
  || fail "unknown account did not return FDX error 701"
pass "unknown account returns FDX error 701"

fdx_get "/fdx/v6/accounts/acc-2001/payment-networks"
[ "$STATUS" = "404" ] || fail "alice could read bob's account (HTTP $STATUS)"
pass "another customer's account is not reachable"

BASIC_ONLY_TOKEN="$(SCOPE="openid fdx:accountbasic:read" "$E2E_DIR/login.sh" alice password)"
fdx_get "/fdx/v6/accounts/$ACCOUNT_ID/payment-networks" "$BASIC_ONLY_TOKEN"
[ "$STATUS" = "403" ] && [ "$(echo "$BODY" | jq -r '.code')" = "602" ] \
  || fail "a token without fdx:paymentsupport:read was not refused with FDX error 602 (HTTP $STATUS)"
pass "a token without the payment scope returns FDX error 602"

echo "Flow step 4: a payment to the token succeeds"
pay "$ROUTING" "$TOKEN"
[ "$STATUS" = "201" ] && [ "$(echo "$BODY" | jq -r '.status')" = "POSTED" ] \
  || fail "payment to the token was not posted (HTTP $STATUS)"
PAYMENT_ID="$(echo "$BODY" | jq -r '.paymentId')"
curl -s "$PAYMENTS_URL/v1/ledger" | jq -e --arg id "$PAYMENT_ID" '.[] | select(.paymentId == $id)' > /dev/null \
  || fail "payment $PAYMENT_ID is not in the ledger"
pass "payment posted to the ledger"

pay "$ROUTING" "999999999999"
[ "$STATUS" = "422" ] && [ "$(echo "$BODY" | jq -r '.status')" = "REJECTED" ] \
  || fail "payment to a made-up token was not rejected (HTTP $STATUS)"
pass "payment to a made-up token is rejected"

echo "Flow step 5: the customer revokes consent at the bank"
ADMIN_TOKEN="$(curl -s "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  -d "username=$KEYCLOAK_ADMIN_USER" -d "password=$KEYCLOAK_ADMIN_PASSWORD" | jq -r '.access_token')"
USER_ID="$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$KEYCLOAK_URL/admin/realms/citizens/users?username=alice&exact=true" | jq -r '.[0].id')"
# The same call Keycloak's account console makes when a customer removes an application's access.
REVOKE_STATUS="$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$KEYCLOAK_URL/admin/realms/citizens/users/$USER_ID/consents/aggregator-ui")"
[ "$REVOKE_STATUS" = "204" ] || fail "could not revoke alice's consent in Keycloak (HTTP $REVOKE_STATUS)"
REVOKED_AT="$(date +%s)"
pass "alice's consent for the aggregator is revoked in Keycloak"

echo "Flow step 6: the same payment now fails"
DELAY=""
for _ in $(seq "$REVOCATION_TIMEOUT_SECONDS"); do
  pay "$ROUTING" "$TOKEN"
  if [ "$STATUS" = "422" ]; then DELAY="$(( $(date +%s) - REVOKED_AT ))"; break; fi
  sleep 1
done
[ -n "$DELAY" ] || fail "payment to the token still succeeds ${REVOCATION_TIMEOUT_SECONDS}s after consent was revoked"
REVOKED_REASON="$(echo "$BODY" | jq -r '.reason')"
pass "payment to the token is rejected, about ${DELAY}s after consent was revoked"

pay "$ROUTING" "999999999999"
[ "$(echo "$BODY" | jq -r '.reason')" = "$REVOKED_REASON" ] \
  || fail "a revoked token and a made-up token are rejected with different reasons"
pass "the rejection looks the same as for a made-up token"

fdx_get "/fdx/v6/accounts/$ACCOUNT_ID/payment-networks"
[ "$STATUS" = "403" ] && [ "$(echo "$BODY" | jq -r '.code')" = "602" ] \
  || fail "the old access token could still get a token after revocation (HTTP $STATUS)"
pass "the old access token can no longer get a token (FDX error 602)"

echo "Afterwards: the customer links the account again"
ACCESS_TOKEN="$("$E2E_DIR/login.sh" alice password)"
fdx_get "/fdx/v6/accounts/$ACCOUNT_ID/payment-networks"
NEW_TOKEN="$(echo "$BODY" | jq -r '.paymentNetworks[0].identifier')"
[ "$STATUS" = "200" ] && [ "$NEW_TOKEN" != "$TOKEN" ] || fail "re-linking did not produce a new token"
pay "$ROUTING" "$NEW_TOKEN"
[ "$STATUS" = "201" ] || fail "payment to the new token was not posted (HTTP $STATUS)"
pay "$ROUTING" "$TOKEN"
[ "$STATUS" = "422" ] || fail "the old token works again after re-linking"
pass "a new consent gets a new token, and the old token stays dead"

echo "All checks passed"
