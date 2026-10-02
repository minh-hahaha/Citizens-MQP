package edu.wpi.citizens.tokenservice;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/tokens")
public class TokenController {

    /** The one and only denial body. It must not reveal why a token was refused. */
    static final Map<String, String> DENIED = Map.of("error", "Token could not be resolved");

    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping
    public IssueTokenResponse issue(
            @Valid @RequestBody IssueTokenRequest request,
            @RequestHeader(value = RequestContext.ACTOR_HEADER, required = false) String actor,
            @RequestHeader(value = RequestContext.REQUEST_ID_HEADER, required = false) String requestId) {
        RequestContext context = RequestContext.fromHeaders(actor, requestId);
        return IssueTokenResponse.from(tokenService.issue(request.toLink(), context));
    }

    @PostMapping("/detokenize")
    public ResponseEntity<?> detokenize(
            @RequestBody DetokenizeRequest request,
            @RequestHeader(value = RequestContext.ACTOR_HEADER, required = false) String actor,
            @RequestHeader(value = RequestContext.REQUEST_ID_HEADER, required = false) String requestId) {
        RequestContext context = RequestContext.fromHeaders(actor, requestId);
        Optional<UUID> accountRef;
        try {
            accountRef = tokenService.detokenize(request.routingNumber(), request.tokenValue(), context);
        } catch (RuntimeException e) {
            log.error("detokenize failed, denying request_id={}", context.requestId(), e);
            accountRef = Optional.empty();
        }
        if (accountRef.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(DENIED);
        }
        return ResponseEntity.ok(new DetokenizeResponse(accountRef.get()));
    }

    @PostMapping("/{tokenId}/revoke")
    public ResponseEntity<TokenMetadata> revoke(
            @PathVariable UUID tokenId,
            @Valid @RequestBody RevokeRequest request,
            @RequestHeader(value = RequestContext.ACTOR_HEADER, required = false) String actor,
            @RequestHeader(value = RequestContext.REQUEST_ID_HEADER, required = false) String requestId) {
        RequestContext context = RequestContext.fromHeaders(actor, requestId);
        return tokenService.revoke(tokenId, request.reason(), context)
                .map(token -> ResponseEntity.ok(TokenMetadata.from(token)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/revoke-by-consent")
    public Map<String, Integer> revokeByConsent(
            @Valid @RequestBody RevokeByConsentRequest request,
            @RequestHeader(value = RequestContext.ACTOR_HEADER, required = false) String actor,
            @RequestHeader(value = RequestContext.REQUEST_ID_HEADER, required = false) String requestId) {
        RequestContext context = RequestContext.fromHeaders(actor, requestId);
        return Map.of("revokedCount", tokenService.revokeByConsent(request.consentId(), request.reason(), context));
    }

    /** Status and metadata only. Never the full token value and never an account number. */
    @GetMapping("/{tokenId}")
    public ResponseEntity<TokenMetadata> get(@PathVariable UUID tokenId) {
        return tokenService.find(tokenId)
                .map(token -> ResponseEntity.ok(TokenMetadata.from(token)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
