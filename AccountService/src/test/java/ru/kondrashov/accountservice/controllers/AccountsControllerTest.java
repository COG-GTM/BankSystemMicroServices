package ru.kondrashov.accountservice.controllers;

import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import ru.kondrashov.accountservice.config.ApplicationSpringConfig;
import ru.kondrashov.accountservice.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.accountservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.utils.interfaces.AccountMapping;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = AccountsController.class)
@Import({SecurityConfig.class, ApplicationSpringConfig.class})
@TestPropertySource(properties = {"app.security.username=test-user", "app.security.password=test-password"})
@WithMockUser
class AccountsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountsService accountsService;
    @MockBean
    private AccountMapping accountMapping;

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
    @WithAnonymousUser
    void whenNoCredentials_thenReturns401() throws Exception {

        AccountRequestDTO account = new AccountRequestDTO("name",LocalDate.now(), UUID.randomUUID());

        mockMvc.perform(post("/v1/people/{personId}/accounts", UUID.randomUUID())
                .content(objectMapper.writeValueAsString(account))
                .contentType("application/json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void whenWrongBasicCredentials_thenReturns401() throws Exception {

        AccountRequestDTO account = new AccountRequestDTO("name",LocalDate.now(), UUID.randomUUID());

        mockMvc.perform(post("/v1/people/{personId}/accounts", UUID.randomUUID())
                .with(httpBasic("test-user", "wrong-password"))
                .content(objectMapper.writeValueAsString(account))
                .contentType("application/json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void whenValidBasicCredentials_thenReturns201() throws Exception {

        AccountRequestDTO account = new AccountRequestDTO("name",LocalDate.now(), UUID.randomUUID());

        mockMvc.perform(post("/v1/people/{personId}/accounts", UUID.randomUUID())
                .with(httpBasic("test-user", "test-password"))
                .content(objectMapper.writeValueAsString(account))
                .contentType("application/json"))
                .andExpect(status().isCreated());
    }
}
