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
import ru.kondrashov.accountservice.entities.AccountAuditAction;
import ru.kondrashov.accountservice.entities.AccountAuditRecord;
import ru.kondrashov.accountservice.entities.AccountStatus;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.repositories.AccountAuditRepository;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Autowired
    private BillsRepository billsRepository;

    @Autowired
    private AccountAuditRepository accountAuditRepository;

    Account init(String name, LocalDate localDate, UUID personId) throws Exception {
        AccountRequestDTO accountRequestDTO = new AccountRequestDTO(name, localDate, personId);

        mockMvc.perform(post("/v1/people/{personId}/accounts", personId)
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
        Bill emptyBill = billsRepository.save(new Bill(Currency.getInstance("USD"), BigDecimal.ZERO, false, account));

        mockMvc.perform(MockMvcRequestBuilders.delete("/v1/people/{personId}/accounts/{id}", personId,account.getId())
                .header("X-Actor", "teller-7")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        Account closed = accountsRepository.getAccountsById(account.getId()).get();
        assertThat(closed.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(closed.getClosedAt()).isNotNull();
        assertThat(billsRepository.getBillById(emptyBill.getId())).isPresent();

        mockMvc.perform(get("/v1/people/{personId}/accounts", personId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        List<AccountAuditRecord> audit = accountAuditRepository.findByAccountIdOrderByOccurredAtAsc(account.getId());
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getAction()).isEqualTo(AccountAuditAction.CLOSED);
        assertThat(audit.get(0).getActor()).startsWith("teller-7");
        assertThat(audit.get(0).getBeforeState()).contains("\"status\":\"OPEN\"");
        assertThat(audit.get(0).getAfterState()).contains("\"status\":\"CLOSED\"");
    }

    @Test
    void deleteWithNonZeroBalanceIsRefused() throws Exception {
        UUID personId = UUID.randomUUID();
        Account account = init("Salary", LocalDate.now(), personId);
        Bill funded = billsRepository.save(new Bill(Currency.getInstance("USD"), new BigDecimal("10.00"), false, account));

        mockMvc.perform(MockMvcRequestBuilders.delete("/v1/people/{personId}/accounts/{id}", personId, account.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.exception").value("AccountHasNonZeroBalanceException"));

        assertThat(accountsRepository.getAccountsById(account.getId()).get().getStatus()).isEqualTo(AccountStatus.OPEN);
        assertThat(billsRepository.getBillById(funded.getId())).isPresent();
        assertThat(accountAuditRepository.findByAccountIdOrderByOccurredAtAsc(account.getId())).isEmpty();
    }

    @Test
    void deleteWithWrongPersonIsNotFound() throws Exception {
        UUID personId = UUID.randomUUID();
        Account account = init("Salary", LocalDate.now(), personId);

        mockMvc.perform(MockMvcRequestBuilders.delete("/v1/people/{personId}/accounts/{id}", UUID.randomUUID(), account.getId()))
                .andExpect(status().isNotFound());

        assertThat(accountsRepository.getAccountsById(account.getId()).get().getStatus()).isEqualTo(AccountStatus.OPEN);
    }

    @Test
    void ownershipChangeIsAudited() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();
        LocalDate localDate = LocalDate.now();
        Account account = init("Salary", localDate, personId);

        mockMvc.perform(put("/v1/people/{personId}/accounts/{id}", personId, account.getId())
                .header("X-Actor", "teller-7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AccountRequestDTO("Salary", localDate, newOwner))))
                .andExpect(status().isAccepted());

        assertThat(accountsRepository.getAccountsById(account.getId()).get().getPersonId()).isEqualTo(newOwner);
        List<AccountAuditRecord> audit = accountAuditRepository.findByAccountIdOrderByOccurredAtAsc(account.getId());
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getAction()).isEqualTo(AccountAuditAction.OWNERSHIP_CHANGED);
        assertThat(audit.get(0).getBeforeState()).contains(personId.toString());
        assertThat(audit.get(0).getAfterState()).contains(newOwner.toString());
    }
}
