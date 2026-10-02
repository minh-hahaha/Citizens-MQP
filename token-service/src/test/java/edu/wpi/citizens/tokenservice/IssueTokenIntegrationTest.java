package edu.wpi.citizens.tokenservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

class IssueTokenIntegrationTest extends PostgresIntegrationTest {

    private static final int PARALLEL_CALLS = 20;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void issuesATwelveDigitTokenWithTheConfiguredRoutingNumber() {
        ResponseEntity<IssueTokenResponse> response = issue(newRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().tokenValue()).matches("[1-9][0-9]{11}");
        assertThat(response.getBody().routingNumber()).isEqualTo("123456780");
        assertThat(response.getBody().status()).isEqualTo(TokenStatus.ACTIVE);
    }

    @Test
    void returnsTheSameTokenWhenTheSameLinkIsIssuedAgain() {
        IssueTokenRequest request = newRequest();

        IssueTokenResponse first = issue(request).getBody();
        IssueTokenResponse second = issue(request).getBody();

        assertThat(second.tokenId()).isEqualTo(first.tokenId());
        assertThat(second.tokenValue()).isEqualTo(first.tokenValue());
    }

    @Test
    void issuesDifferentTokensForDifferentConsents() {
        UUID accountRef = UUID.randomUUID();

        IssueTokenResponse first = issue(new IssueTokenRequest(accountRef, "aggregator-ui", "consent-a")).getBody();
        IssueTokenResponse second = issue(new IssueTokenRequest(accountRef, "aggregator-ui", "consent-b")).getBody();

        assertThat(second.tokenValue()).isNotEqualTo(first.tokenValue());
    }

    @Test
    void twentyParallelCallsForOneLinkProduceExactlyOneActiveToken() throws Exception {
        IssueTokenRequest request = newRequest();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_CALLS);
        try {
            List<Future<ResponseEntity<IssueTokenResponse>>> futures = IntStream.range(0, PARALLEL_CALLS)
                    .mapToObj(i -> pool.submit((Callable<ResponseEntity<IssueTokenResponse>>) () -> {
                        start.await();
                        return issue(request);
                    }))
                    .toList();
            start.countDown();

            List<String> values = new java.util.ArrayList<>();
            for (Future<ResponseEntity<IssueTokenResponse>> future : futures) {
                ResponseEntity<IssueTokenResponse> response = future.get();
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                values.add(response.getBody().tokenValue());
            }

            assertThat(values).hasSize(PARALLEL_CALLS);
            assertThat(values.stream().distinct()).hasSize(1);
            assertThat(activeTokenCount(request)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsARequestWithMissingFields() {
        ResponseEntity<Map> response = rest.postForEntity("/v1/tokens", Map.of("clientId", "aggregator-ui"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<IssueTokenResponse> issue(IssueTokenRequest request) {
        return rest.postForEntity("/v1/tokens", request, IssueTokenResponse.class);
    }

    private long activeTokenCount(IssueTokenRequest request) {
        return jdbc.sql("""
                        SELECT count(*) FROM account_token
                        WHERE account_ref = :accountRef AND consent_id = :consentId AND status = 'ACTIVE'
                        """)
                .param("accountRef", request.accountRef())
                .param("consentId", request.consentId())
                .query(Long.class)
                .single();
    }

    private static IssueTokenRequest newRequest() {
        return new IssueTokenRequest(UUID.randomUUID(), "aggregator-ui", "consent-" + UUID.randomUUID());
    }
}
