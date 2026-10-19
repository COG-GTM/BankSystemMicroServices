package ru.kondrashov.accountservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.accountservice.config.ApplicationSpringConfig;
import ru.kondrashov.accountservice.config.SecurityConfig;
import ru.kondrashov.accountservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.security.PersonAccess;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.utils.interfaces.AccountMapping;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.kondrashov.accountservice.support.TestJwts.personToken;
import static ru.kondrashov.accountservice.support.TestJwts.serviceToken;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = AccountsController.class)
@Import({SecurityConfig.class, PersonAccess.class, ApplicationSpringConfig.class})
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
    private JwtDecoder jwtDecoder;

    @Test
    void whenValidUrlAndMethodAndContentType_thenReturns201() throws Exception {
        UUID personId = UUID.randomUUID();
        AccountRequestDTO account = new AccountRequestDTO("name",LocalDate.now(), personId);

        mockMvc.perform(post("/v1/people/{personId}/accounts", personId)
                .with(serviceToken())
                .content(objectMapper.writeValueAsString(account))
                .contentType("application/json"))
                .andExpect(status().isCreated());
    }

    @Test
    void whenNullValue_thenReturns400() throws Exception {
        UUID personId = UUID.randomUUID();
        AccountRequestDTO account = new AccountRequestDTO(null,LocalDate.now(), personId);

        mockMvc.perform(post("/v1/people/{personId}/accounts", personId)
                .with(serviceToken())
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
                .with(serviceToken())
                .contentType("application/json")
                .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenNoToken_thenEveryEndpointReturns401() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new AccountRequestDTO("name", LocalDate.now(), personId));

        mockMvc.perform(get("/v1/people/{personId}/accounts", personId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/people/{personId}/accounts", personId).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/people/{personId}/accounts/{id}", personId, accountId).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/people/{personId}/accounts/{id}", personId, accountId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountsService);
    }

    @Test
    void whenPersonTokenForAnotherPerson_thenReturns403() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new AccountRequestDTO("name", LocalDate.now(), personId));

        mockMvc.perform(get("/v1/people/{personId}/accounts", personId).with(personToken(attacker)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/people/{personId}/accounts", personId).with(personToken(attacker))
                .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/v1/people/{personId}/accounts/{id}", personId, accountId).with(personToken(attacker))
                .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/v1/people/{personId}/accounts/{id}", personId, accountId).with(personToken(attacker)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accountsService);
    }

    @Test
    void whenTokenWithoutScopeOrPersonClaim_thenReturns403() throws Exception {
        mockMvc.perform(get("/v1/people/{personId}/accounts", UUID.randomUUID())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accountsService);
    }

    @Test
    void whenPersonTokenForSamePerson_thenAllowed() throws Exception {
        UUID personId = UUID.randomUUID();

        mockMvc.perform(get("/v1/people/{personId}/accounts", personId).with(personToken(personId)))
                .andExpect(status().isOk());

        verify(accountsService).getAccountsByPersonId(personId);
    }

    @Test
    void whenCreateBodyPersonDiffersFromPath_thenReturns400() throws Exception {
        UUID personId = UUID.randomUUID();
        AccountRequestDTO account = new AccountRequestDTO("name", LocalDate.now(), UUID.randomUUID());

        mockMvc.perform(post("/v1/people/{personId}/accounts", personId)
                .with(serviceToken())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(account)))
                .andExpect(status().isBadRequest());

        verify(accountsService, never()).save(any());
    }

    @Test
    void whenUpdateBodyReassignsPerson_thenReturns400() throws Exception {
        UUID personId = UUID.randomUUID();
        AccountRequestDTO account = new AccountRequestDTO("name", LocalDate.now(), UUID.randomUUID());

        mockMvc.perform(put("/v1/people/{personId}/accounts/{id}", personId, UUID.randomUUID())
                .with(serviceToken())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(account)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountsService);
    }

    @Test
    void updateIsScopedToPathPerson() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AccountRequestDTO request = new AccountRequestDTO("name", LocalDate.now(), personId);
        Account mapped = new Account("name", LocalDate.now(), personId);
        when(accountMapping.mapToAccount(request)).thenReturn(mapped);

        mockMvc.perform(put("/v1/people/{personId}/accounts/{id}", personId, accountId)
                .with(personToken(personId))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(accountsService).update(personId, accountId, mapped);
    }

    @Test
    void whenAccountBelongsToAnotherPerson_thenUpdateAndDeleteReturn404() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID foreignAccountId = UUID.randomUUID();
        AccountRequestDTO request = new AccountRequestDTO("name", LocalDate.now(), personId);
        doThrow(new AccountNotFoundException("not found"))
                .when(accountsService).update(eq(personId), eq(foreignAccountId), any());
        doThrow(new AccountNotFoundException("not found"))
                .when(accountsService).delete(personId, foreignAccountId);

        mockMvc.perform(put("/v1/people/{personId}/accounts/{id}", personId, foreignAccountId)
                .with(serviceToken())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/v1/people/{personId}/accounts/{id}", personId, foreignAccountId)
                .with(serviceToken()))
                .andExpect(status().isNotFound());

        verify(accountsService, never()).delete(foreignAccountId);
    }
}
