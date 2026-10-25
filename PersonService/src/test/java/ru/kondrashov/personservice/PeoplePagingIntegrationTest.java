package ru.kondrashov.personservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.kondrashov.personservice.controllers.dto.PersonResponseDTO;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.repositories.PeopleRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
public class PeoplePagingIntegrationTest {

    private static final int PEOPLE = 7;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PeopleRepository peopleRepository;

    @BeforeEach
    void seed() {
        peopleRepository.deleteAll();
        String[] lastNames = {"Tyson", "Ali", "Holyfield", "Ali", "Frazier", "Lewis", "Foreman"};
        String[] firstNames = {"Mike", "Muhammad", "Evander", "Laila", "Joe", "Lennox", "George"};
        for (int i = 0; i < PEOPLE; i++) {
            peopleRepository.save(new Person(null, firstNames[i], lastNames[i],
                    LocalDate.of(1960, 1, i + 1), "+7 123456789" + i, "boxer" + i + "@mail.com"));
        }
    }

    @AfterEach
    void cleanUp() {
        peopleRepository.deleteAll();
    }

    @Test
    void returnsOnePageWithPagingHeaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/people").param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", String.valueOf(PEOPLE)))
                .andExpect(header().string("X-Total-Pages", "3"))
                .andExpect(header().string("X-Page", "1"))
                .andExpect(header().string("X-Page-Size", "3"))
                .andReturn();

        assertThat(read(result)).extracting(PersonResponseDTO::getLastName)
                .containsExactly("Frazier", "Holyfield", "Lewis");
    }

    @Test
    void walkingEveryPageYieldsTheFullOrderedListOnce() throws Exception {
        List<UUID> walked = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            walked.addAll(read(mockMvc.perform(get("/v1/people").param("page", String.valueOf(page)).param("size", "3"))
                    .andExpect(status().isOk()).andReturn())
                    .stream().map(PersonResponseDTO::getId).collect(Collectors.toList()));
        }

        List<UUID> expected = peopleRepository.findAll(Sort.by("lastName", "firstName", "id"))
                .stream().map(Person::getId).collect(Collectors.toList());
        assertThat(walked).containsExactlyElementsOf(expected).hasSize(PEOPLE);
    }

    @Test
    void defaultsToFirstPageOfFifty() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/people"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Page", "0"))
                .andExpect(header().string("X-Page-Size", "50"))
                .andExpect(header().string("X-Total-Count", String.valueOf(PEOPLE)))
                .andReturn();

        assertThat(read(result)).hasSize(PEOPLE);
    }

    @Test
    void pageBeyondTheEndIsEmpty() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/people").param("page", "5").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", String.valueOf(PEOPLE)))
                .andReturn();

        assertThat(read(result)).isEmpty();
    }

    @Test
    void rejectsOutOfRangePageAndSize() throws Exception {
        mockMvc.perform(get("/v1/people").param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/people").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/people").param("size", "201")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/people").param("size", "abc")).andExpect(status().isBadRequest());
    }

    private List<PersonResponseDTO> read(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<List<PersonResponseDTO>>() {});
    }
}
