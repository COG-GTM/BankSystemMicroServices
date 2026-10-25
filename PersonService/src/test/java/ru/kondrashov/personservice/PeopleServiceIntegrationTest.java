package ru.kondrashov.personservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.kondrashov.personservice.controllers.dto.PersonRequestDTO;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.repositories.PeopleRepository;

import java.time.LocalDate;
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
public class PeopleServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PeopleRepository peopleRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        peopleRepository.deleteAll();
    }

    private static PersonRequestDTO mikeTyson() {
        return new PersonRequestDTO("Mike", "Tyson", LocalDate.of(1966, 6, 30),
                "+7 1234859520", "miketyson@mail.com");
    }

    private static PersonRequestDTO johnJones() {
        return new PersonRequestDTO("John", "Jones", LocalDate.of(1987, 7, 19),
                "+7 9876543210", "JohnJones@mail.com");
    }

    private Person createPerson(PersonRequestDTO request) throws Exception {
        mockMvc.perform(
                post("/v1/people")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<Person> people = peopleRepository.findAll();
        assertThat(people).hasSize(1);
        return people.get(0);
    }

    private Person reload(UUID id) {
        return peopleRepository.findById(id)
                .orElseThrow(() -> new AssertionError("Person with id = " + id + " is not persisted"));
    }

    private static void assertPersonMatches(Person person, PersonRequestDTO expected) {
        assertThat(person.getFirstName()).isEqualTo(expected.getFirstName());
        assertThat(person.getLastName()).isEqualTo(expected.getLastName());
        assertThat(person.getBirthdate()).isEqualTo(expected.getBirthdate());
        assertThat(person.getPhoneNumber()).isEqualTo(expected.getPhoneNumber());
        assertThat(person.getEmail()).isEqualTo(expected.getEmail());
    }

    @Test
    void createPersistsEveryField() throws Exception {
        Person created = createPerson(mikeTyson());

        assertThat(created.getId()).isNotNull();
        assertPersonMatches(reload(created.getId()), mikeTyson());
    }

    @Test
    void updatePersistsEveryChangedField() throws Exception {
        UUID id = createPerson(mikeTyson()).getId();
        PersonRequestDTO changes = johnJones();

        mockMvc.perform(
                put("/v1/people/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changes)))
                .andExpect(status().isAccepted());

        assertPersonMatches(reload(id), changes);
        assertThat(peopleRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/v1/people/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.firstName").value(changes.getFirstName()))
                .andExpect(jsonPath("$.lastName").value(changes.getLastName()))
                .andExpect(jsonPath("$.birthdate").value(changes.getBirthdate().toString()))
                .andExpect(jsonPath("$.phoneNumber").value(changes.getPhoneNumber()))
                .andExpect(jsonPath("$.email").value(changes.getEmail()));
    }

    @Test
    void updateOfUnknownPersonReturnsNotFoundAndChangesNothing() throws Exception {
        UUID id = createPerson(mikeTyson()).getId();

        mockMvc.perform(
                put("/v1/people/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(johnJones())))
                .andExpect(status().isNotFound());

        assertPersonMatches(reload(id), mikeTyson());
        assertThat(peopleRepository.count()).isEqualTo(1);
    }

    @Test
    void deleteRemovesPerson() throws Exception {
        UUID id = createPerson(mikeTyson()).getId();

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/v1/people/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        assertThat(peopleRepository.findById(id)).isEmpty();
    }
}
