package edu.wpi.citizens.gateway;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rejects calls without a valid Keycloak access token and the FDX scope the endpoint
 * needs. Rejections use the FDX Error body.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    static final String SCOPE_ACCOUNT_BASIC = "SCOPE_fdx:accountbasic:read";
    static final String SCOPE_PAYMENT_SUPPORT = "SCOPE_fdx:paymentsupport:read";

    private static final String AUTHENTICATION_FAILED = "{\"code\":\"603\",\"message\":\"Authentication failed\"}";
    private static final String NOT_AUTHORIZED = "{\"code\":\"602\",\"message\":\"Not authorized\"}";

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, CorsConfigurationSource cors) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(spec -> spec.configurationSource(cors))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.GET, "/fdx/v6/accounts").hasAuthority(SCOPE_ACCOUNT_BASIC)
                        .pathMatchers(HttpMethod.GET, "/fdx/v6/accounts/*/payment-networks")
                        .hasAuthority(SCOPE_PAYMENT_SUPPORT)
                        .anyExchange().denyAll())
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> { })
                        .authenticationEntryPoint((exchange, e) ->
                                write(exchange, HttpStatus.UNAUTHORIZED, AUTHENTICATION_FAILED))
                        .accessDeniedHandler((exchange, e) ->
                                write(exchange, HttpStatus.FORBIDDEN, NOT_AUTHORIZED)))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${gateway.allowed-origin}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", InteractionIdFilter.HEADER));
        config.setExposedHeaders(List.of(InteractionIdFilter.HEADER));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/fdx/**", config);
        return source;
    }

    private static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String body) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
