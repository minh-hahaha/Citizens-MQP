package edu.wpi.citizens.openbanking;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import edu.wpi.citizens.openbanking.TokenServiceClient.IssuedToken;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.ResourceAccessException;

@SpringBootTest(properties = "openbanking.reconcile-interval-ms=3600000")
@AutoConfigureMockMvc
class AccountsControllerTest {

    private static final String INTERACTION_ID = "c770aef3-6784-41f7-8e0e-ff5f97bddb3a";
    private static final String HEADER = InteractionIdFilter.HEADER;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TokenServiceClient tokenService;

    @MockitoBean
    private KeycloakConsentClient keycloak;

    @BeforeEach
    void customersHaveConsented() {
        when(keycloak.grantCreatedAt(any(), eq("aggregator-ui"))).thenReturn(Optional.of(100L));
    }

    @Test
    void listsOnlyTheCallersAccountsAsFdxAccountDescriptors() throws Exception {
        mvc.perform(get("/fdx/v6/accounts").header(HEADER, INTERACTION_ID).with(alice()))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, INTERACTION_ID))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.accounts", hasSize(2)))
                .andExpect(jsonPath("$.accounts[0].accountId").value("acc-1001"))
                .andExpect(jsonPath("$.accounts[0].accountCategory").value("DEPOSIT_ACCOUNT"))
                .andExpect(jsonPath("$.accounts[0].accountType").value("CHECKING"))
                .andExpect(jsonPath("$.accounts[0].accountNumberDisplay").value("****4321"))
                .andExpect(jsonPath("$.accounts[0].status").value("OPEN"))
                .andExpect(jsonPath("$.accounts[0].accountNumber").doesNotExist())
                .andExpect(content().string(not(containsString("000111224321"))));
    }

    @Test
    void paymentNetworksReturnsTheTokenAndNeverTheRealAccountNumber() throws Exception {
        when(tokenService.issue(any(), eq(INTERACTION_ID)))
                .thenReturn(new IssuedToken(UUID.randomUUID(), "482910375526", "123456780"));

        mvc.perform(get("/fdx/v6/accounts/acc-1001/payment-networks").header(HEADER, INTERACTION_ID).with(alice()))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, INTERACTION_ID))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.paymentNetworks", hasSize(1)))
                .andExpect(jsonPath("$.paymentNetworks[0].bankId").value("123456780"))
                .andExpect(jsonPath("$.paymentNetworks[0].identifier").value("482910375526"))
                .andExpect(jsonPath("$.paymentNetworks[0].identifierType").value("TOKENIZED_ACCOUNT_NUMBER"))
                .andExpect(jsonPath("$.paymentNetworks[0].type").value("US_ACH"))
                .andExpect(jsonPath("$.paymentNetworks[0].transferIn").value(true))
                .andExpect(jsonPath("$.paymentNetworks[0].transferOut").value(true))
                .andExpect(content().string(not(containsString("000111224321"))));
    }

    @Test
    void unknownAccountReturnsFdxError701() throws Exception {
        mvc.perform(get("/fdx/v6/accounts/nope/payment-networks").header(HEADER, INTERACTION_ID).with(alice()))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HEADER, INTERACTION_ID))
                .andExpect(jsonPath("$.code").value("701"))
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void anotherCustomersAccountLooksLikeItDoesNotExist() throws Exception {
        mvc.perform(get("/fdx/v6/accounts/acc-2001/payment-networks").header(HEADER, INTERACTION_ID).with(alice()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("701"));
    }

    @Test
    void missingInteractionIdIsRejectedAndAHeaderIsStillReturned() throws Exception {
        mvc.perform(get("/fdx/v6/accounts").with(alice()))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists(HEADER))
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    void interactionIdThatIsNotAUuidIsRejected() throws Exception {
        mvc.perform(get("/fdx/v6/accounts").header(HEADER, "not-a-uuid").with(alice()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    void tokenServiceFailureReturnsFdxError500WithoutDetails() throws Exception {
        when(tokenService.issue(any(), any())).thenThrow(new ResourceAccessException("connection refused"));

        mvc.perform(get("/fdx/v6/accounts/acc-1001/payment-networks").header(HEADER, INTERACTION_ID).with(alice()))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(HEADER, INTERACTION_ID))
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(content().string(not(containsString("connection refused"))));
    }

    @Test
    void eachCustomerSeesOnlyTheirOwnAccounts() throws Exception {
        mvc.perform(get("/fdx/v6/accounts").header(HEADER, INTERACTION_ID)
                        .with(customer("bob", "fdx:accountbasic:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts", hasSize(1)))
                .andExpect(jsonPath("$.accounts[0].accountId").value("acc-2001"));
    }

    @Test
    void aCallWithoutAnAccessTokenReturnsFdxError603() throws Exception {
        mvc.perform(get("/fdx/v6/accounts").header(HEADER, INTERACTION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HEADER, INTERACTION_ID))
                .andExpect(jsonPath("$.code").value("603"));
    }

    @Test
    void aTokenWithoutThePaymentSupportScopeCannotReadPaymentNetworks() throws Exception {
        mvc.perform(get("/fdx/v6/accounts/acc-1001/payment-networks").header(HEADER, INTERACTION_ID)
                        .with(customer("alice", "fdx:accountbasic:read")))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HEADER, INTERACTION_ID))
                .andExpect(jsonPath("$.code").value("602"));
    }

    @Test
    void aTokenWithoutTheAccountBasicScopeCannotListAccounts() throws Exception {
        mvc.perform(get("/fdx/v6/accounts").header(HEADER, INTERACTION_ID)
                        .with(customer("alice", "fdx:paymentsupport:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("602"));
    }

    @Test
    void aTokenThatDoesNotNameACustomerIsNotAuthorized() throws Exception {
        mvc.perform(get("/fdx/v6/accounts").header(HEADER, INTERACTION_ID)
                        .with(jwt().jwt(token -> token.claim("azp", "aggregator-ui"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_fdx:accountbasic:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("602"));
    }

    @Test
    void paymentNetworksIsRefusedWhenKeycloakHasNoGrantForTheCustomer() throws Exception {
        when(keycloak.grantCreatedAt(any(), any())).thenReturn(Optional.empty());

        mvc.perform(get("/fdx/v6/accounts/acc-1002/payment-networks").header(HEADER, INTERACTION_ID)
                        .with(customer("alice", "fdx:paymentsupport:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("602"));
    }

    private static RequestPostProcessor alice() {
        return customer("alice", "fdx:accountbasic:read", "fdx:paymentsupport:read");
    }

    private static RequestPostProcessor customer(String username, String... scopes) {
        return jwt()
                .jwt(token -> token.subject("id-of-" + username)
                        .claim("preferred_username", username).claim("azp", "aggregator-ui"))
                .authorities(Arrays.stream(scopes)
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                        .toArray(GrantedAuthority[]::new));
    }
}
