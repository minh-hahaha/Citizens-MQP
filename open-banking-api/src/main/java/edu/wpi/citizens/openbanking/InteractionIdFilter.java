package edu.wpi.citizens.openbanking;

import java.io.IOException;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wpi.citizens.openbanking.fdx.FdxError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * FDX requires x-fapi-interaction-id (a UUID) on every request and echoes it on every
 * response, including errors.
 */
@Component
public class InteractionIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "x-fapi-interaction-id";

    private static final String FDX_PATH_PREFIX = "/fdx/";

    private final ObjectMapper objectMapper;

    public InteractionIdFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(FDX_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String interactionId = request.getHeader(HEADER);
        if (!isUuid(interactionId)) {
            response.setHeader(HEADER, UUID.randomUUID().toString());
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    FdxError.INVALID_INPUT.withDebugMessage(HEADER + " header must be a UUID"));
            return;
        }
        response.setHeader(HEADER, interactionId);
        chain.doFilter(request, response);
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
