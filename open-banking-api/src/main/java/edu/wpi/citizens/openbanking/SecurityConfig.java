package edu.wpi.citizens.openbanking;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wpi.citizens.openbanking.fdx.FdxError;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every FDX call needs a valid access token from Keycloak carrying the right FDX scope.
 * Failures are answered with FDX Error bodies.
 */
@Configuration
public class SecurityConfig {

    static final String SCOPE_ACCOUNT_BASIC = "SCOPE_fdx:accountbasic:read";
    static final String SCOPE_PAYMENT_SUPPORT = "SCOPE_fdx:paymentsupport:read";

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/fdx/v6/accounts").hasAuthority(SCOPE_ACCOUNT_BASIC)
                        .requestMatchers(HttpMethod.GET, "/fdx/v6/accounts/*/payment-networks")
                        .hasAuthority(SCOPE_PAYMENT_SUPPORT)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> { })
                        .authenticationEntryPoint((request, response, e) ->
                                write(response, HttpStatus.UNAUTHORIZED, FdxError.AUTHENTICATION_FAILED))
                        .accessDeniedHandler((request, response, e) ->
                                write(response, HttpStatus.FORBIDDEN, FdxError.NOT_AUTHORIZED)))
                .build();
    }

    private void write(HttpServletResponse response, HttpStatus status, FdxError error) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
