package edu.wpi.citizens.gateway;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class GatewayTest {

    private static final String HEADER = InteractionIdFilter.HEADER;
    private static final String INTERACTION_ID = "c770aef3-6784-41f7-8e0e-ff5f97bddb3a";
    private static final String UPSTREAM_BODY = "{\"from\":\"open-banking-api\"}";

    /** A tiny stand-in for the Open Banking API so routing can be checked. */
    private static final HttpServer UPSTREAM = startUpstream();

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void upstream(DynamicPropertyRegistry registry) {
        registry.add("OPEN_BANKING_API_URL", () -> "http://localhost:" + UPSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @Test
    void forwardsACallWithAValidTokenAndTheRightScopeAndReturnsTheInteractionIdOnce() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority(SecurityConfig.SCOPE_ACCOUNT_BASIC)))
                .get().uri("/fdx/v6/accounts").header(HEADER, INTERACTION_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HEADER, INTERACTION_ID)
                .expectBody().json(UPSTREAM_BODY);
    }

    @Test
    void rejectsACallWithoutATokenWithFdxError603() {
        client.get().uri("/fdx/v6/accounts").header(HEADER, INTERACTION_ID)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HEADER, INTERACTION_ID)
                .expectBody().jsonPath("$.code").isEqualTo("603");
    }

    @Test
    void rejectsPaymentNetworksWithoutThePaymentSupportScopeWithFdxError602() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority(SecurityConfig.SCOPE_ACCOUNT_BASIC)))
                .get().uri("/fdx/v6/accounts/acc-1001/payment-networks").header(HEADER, INTERACTION_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().valueEquals(HEADER, INTERACTION_ID)
                .expectBody().jsonPath("$.code").isEqualTo("602");
    }

    @Test
    void forwardsPaymentNetworksWithThePaymentSupportScope() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority(SecurityConfig.SCOPE_PAYMENT_SUPPORT)))
                .get().uri("/fdx/v6/accounts/acc-1001/payment-networks").header(HEADER, INTERACTION_ID)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void rejectsAnyPathThatIsNotAnFdxEndpointEvenWithAToken() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority(SecurityConfig.SCOPE_ACCOUNT_BASIC)))
                .get().uri("/v1/tokens")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void addsAnInteractionIdToTheResponseWhenTheCallerSentNone() {
        client.get().uri("/fdx/v6/accounts")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists(HEADER);
    }

    @Test
    void answersTheBrowserPreflightFromTheAggregatorUi() {
        // An absolute URL, because the CORS check compares the Origin with the request's own origin.
        client.options().uri("http://localhost:8081/fdx/v6/accounts")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization," + HEADER)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = UPSTREAM_BODY.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                // Like the real API, the stub echoes the interaction ID itself.
                String interactionId = exchange.getRequestHeaders().getFirst(HEADER);
                if (interactionId != null) {
                    exchange.getResponseHeaders().add(HEADER, interactionId);
                }
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("could not start the upstream stub", e);
        }
    }
}
