package ru.kondrashov.accountservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.kondrashov.accountservice.audit.AccountAuditEvent;
import ru.kondrashov.accountservice.audit.AccountAuditRepository;
import ru.kondrashov.accountservice.audit.AccountAuditService;
import ru.kondrashov.accountservice.audit.ActorSource;
import ru.kondrashov.accountservice.audit.AuditAction;
import ru.kondrashov.accountservice.audit.AuditActorResolver;
import ru.kondrashov.accountservice.audit.AuditOutcome;
import ru.kondrashov.accountservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
class AccountAuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountsRepository accountsRepository;

    @Autowired
    private AccountAuditRepository auditRepository;

    @Autowired
    private AccountsService accountsService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void createUpdateDelete_writeAttributedAuditTrail() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();

        mockMvc.perform(post("/v1/people/{personId}/accounts", owner)
                .header(AuditActorResolver.ACTOR_HEADER, "teller-7")
                .header(AuditActorResolver.REQUEST_ID_HEADER, "req-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AccountRequestDTO("Salary", LocalDate.now(), owner))))
                .andExpect(status().isCreated());
        Account account = accountsRepository.getAccountsByPersonId(owner).stream().findFirst().get();

        mockMvc.perform(put("/v1/people/{personId}/accounts/{id}", owner, account.getId())
                .header(AuditActorResolver.ACTOR_HEADER, "teller-7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AccountRequestDTO("Savings", LocalDate.now(), newOwner))))
                .andExpect(status().isAccepted());

        mockMvc.perform(delete("/v1/people/{personId}/accounts/{id}", newOwner, account.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        List<AccountAuditEvent> trail = auditRepository.findByAccountIdOrderByOccurredAtAsc(account.getId());
        assertThat(trail).extracting(AccountAuditEvent::getAction)
                .containsExactly(AuditAction.CREATE, AuditAction.UPDATE, AuditAction.DELETE);
        assertThat(trail).extracting(AccountAuditEvent::getOutcome).containsOnly(AuditOutcome.SUCCESS);
        assertThat(trail).allSatisfy(e -> assertThat(e.getOccurredAt()).isNotNull());

        AccountAuditEvent create = trail.get(0);
        assertThat(create.getActor()).isEqualTo("teller-7");
        assertThat(create.getActorSource()).isEqualTo(ActorSource.HEADER);
        assertThat(create.getRequestId()).isEqualTo("req-create");
        assertThat(create.getClientAddress()).isNotBlank();
        assertThat(create.getPersonId()).isEqualTo(owner);

        AccountAuditEvent update = trail.get(1);
        JsonNode before = objectMapper.readTree(update.getBeforeState());
        JsonNode after = objectMapper.readTree(update.getAfterState());
        assertThat(before.get("personId").asText()).isEqualTo(owner.toString());
        assertThat(before.get("name").asText()).isEqualTo("Salary");
        assertThat(after.get("personId").asText()).isEqualTo(newOwner.toString());
        assertThat(after.get("name").asText()).isEqualTo("Savings");

        AccountAuditEvent deletion = trail.get(2);
        assertThat(deletion.getActor()).isEqualTo("anonymous");
        assertThat(deletion.getActorSource()).isEqualTo(ActorSource.NONE);
        assertThat(objectMapper.readTree(deletion.getBeforeState()).get("name").asText()).isEqualTo("Savings");
        assertThat(deletion.getAfterState()).isNull();
    }

    @Test
    void failedDelete_isRecordedEvenThoughRequestRollsBack() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(delete("/v1/people/{personId}/accounts/{id}", UUID.randomUUID(), missing)
                .header(AuditActorResolver.ACTOR_HEADER, "teller-9")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        List<AccountAuditEvent> trail = auditRepository.findByAccountIdOrderByOccurredAtAsc(missing);
        assertThat(trail).hasSize(1);
        AccountAuditEvent failure = trail.get(0);
        assertThat(failure.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(failure.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(failure.getActor()).isEqualTo("teller-9");
        assertThat(failure.getFailureReason()).startsWith("AccountNotFoundException");
    }

    @Test
    void enclosingRollbackAfterSuccessfulCreate_isRecordedAsFailure() {
        Account account = new Account("Salary", LocalDate.now(), UUID.randomUUID());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            accountsService.save(account);
            status.setRollbackOnly();
        });

        assertThat(account.getId()).isNotNull();
        assertThat(accountsRepository.getAccountsById(account.getId())).isEmpty();
        List<AccountAuditEvent> trail = auditRepository.findByAccountIdOrderByOccurredAtAsc(account.getId());
        assertThat(trail).hasSize(1);
        assertThat(trail.get(0).getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(trail.get(0).getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(trail.get(0).getFailureReason()).isEqualTo(AccountAuditService.ROLLED_BACK_REASON);
        assertThat(trail.get(0).getAfterState()).contains(account.getId().toString());
    }
}
