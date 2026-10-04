package edu.wpi.citizens.openbanking;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every call needs a valid access token from Keycloak that carries the FDX scope for the
 * endpoint. Failures are answered with FDX Error bodies.
 */
@Configuration
public class SecurityConfig {

    private static final String INTERACTION_ID_HEADER = "x-fapi-interaction-id";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .addFilterBefore(SecurityConfig::echoInteractionId, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/fdx/v6/accounts").hasAuthority("SCOPE_fdx:accountbasic:read")
                        .requestMatchers("/fdx/v6/accounts/*/payment-networks")
                        .hasAuthority("SCOPE_fdx:paymentsupport:read")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> { })
                        .authenticationEntryPoint((request, response, e) ->
                                write(response, HttpStatus.UNAUTHORIZED, "603", "Authentication failed"))
                        .accessDeniedHandler((request, response, e) ->
                                write(response, HttpStatus.FORBIDDEN, "602", "Not authorized")))
                .build();
    }

    /**
     * FDX wants the caller's x-fapi-interaction-id echoed on every response. This runs before
     * the access token check so that 401 and 403 responses carry the header too.
     */
    private static void echoInteractionId(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String interactionId = ((HttpServletRequest) request).getHeader(INTERACTION_ID_HEADER);
        ((HttpServletResponse) response).setHeader(INTERACTION_ID_HEADER,
                interactionId != null ? interactionId : UUID.randomUUID().toString());
        chain.doFilter(request, response);
    }

    private static void write(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
