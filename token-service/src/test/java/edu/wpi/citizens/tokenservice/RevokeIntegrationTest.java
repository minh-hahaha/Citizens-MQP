package edu.wpi.citizens.tokenservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

class RevokeIntegrationTest extends PostgresIntegrationTest {

    private static final String ROUTING = "123456780";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ExpiryJob expiryJob;

    @Test
    void aRevokedTokenCanNoLongerBeResolved() {
        IssueTokenResponse token = issue(UUID.randomUUID(), newConsentId());
        assertThat(detokenize(token.tokenValue()).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<TokenMetadata> revoked = revoke(token.tokenId());

        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(revoked.getBody().status()).isEqualTo(TokenStatus.REVOKED);
        assertThat(revoked.getBody().revokeReason()).isEqualTo("CONSENT_REVOKED");
        assertThat(revoked.getBody().revokedAt()).isNotNull();
        ResponseEntity<Map> afterRevoke = detokenize(token.tokenValue());
        assertThat(afterRevoke.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(afterRevoke.getBody()).isEqualTo(TokenController.DENIED);
    }

    @Test
    void revokingTwiceIsHarmless() {
        IssueTokenResponse token = issue(UUID.randomUUID(), newConsentId());

        TokenMetadata first = revoke(token.tokenId()).getBody();
        TokenMetadata second = revoke(token.tokenId()).getBody();

        assertThat(second.status()).isEqualTo(TokenStatus.REVOKED);
        assertThat(second.revokedAt()).isEqualTo(first.revokedAt());
    }

    @Test
    void revokingAnUnknownTokenReturnsNotFound() {
        assertThat(revoke(UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void revokeByConsentRevokesEveryTokenUnderThatConsentAndNoOthers() {
        String consentId = newConsentId();
        IssueTokenResponse first = issue(UUID.randomUUID(), consentId);
        IssueTokenResponse second = issue(UUID.randomUUID(), consentId);
        IssueTokenResponse other = issue(UUID.randomUUID(), newConsentId());

        ResponseEntity<Map> response = rest.postForEntity("/v1/tokens/revoke-by-consent",
                new RevokeByConsentRequest(consentId, "CONSENT_REVOKED"), Map.class);

        assertThat(response.getBody()).containsEntry("revokedCount", 2);
        assertThat(detokenize(first.tokenValue()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detokenize(second.tokenValue()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(detokenize(other.tokenValue()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aNewTokenIsIssuedForTheSameLinkAfterTheOldOneIsRevoked() {
        UUID accountRef = UUID.randomUUID();
        String consentId = newConsentId();
        IssueTokenResponse old = issue(accountRef, consentId);
        revoke(old.tokenId());

        IssueTokenResponse fresh = issue(accountRef, consentId);

        assertThat(fresh.tokenValue()).isNotEqualTo(old.tokenValue());
        assertThat(detokenize(old.tokenValue()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getReturnsMetadataWithoutTheFullTokenValue() {
        IssueTokenResponse token = issue(UUID.randomUUID(), newConsentId());

        ResponseEntity<String> response = rest.getForEntity("/v1/tokens/" + token.tokenId(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"ACTIVE\"");
        assertThat(response.getBody()).contains("****" + token.tokenValue().substring(8));
        assertThat(response.getBody()).doesNotContain(token.tokenValue());
    }

    @Test
    void getUnknownTokenReturnsNotFoundAndMalformedIdReturnsBadRequest() {
        assertThat(rest.getForEntity("/v1/tokens/" + UUID.randomUUID(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/v1/tokens/not-a-uuid", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theExpiryJobMarksTokensPastTheirExpiryAsExpired() {
        IssueTokenResponse token = issue(UUID.randomUUID(), newConsentId());
        jdbc.sql("UPDATE account_token SET expires_at = now() - interval '1 minute' WHERE token_id = :id")
                .param("id", token.tokenId()).update();

        expiryJob.run();

        TokenMetadata metadata = rest.getForObject("/v1/tokens/" + token.tokenId(), TokenMetadata.class);
        assertThat(metadata.status()).isEqualTo(TokenStatus.EXPIRED);
    }

    private IssueTokenResponse issue(UUID accountRef, String consentId) {
        return rest.postForEntity("/v1/tokens",
                new IssueTokenRequest(accountRef, "aggregator-ui", consentId), IssueTokenResponse.class).getBody();
    }

    private ResponseEntity<TokenMetadata> revoke(UUID tokenId) {
        return rest.postForEntity("/v1/tokens/" + tokenId + "/revoke",
                new RevokeRequest("CONSENT_REVOKED"), TokenMetadata.class);
    }

    private ResponseEntity<Map> detokenize(String tokenValue) {
        return rest.postForEntity("/v1/tokens/detokenize", new DetokenizeRequest(ROUTING, tokenValue), Map.class);
    }

    private static String newConsentId() {
        return "consent-" + UUID.randomUUID();
    }
}
