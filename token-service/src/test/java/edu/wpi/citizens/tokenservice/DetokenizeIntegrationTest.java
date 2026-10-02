package edu.wpi.citizens.tokenservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

class DetokenizeIntegrationTest extends PostgresIntegrationTest {

    private static final String ROUTING = "123456780";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void resolvesAnActiveTokenToItsAccountRefAndWritesAnAllowedAuditRow() {
        UUID accountRef = UUID.randomUUID();
        IssueTokenResponse token = issue(accountRef);
        String requestId = UUID.randomUUID().toString();

        ResponseEntity<Map> response = detokenize(ROUTING, token.tokenValue(), requestId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("accountRef", accountRef.toString());
        assertThat(auditRows(requestId)).containsExactly(
                Map.of("event_type", "DETOKENIZE", "actor", "payment-receiver", "outcome", "ALLOWED",
                        "token_id", token.tokenId()));
        assertThat(lastUsedIsSet(token.tokenId())).isTrue();
    }

    @Test
    void everyKindOfFailureGetsTheSameDenialAndADeniedAuditRow() {
        IssueTokenResponse revoked = issue(UUID.randomUUID());
        setStatus(revoked.tokenId(), "REVOKED");
        IssueTokenResponse expired = issue(UUID.randomUUID());
        jdbc.sql("UPDATE account_token SET expires_at = now() - interval '1 minute' WHERE token_id = :id")
                .param("id", expired.tokenId()).update();
        IssueTokenResponse active = issue(UUID.randomUUID());

        Map<String, ResponseEntity<Map>> denials = new HashMap<>();
        Map<String, String> requestIds = new HashMap<>();
        Map<String, String[]> attempts = Map.of(
                "unknown token", new String[] {ROUTING, "999999999999"},
                "revoked token", new String[] {ROUTING, revoked.tokenValue()},
                "expired token", new String[] {ROUTING, expired.tokenValue()},
                "wrong routing", new String[] {"999999999", active.tokenValue()},
                "missing token", new String[] {ROUTING, null});
        attempts.forEach((name, args) -> {
            String requestId = UUID.randomUUID().toString();
            requestIds.put(name, requestId);
            denials.put(name, detokenize(args[0], args[1], requestId));
        });

        assertThat(denials.values()).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isEqualTo(TokenController.DENIED);
        });
        assertThat(requestIds.values()).allSatisfy(requestId ->
                assertThat(auditRows(requestId)).singleElement()
                        .satisfies(row -> assertThat(row).containsEntry("outcome", "DENIED")));
    }

    @Test
    void auditRowsCannotBeChangedOrDeleted() {
        IssueTokenResponse token = issue(UUID.randomUUID());
        String requestId = UUID.randomUUID().toString();
        detokenize(ROUTING, token.tokenValue(), requestId);

        assertThatThrownBy(() -> jdbc.sql("UPDATE token_audit SET outcome = 'DENIED' WHERE request_id = :id")
                .param("id", requestId).update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM token_audit WHERE request_id = :id")
                .param("id", requestId).update()).hasMessageContaining("append-only");
    }

    private IssueTokenResponse issue(UUID accountRef) {
        IssueTokenRequest request = new IssueTokenRequest(accountRef, "aggregator-ui", "consent-" + UUID.randomUUID());
        return rest.postForEntity("/v1/tokens", request, IssueTokenResponse.class).getBody();
    }

    private ResponseEntity<Map> detokenize(String routingNumber, String tokenValue, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(RequestContext.ACTOR_HEADER, "payment-receiver");
        headers.set(RequestContext.REQUEST_ID_HEADER, requestId);
        Map<String, String> body = new HashMap<>();
        body.put("routingNumber", routingNumber);
        body.put("tokenValue", tokenValue);
        return rest.postForEntity("/v1/tokens/detokenize", new HttpEntity<>(body, headers), Map.class);
    }

    private void setStatus(UUID tokenId, String status) {
        jdbc.sql("UPDATE account_token SET status = CAST(:status AS token_status) WHERE token_id = :id")
                .param("status", status).param("id", tokenId).update();
    }

    private List<Map<String, Object>> auditRows(String requestId) {
        return jdbc.sql("SELECT event_type, actor, outcome, token_id FROM token_audit WHERE request_id = :id")
                .param("id", requestId).query().listOfRows().stream()
                .map(DetokenizeIntegrationTest::withoutNulls)
                .toList();
    }

    private static Map<String, Object> withoutNulls(Map<String, Object> row) {
        Map<String, Object> copy = new HashMap<>();
        row.forEach((key, value) -> {
            if (value != null) {
                copy.put(key, value);
            }
        });
        return copy;
    }

    private boolean lastUsedIsSet(UUID tokenId) {
        return jdbc.sql("SELECT last_used_at IS NOT NULL FROM account_token WHERE token_id = :id")
                .param("id", tokenId).query(Boolean.class).single();
    }
}
