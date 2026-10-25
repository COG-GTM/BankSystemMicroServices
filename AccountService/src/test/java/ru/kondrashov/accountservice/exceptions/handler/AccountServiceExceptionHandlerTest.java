package ru.kondrashov.accountservice.exceptions.handler;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import ru.kondrashov.accountservice.controllers.AccountsController;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.utils.interfaces.AccountMapping;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = AccountsController.class)
class AccountServiceExceptionHandlerTest {

    private static final String ACCOUNTS_URL = "/v1/people/{personId}/accounts";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountsService accountsService;
    @MockBean
    private AccountMapping accountMapping;
    @MockBean
    private Logger logger;

    @Test
    void accountNotFound_returns404Template() throws Exception {
        given(accountsService.getAccountsByPersonId(any())).willThrow(new AccountNotFoundException("Account with id = 1 not found."));

        mockMvc.perform(get(ACCOUNTS_URL, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account with id = 1 not found."))
                .andExpect(jsonPath("$.httpCode").value(404))
                .andExpect(jsonPath("$.exception").value("AccountNotFoundException"));
    }

    @Test
    void billNotFound_returns404Template() throws Exception {
        given(accountsService.getAccountsByPersonId(any())).willThrow(new BillNotFoundException("Bill with id = 2 not found."));

        mockMvc.perform(get(ACCOUNTS_URL, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Bill with id = 2 not found."))
                .andExpect(jsonPath("$.httpCode").value(404))
                .andExpect(jsonPath("$.exception").value("BillNotFoundException"));
    }

    @Test
    void notEnoughMoney_returns400Template() throws Exception {
        given(accountsService.getAccountsByPersonId(any())).willThrow(new NotEnoughMoneyException("Not enough money for payment on bill with ID: 3"));

        mockMvc.perform(get(ACCOUNTS_URL, UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not enough money for payment on bill with ID: 3"))
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.exception").value("NotEnoughMoneyException"));
    }

    @Test
    void validationFailure_returns400TemplateWithFieldMessages() throws Exception {
        String request = "{\"creationDate\": \"2021-04-23\", \"personId\": \"" + UUID.randomUUID() + "\"}";

        mockMvc.perform(post(ACCOUNTS_URL, UUID.randomUUID())
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Name shouldn't be empty; Name shouldn't be null"))
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.exception").value("MethodArgumentNotValidException"));
    }

    @Test
    void unreadableBody_returns400TemplateWithoutParserDetails() throws Exception {
        String request = "{\"creationDate\": \"2021-04-23\", \"name\": \"string\", \"personId\": \"42\"}";

        mockMvc.perform(post(ACCOUNTS_URL, UUID.randomUUID())
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bad Request"))
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.exception").value("HttpMessageNotReadableException"));
    }

    @Test
    void malformedPathVariable_returns400Template() throws Exception {
        mockMvc.perform(get(ACCOUNTS_URL, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.exception").value("MethodArgumentTypeMismatchException"));
    }

    @Test
    void unsupportedMethod_returns405Template() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(ACCOUNTS_URL, UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.httpCode").value(405))
                .andExpect(jsonPath("$.exception").value("HttpRequestMethodNotSupportedException"));
    }

    @Test
    void responseStatusException_keepsItsStatus() throws Exception {
        given(accountsService.getAccountsByPersonId(any())).willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Account is locked"));

        mockMvc.perform(get(ACCOUNTS_URL, UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Account is locked"))
                .andExpect(jsonPath("$.httpCode").value(409))
                .andExpect(jsonPath("$.exception").value("ResponseStatusException"));
    }

    @Test
    void unexpectedException_returns500TemplateWithoutInternalMessage() throws Exception {
        given(accountsService.getAccountsByPersonId(any())).willThrow(new IllegalStateException("jdbc:postgresql://db/accounts password=secret"));

        mockMvc.perform(get(ACCOUNTS_URL, UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal Server Error"))
                .andExpect(jsonPath("$.httpCode").value(500))
                .andExpect(jsonPath("$.exception").value("IllegalStateException"))
                .andExpect(content().string(not(containsString("secret"))));
    }
}
