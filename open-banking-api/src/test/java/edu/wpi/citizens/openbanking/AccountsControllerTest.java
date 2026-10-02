package edu.wpi.citizens.openbanking;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import edu.wpi.citizens.openbanking.TokenServiceClient.IssuedToken;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

@SpringBootTest
@AutoConfigureMockMvc
class AccountsControllerTest {

    private static final String INTERACTION_ID = "c770aef3-6784-41f7-8e0e-ff5f97bddb3a";
    private static final String HEADER = InteractionIdFilter.HEADER;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TokenServiceClient tokenService;

    @Test
    void listsOnlyTheCallersAccountsAsFdxAccountDescriptors() throws Exception {
        mvc.perform(get("/fdx/v6/accounts").header(HEADER, INTERACTION_ID))
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

        mvc.perform(get("/fdx/v6/accounts/acc-1001/payment-networks").header(HEADER, INTERACTION_ID))
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
        mvc.perform(get("/fdx/v6/accounts/nope/payment-networks").header(HEADER, INTERACTION_ID))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HEADER, INTERACTION_ID))
                .andExpect(jsonPath("$.code").value("701"))
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void anotherCustomersAccountLooksLikeItDoesNotExist() throws Exception {
        mvc.perform(get("/fdx/v6/accounts/acc-2001/payment-networks").header(HEADER, INTERACTION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("701"));
    }

    @Test
    void missingInteractionIdIsRejectedAndAHeaderIsStillReturned() throws Exception {
        mvc.perform(get("/fdx/v6/accounts"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists(HEADER))
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    void interactionIdThatIsNotAUuidIsRejected() throws Exception {
        mvc.perform(get("/fdx/v6/accounts").header(HEADER, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    void tokenServiceFailureReturnsFdxError500WithoutDetails() throws Exception {
        when(tokenService.issue(any(), any())).thenThrow(new ResourceAccessException("connection refused"));

        mvc.perform(get("/fdx/v6/accounts/acc-1001/payment-networks").header(HEADER, INTERACTION_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(HEADER, INTERACTION_ID))
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(content().string(not(containsString("connection refused"))));
    }
}
