#!/usr/bin/env bash
# End-to-end check of the demo flow against the running Compose stack.
# Usage: docker compose -f infra/docker-compose.yml up --build -d && e2e/flow.sh
set -euo pipefail

API_URL="${API_URL:-http://localhost:8082}"
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

# Calls the Open Banking API. Sets BODY, STATUS and ECHOED_ID.
fdx_get() {
  local path="$1" interaction_id headers
  interaction_id="$(new_id)"
  headers="$(mktemp)"
  BODY="$(curl -s -D "$headers" -o - -H "x-fapi-interaction-id: $interaction_id" "$API_URL$path")"
  STATUS="$(awk 'NR==1 {print $2}' "$headers")"
  ECHOED_ID="$(awk 'tolower($1)=="x-fapi-interaction-id:" {print $2}' "$headers" | tr -d '\r')"
  rm -f "$headers"
  [ "$ECHOED_ID" = "$interaction_id" ] || fail "x-fapi-interaction-id not echoed on $path"
}

echo "Waiting for services"
wait_for "open-banking-api" "$API_URL/actuator/health"

echo "Flow step 2: the aggregator lists the customer's accounts"
fdx_get "/fdx/v6/accounts"
[ "$STATUS" = "200" ] || fail "accounts returned HTTP $STATUS"
echo "$BODY" | jq -e --arg id "$ACCOUNT_ID" '.accounts[] | select(.accountId == $id)' > /dev/null \
  || fail "account $ACCOUNT_ID not in the list"
pass "accounts listed, including $ACCOUNT_ID"

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

echo "All checks passed"
