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
import ru.kondrashov.accountservice.controllers.dto.AccountResponseDTO;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.utils.interfaces.AccountMapping;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void whenGetAccountById_thenReturnsSingleAccountWithoutListing() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = new Account("name", LocalDate.now(), personId);
        account.setId(accountId);
        AccountResponseDTO dto = new AccountResponseDTO();
        dto.setId(accountId);
        dto.setPersonId(personId);
        when(accountsService.getAccount(accountId)).thenReturn(account);
        when(accountMapping.mapToAccountResponseDTO(account)).thenReturn(dto);

        mockMvc.perform(get("/v1/people/{personId}/accounts/{id}", personId, accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.personId").value(personId.toString()));

        verify(accountsService, never()).getAccountsByPersonId(personId);
    }

    @Test
    void whenGetAccountOfAnotherPerson_thenReturns404() throws Exception {
        UUID accountId = UUID.randomUUID();
        Account account = new Account("name", LocalDate.now(), UUID.randomUUID());
        account.setId(accountId);
        when(accountsService.getAccount(accountId)).thenReturn(account);

        mockMvc.perform(get("/v1/people/{personId}/accounts/{id}", UUID.randomUUID(), accountId))
                .andExpect(status().isNotFound());
    }

    @Test
    void whenGetMissingAccount_thenReturns404() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accountsService.getAccount(accountId)).thenThrow(new AccountNotFoundException("Account with id = "+accountId+" not found."));

        mockMvc.perform(get("/v1/people/{personId}/accounts/{id}", UUID.randomUUID(), accountId))
                .andExpect(status().isNotFound());
    }

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



}