package edu.wpi.citizens.paymentreceiver;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PaymentControllerTest {

    private static final String ROUTING = "123456780";
    private static final String TOKEN = "482910375526";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TokenServiceClient tokenService;

    @Test
    void postsAPaymentToTheLedgerWhenTheTokenResolves() throws Exception {
        UUID accountRef = UUID.randomUUID();
        when(tokenService.detokenize(eq(ROUTING), eq(TOKEN), any())).thenReturn(Optional.of(accountRef));

        pay(ROUTING, TOKEN, "25.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.paymentId").exists());

        mvc.perform(get("/v1/ledger"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountRef").value(accountRef.toString()))
                .andExpect(jsonPath("$[0].amount").value(25.00));
    }

    @Test
    void rejectsAPaymentAndPostsNothingWhenTheTokenDoesNotResolve() throws Exception {
        when(tokenService.detokenize(any(), any(), any())).thenReturn(Optional.empty());

        pay(ROUTING, TOKEN, "25.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("Unable to locate account"));

        mvc.perform(get("/v1/ledger")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsAMalformedPaymentRequest() throws Exception {
        pay("12345", TOKEN, "25.00").andExpect(status().isBadRequest());
        pay(ROUTING, "abc", "25.00").andExpect(status().isBadRequest());
        pay(ROUTING, TOKEN, "0").andExpect(status().isBadRequest());
        pay(ROUTING, TOKEN, "-5").andExpect(status().isBadRequest());
    }

    private ResultActions pay(String routing, String identifier, String amount) throws Exception {
        String body = """
                {"routingNumber": "%s", "accountIdentifier": "%s", "amount": %s}
                """.formatted(routing, identifier, amount);
        return mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
