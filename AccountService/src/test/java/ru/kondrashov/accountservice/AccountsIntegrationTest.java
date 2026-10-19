package ru.kondrashov.accountservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.kondrashov.accountservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.repositories.AccountsRepository;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.kondrashov.accountservice.support.TestJwts.serviceToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
public class AccountsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountsRepository accountsRepository;

    Account init(String name, LocalDate localDate, UUID personId) throws Exception {
        AccountRequestDTO accountRequestDTO = new AccountRequestDTO(name, localDate, personId);

        mockMvc.perform(post("/v1/people/{personId}/accounts", personId)
                .with(serviceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(accountRequestDTO)))
                .andExpect(status().isCreated());
        return accountsRepository.getAccountsByPersonId(personId).stream().findFirst().get();
    }

    @Test
    void save() throws Exception {
        UUID personId = UUID.randomUUID();
        LocalDate localDate = LocalDate.now();
        String name = "Salary";

        Account account = init(name, localDate, personId);

        assertThat(account.getName()).isEqualToIgnoringCase(name);
        assertThat(account.getCreationDate()).isEqualTo(localDate);
        assertThat(account.getPersonId()).isEqualTo(personId);
    }

    @Test
    void update() throws Exception {
        UUID personId = UUID.randomUUID();
        LocalDate localDate = LocalDate.now();
        String oldName = "Salary";

        Account oldAccount = init(oldName, localDate, personId);

        String newName = "Salary12";
        AccountRequestDTO newAccountRequestDTO = new AccountRequestDTO(newName, localDate, personId);

        mockMvc.perform(put("/v1/people/{personId}/accounts/{id}", personId,oldAccount.getId())
                .with(serviceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newAccountRequestDTO)))
                .andExpect(status().isAccepted());
        Account newAccount = accountsRepository.getAccountsByPersonId(personId).stream().findFirst().get();
        assertThat(newAccount.getName()).isEqualToIgnoringCase(newName);
    }

    @Test
    void delete() throws Exception {
        UUID personId = UUID.randomUUID();
        LocalDate localDate = LocalDate.now();
        String name = "Salary";

        Account account = init(name, localDate, personId);

        mockMvc.perform(MockMvcRequestBuilders.delete("/v1/people/{personId}/accounts/{id}", personId,account.getId())
                .with(serviceToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        assertThat(accountsRepository.getAccountsByPersonId(personId).stream().findFirst().isPresent()).isFalse();
    }

    @Test
    void updateAndDeleteThroughAnotherPersonPathReturn404AndLeaveAccountUntouched() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID otherPersonId = UUID.randomUUID();
        Account account = init("Salary", LocalDate.now(), ownerId);

        mockMvc.perform(put("/v1/people/{personId}/accounts/{id}", otherPersonId, account.getId())
                .with(serviceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AccountRequestDTO("Hijacked", LocalDate.now(), otherPersonId))))
                .andExpect(status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.delete("/v1/people/{personId}/accounts/{id}", otherPersonId, account.getId())
                .with(serviceToken()))
                .andExpect(status().isNotFound());

        Account stored = accountsRepository.findById(account.getId()).get();
        assertThat(stored.getPersonId()).isEqualTo(ownerId);
        assertThat(stored.getName()).isEqualTo("Salary");
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        UUID personId = UUID.randomUUID();
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/people/{personId}/accounts", personId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/people/{personId}/accounts", personId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AccountRequestDTO("Salary", LocalDate.now(), personId))))
                .andExpect(status().isUnauthorized());
        assertThat(accountsRepository.getAccountsByPersonId(personId)).isEmpty();
    }
}
