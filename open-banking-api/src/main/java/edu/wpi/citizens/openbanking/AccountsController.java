package edu.wpi.citizens.openbanking;

import java.util.List;

import edu.wpi.citizens.openbanking.TokenServiceClient.IssueRequest;
import edu.wpi.citizens.openbanking.TokenServiceClient.IssuedToken;
import edu.wpi.citizens.openbanking.fdx.AccountDescriptor;
import edu.wpi.citizens.openbanking.fdx.AccountPaymentNetwork;
import edu.wpi.citizens.openbanking.fdx.AccountPaymentNetworkList;
import edu.wpi.citizens.openbanking.fdx.Accounts;
import edu.wpi.citizens.openbanking.fdx.PageMetadata;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fdx/v6/accounts")
public class AccountsController {

    private static final String ACCOUNT_CATEGORY = "DEPOSIT_ACCOUNT";
    private static final String ACCOUNT_STATUS = "OPEN";
    private static final String IDENTIFIER_TYPE = "TOKENIZED_ACCOUNT_NUMBER";
    private static final String PAYMENT_NETWORK = "US_ACH";

    private final FakeAccountRepository accounts;
    private final ConsentRegistry consents;
    private final TokenServiceClient tokenService;
    private final CallerResolver callerResolver;

    public AccountsController(FakeAccountRepository accounts, ConsentRegistry consents,
                              TokenServiceClient tokenService, CallerResolver callerResolver) {
        this.accounts = accounts;
        this.consents = consents;
        this.tokenService = tokenService;
        this.callerResolver = callerResolver;
    }

    /** FDX searchForAccounts. */
    @GetMapping
    public Accounts searchForAccounts() {
        Caller caller = callerResolver.currentCaller();
        List<AccountDescriptor> descriptors = accounts.findByOwner(caller.username()).stream()
                .map(AccountsController::toDescriptor)
                .toList();
        return new Accounts(new PageMetadata(descriptors.size()), descriptors);
    }

    /** FDX getAccountPaymentNetworks. Returns a token in place of the account number. */
    @GetMapping("/{accountId}/payment-networks")
    public AccountPaymentNetworkList getAccountPaymentNetworks(
            @PathVariable String accountId,
            @RequestHeader(InteractionIdFilter.HEADER) String interactionId) {
        Caller caller = callerResolver.currentCaller();
        FakeAccount account = accounts.findByIdAndOwner(accountId, caller.username())
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        String consentId = consents.consentIdFor(caller, accountId);
        IssuedToken token = tokenService.issue(
                new IssueRequest(account.accountRef(), caller.clientId(), consentId), interactionId);
        AccountPaymentNetwork network = new AccountPaymentNetwork(
                token.routingNumber(), token.tokenValue(), IDENTIFIER_TYPE, PAYMENT_NETWORK, true, true);
        return new AccountPaymentNetworkList(new PageMetadata(1), List.of(network));
    }

    private static AccountDescriptor toDescriptor(FakeAccount account) {
        return new AccountDescriptor(ACCOUNT_CATEGORY, account.accountId(), account.accountType(),
                account.accountNumberDisplay(), account.nickname(), ACCOUNT_STATUS);
    }
}
