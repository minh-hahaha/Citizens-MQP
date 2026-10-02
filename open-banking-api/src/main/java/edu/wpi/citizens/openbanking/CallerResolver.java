package edu.wpi.citizens.openbanking;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Reads who is calling from the access token Keycloak issued. */
@Component
public class CallerResolver {

    private static final String USERNAME_CLAIM = "preferred_username";
    private static final String CLIENT_ID_CLAIM = "azp";

    public Caller resolve(Jwt accessToken) {
        String userId = accessToken.getSubject();
        String username = accessToken.getClaimAsString(USERNAME_CLAIM);
        String clientId = accessToken.getClaimAsString(CLIENT_ID_CLAIM);
        if (isBlank(userId) || isBlank(username) || isBlank(clientId)) {
            throw new AccessDeniedException("Access token does not identify a customer and a client");
        }
        return new Caller(userId, username, clientId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
