package edu.wpi.citizens.gateway;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Echoes x-fapi-interaction-id on every response, including the 401 and 403 responses
 * the gateway produces itself. If the caller sent none, a new one is generated.
 * The header is added only if the Open Banking API has not already set it, so it is
 * never sent twice.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InteractionIdFilter implements WebFilter {

    static final String HEADER = "x-fapi-interaction-id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String received = exchange.getRequest().getHeaders().getFirst(HEADER);
        String interactionId = received != null ? received : UUID.randomUUID().toString();
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            if (!headers.containsKey(HEADER)) {
                headers.set(HEADER, interactionId);
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
