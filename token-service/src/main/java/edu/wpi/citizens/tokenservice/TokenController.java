package edu.wpi.citizens.tokenservice;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/tokens")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping
    public IssueTokenResponse issue(@Valid @RequestBody IssueTokenRequest request) {
        return IssueTokenResponse.from(tokenService.issue(request.toLink()));
    }
}
