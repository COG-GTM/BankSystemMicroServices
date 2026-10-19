package ru.kondrashov.personservice;

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
import ru.kondrashov.personservice.audit.AuditActorResolver;
import ru.kondrashov.personservice.audit.AuditOperation;
import ru.kondrashov.personservice.audit.AuditOutcome;
import ru.kondrashov.personservice.audit.PersonAuditEvent;
import ru.kondrashov.personservice.audit.PersonAuditRepository;
import ru.kondrashov.personservice.audit.PersonAuditService;
import ru.kondrashov.personservice.controllers.dto.PersonRequestDTO;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.repositories.PeopleRepository;
import ru.kondrashov.personservice.services.PeopleService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
public class PersonAuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PeopleRepository peopleRepository;

    @Autowired
    private PersonAuditRepository auditRepository;

    @Autowired
    private PeopleService peopleService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void createUpdateDeleteLeaveAttributableAuditTrail() throws Exception {
        String email = "audit-" + UUID.randomUUID() + "@mail.com";
        PersonRequestDTO created = new PersonRequestDTO("Ada", "Lovelace", LocalDate.of(1815, 12, 10), "+44 1000000000", email);

        mockMvc.perform(post("/v1/people")
                        .header(AuditActorResolver.ACTOR_HEADER, "clerk-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(created)))
                .andExpect(status().isCreated());

        UUID id = peopleRepository.findAll().stream()
                .filter(p -> email.equals(p.getEmail()))
                .map(Person::getId)
                .findFirst()
                .orElseThrow(IllegalStateException::new);

        PersonRequestDTO changed = new PersonRequestDTO("Ada", "King", LocalDate.of(1815, 12, 10), "+44 2000000000", email);
        mockMvc.perform(put("/v1/people/{id}", id)
                        .header(AuditActorResolver.ACTOR_HEADER, "clerk-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changed)))
                .andExpect(status().isAccepted());

        mockMvc.perform(delete("/v1/people/{id}", id)
                        .header(AuditActorResolver.ACTOR_HEADER, "supervisor-3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        List<PersonAuditEvent> events = auditRepository.findByPersonIdOrderByOccurredAtAsc(id);

        assertThat(events).extracting(PersonAuditEvent::getOperation)
                .containsExactly(AuditOperation.CREATE, AuditOperation.UPDATE, AuditOperation.DELETE);
        assertThat(events).extracting(PersonAuditEvent::getActor)
                .containsExactly("clerk-1", "clerk-2", "supervisor-3");
        assertThat(events).extracting(PersonAuditEvent::getOutcome).containsOnly(AuditOutcome.SUCCESS);
        assertThat(events).allSatisfy(e -> assertThat(e.getOccurredAt()).isNotNull());
        assertThat(events.get(0).getChangedFields()).isEqualTo("firstName,lastName,birthdate,phoneNumber,email");
        assertThat(events.get(1).getChangedFields()).isEqualTo("lastName,phoneNumber");
        assertThat(events.get(2).getChangedFields()).isEqualTo("firstName,lastName,birthdate,phoneNumber,email");
        assertThat(events).allSatisfy(e -> assertThat(e.getChangedFields()).doesNotContain(email).doesNotContain("King"));
        assertThat(peopleRepository.getPersonById(id)).isEmpty();
    }

    @Test
    void failedDeleteIsAuditedDespiteRollback() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(delete("/v1/people/{id}", missing)
                        .header(AuditActorResolver.ACTOR_HEADER, "clerk-4")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        List<PersonAuditEvent> events = auditRepository.findByPersonIdOrderByOccurredAtAsc(missing);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOperation()).isEqualTo(AuditOperation.DELETE);
        assertThat(events.get(0).getActor()).isEqualTo("clerk-4");
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(events.get(0).getFailureReason()).isEqualTo("PersonNotFoundException");
    }

    @Test
    void rollbackAfterSuccessfulCreateReplacesSuccessWithFailure() {
        Person person = new Person();
        person.setFirstName("Grace");
        person.setLastName("Hopper");
        person.setBirthdate(LocalDate.of(1906, 12, 9));
        person.setEmail("audit-" + UUID.randomUUID() + "@mail.com");
        UUID[] id = new UUID[1];

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(tx -> {
            peopleService.save(person);
            id[0] = person.getId();
            throw new IllegalStateException("commit-time failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(peopleRepository.getPersonById(id[0])).isEmpty();
        List<PersonAuditEvent> events = auditRepository.findByPersonIdOrderByOccurredAtAsc(id[0]);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOperation()).isEqualTo(AuditOperation.CREATE);
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(events.get(0).getFailureReason()).isEqualTo(PersonAuditService.ROLLED_BACK_AFTER_SUCCESS);
        assertThat(events.get(0).getActor()).isEqualTo(AuditActorResolver.ANONYMOUS);
    }
}
