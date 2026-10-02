#!/usr/bin/env bash
# Logs a fake customer in at Keycloak, grants consent to the aggregator, and prints an
# access token. It drives the same browser pages a person would click through
# (authorization code flow with PKCE), using curl.
# Usage: e2e/login.sh [username] [password]
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${REALM:-citizens}"
CLIENT_ID="${CLIENT_ID:-aggregator-ui}"
REDIRECT_URI="${REDIRECT_URI:-http://localhost:5173/}"
SCOPE="${SCOPE:-openid fdx:accountbasic:read fdx:paymentsupport:read}"
USERNAME="${1:-alice}"
PASSWORD="${2:-password}"

OIDC_URL="$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
JAR="$WORK_DIR/cookies"

die() { echo "login failed: $1" >&2; exit 1; }

# Pulls the first form's action URL out of a Keycloak HTML page.
form_action() {
  local action
  action="$(grep -o 'action="[^"]*"' "$1" | head -n 1 | sed 's/^action="//; s/"$//; s/&amp;/\&/g')"
  case "$action" in
    http*) echo "$action" ;;
    /*) echo "$KEYCLOAK_URL$action" ;;
    *) echo "" ;;
  esac
}

location_header() {
  awk 'tolower($1) == "location:" {print $2}' "$1" | tr -d '\r'
}

VERIFIER="$(openssl rand -base64 64 | tr -d '=+/\n' | cut -c1-64)"
CHALLENGE="$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | openssl base64 | tr '+/' '-_' | tr -d '=\n')"

# 1. Ask for authorization. Keycloak answers with its login page.
curl -s -c "$JAR" -b "$JAR" -o "$WORK_DIR/login.html" -G "$OIDC_URL/auth" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "response_type=code" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "scope=$SCOPE" \
  --data-urlencode "code_challenge=$CHALLENGE" \
  --data-urlencode "code_challenge_method=S256"
LOGIN_ACTION="$(form_action "$WORK_DIR/login.html")"
[ -n "$LOGIN_ACTION" ] || die "no login form"

# 2. Submit the username and password.
curl -s -c "$JAR" -b "$JAR" -D "$WORK_DIR/login.headers" -o /dev/null \
  --data-urlencode "username=$USERNAME" --data-urlencode "password=$PASSWORD" "$LOGIN_ACTION"
NEXT="$(location_header "$WORK_DIR/login.headers")"
[ -n "$NEXT" ] || die "wrong username or password"

# 3. If Keycloak shows the consent page, click Yes. It is skipped when consent already exists.
if [[ "$NEXT" != "$REDIRECT_URI"* ]]; then
  curl -s -c "$JAR" -b "$JAR" -o "$WORK_DIR/consent.html" "$NEXT"
  CONSENT_ACTION="$(form_action "$WORK_DIR/consent.html")"
  CONSENT_CODE="$(grep -o 'name="code" value="[^"]*"' "$WORK_DIR/consent.html" | sed 's/.*value="//; s/"$//')"
  [ -n "$CONSENT_ACTION" ] || die "no consent form"
  curl -s -c "$JAR" -b "$JAR" -D "$WORK_DIR/consent.headers" -o /dev/null \
    --data-urlencode "code=$CONSENT_CODE" --data-urlencode "accept=Yes" "$CONSENT_ACTION"
  NEXT="$(location_header "$WORK_DIR/consent.headers")"
fi
[[ "$NEXT" == "$REDIRECT_URI"* ]] || die "no redirect back to the aggregator"

# 4. Swap the authorization code for an access token.
CODE="$(echo "$NEXT" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')"
[ -n "$CODE" ] || die "no authorization code"
curl -s "$OIDC_URL/token" \
  -d "grant_type=authorization_code" -d "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" -d "code=$CODE" -d "code_verifier=$VERIFIER" \
  | jq -er '.access_token' || die "token exchange failed"
