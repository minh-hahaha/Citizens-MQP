package edu.wpi.citizens.openbanking;

import java.util.List;

import edu.wpi.citizens.openbanking.TokenServiceClient.IssuedToken;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The two FDX endpoints. The access token (a JWT from Keycloak) says who is calling. */
@RestController
@RequestMapping("/fdx/v6/accounts")
public class AccountsController {

    private final ConsentService consents;
    private final TokenServiceClient tokenService;

    public AccountsController(ConsentService consents, TokenServiceClient tokenService) {
        this.consents = consents;
        this.tokenService = tokenService;
    }

    /** FDX searchForAccounts: the customer's accounts, with masked numbers only. */
    @GetMapping
    public Fdx.Accounts searchForAccounts(@AuthenticationPrincipal Jwt accessToken) {
        List<Fdx.AccountDescriptor> descriptors = FakeAccount.ownedBy(customerOf(accessToken)).stream()
                .map(account -> new Fdx.AccountDescriptor("DEPOSIT_ACCOUNT", account.accountId(),
                        account.accountType(), account.accountNumberDisplay(), account.nickname(), "OPEN"))
                .toList();
        return new Fdx.Accounts(new Fdx.PageMetadata(descriptors.size()), descriptors);
    }

    /** FDX getAccountPaymentNetworks: returns a token in place of the account number. */
    @GetMapping("/{accountId}/payment-networks")
    public Fdx.AccountPaymentNetworkList getAccountPaymentNetworks(
            @PathVariable String accountId, @AuthenticationPrincipal Jwt accessToken) {
        FakeAccount account = FakeAccount.ownedBy(customerOf(accessToken)).stream()
                .filter(owned -> owned.accountId().equals(accountId))
                .findFirst()
                .orElseThrow(Fdx.ErrorException::accountNotFound);
        String aggregator = accessToken.getClaimAsString("azp");
        String consentId = consents.consentIdFor(accessToken.getSubject(), aggregator);
        IssuedToken token = tokenService.issue(account.accountRef(), aggregator, consentId);
        Fdx.AccountPaymentNetwork network = new Fdx.AccountPaymentNetwork(
                token.routingNumber(), token.tokenValue(), "TOKENIZED_ACCOUNT_NUMBER", "US_ACH", true, true);
        return new Fdx.AccountPaymentNetworkList(new Fdx.PageMetadata(1), List.of(network));
    }

    @ExceptionHandler(Fdx.ErrorException.class)
    public ResponseEntity<Fdx.Error> fdxError(Fdx.ErrorException e) {
        return ResponseEntity.status(e.status).body(e.error);
    }

    private static String customerOf(Jwt accessToken) {
        return accessToken.getClaimAsString("preferred_username");
    }
}
