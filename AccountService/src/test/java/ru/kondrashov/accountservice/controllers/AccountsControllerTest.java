package ru.kondrashov.accountservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.accountservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.accountservice.exceptions.AccountHasNonZeroBalanceException;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.utils.interfaces.AccountMapping;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = AccountsController.class)
class AccountsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountsService accountsService;
    @MockBean
    private AccountMapping accountMapping;
    @MockBean
    private Logger logger;

    @Test
    void whenValidUrlAndMethodAndContentType_thenReturns201() throws Exception {

        AccountRequestDTO account = new AccountRequestDTO("name",LocalDate.now(), UUID.randomUUID());

        mockMvc.perform(post("/v1/people/{personId}/accounts", UUID.randomUUID())
                .content(objectMapper.writeValueAsString(account))
                .contentType("application/json"))
                .andExpect(status().isCreated());
    }

    @Test
    void whenNullValue_thenReturns400() throws Exception {

        AccountRequestDTO account = new AccountRequestDTO(null,LocalDate.now(), UUID.randomUUID());

        mockMvc.perform(post("/v1/people/{personId}/accounts", UUID.randomUUID())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(account)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenIncorrectValue_thenReturns400() throws Exception {

        String request = "{\n" +
                "  \"creationDate\": \"2021-04-23\",\n" +
                "  \"name\": \"string\",\n" +
                "  \"personId\": \"42\"\n" +
                "}";

        mockMvc.perform(post("/v1/people/{personId}/accounts", UUID.randomUUID())
                .contentType("application/json")
                .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteClosesAccountAndRecordsActor() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        mockMvc.perform(delete("/v1/people/{personId}/accounts/{id}", personId, accountId)
                .header("X-Actor", "teller-7"))
                .andExpect(status().isNoContent());

        verify(accountsService).close(eq(personId), eq(accountId), eq("teller-7 (remote=127.0.0.1)"));
    }

    @Test
    void deleteWithNonZeroBalance_thenReturns409() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        doThrow(new AccountHasNonZeroBalanceException("non-zero"))
                .when(accountsService).close(eq(personId), eq(accountId), eq("remote=127.0.0.1"));

        mockMvc.perform(delete("/v1/people/{personId}/accounts/{id}", personId, accountId))
                .andExpect(status().isConflict());
    }
}
