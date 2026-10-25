package ru.kondrashov.commonservice.services;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import ru.kondrashov.commonservice.controllers.dto.PersonRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.PersonResponseDTO;

import javax.validation.Validation;
import javax.validation.Validator;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CommonServicePersonContractTest {

    private static final String PEOPLE_URL = "http://person-service/v1/people";
    private static final String PERSON_URL = "http://person-service/v1/people/%s";

    private MockRestServiceServer server;
    private CommonServiceImpl commonService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder()
                .messageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
                .build();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        commonService = new CommonServiceImpl(restTemplate, LogManager.getRootLogger());
        ReflectionTestUtils.setField(commonService, "peopleUrl", PEOPLE_URL);
        ReflectionTestUtils.setField(commonService, "personUrl", PERSON_URL);
    }

    @Test
    void createPersonSendsPersonRequestBody() throws Exception {
        server.expect(requestTo(PEOPLE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.firstName").value("Mike"))
                .andExpect(jsonPath("$.lastName").value("Tyson"))
                .andExpect(jsonPath("$.birthdate").value("1966-06-30"))
                .andExpect(jsonPath("$.phoneNumber").value("+7 1234859520"))
                .andExpect(jsonPath("$.email").value("miketyson@mail.com"))
                .andRespond(withStatus(HttpStatus.CREATED));

        commonService.createPerson(validRequest());

        server.verify();
    }

    @Test
    void updatePersonSendsPersonRequestBody() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(String.format(PERSON_URL, id)))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.firstName").value("Mike"))
                .andExpect(jsonPath("$.birthdate").value("1966-06-30"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        commonService.updatePerson(id, validRequest());

        server.verify();
    }

    @Test
    void getPeopleReadsPersonResponseBody() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(PEOPLE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"" + id + "\",\"firstName\":\"Mike\",\"lastName\":\"Tyson\","
                        + "\"birthdate\":\"1966-06-30\",\"phoneNumber\":\"+7 1234859520\",\"email\":\"miketyson@mail.com\"}]",
                        MediaType.APPLICATION_JSON));

        Collection<PersonResponseDTO> people = commonService.getPeople();

        assertThat(people).singleElement().satisfies(person -> {
            assertThat(person.getId()).isEqualTo(id);
            assertThat(person.getFirstName()).isEqualTo("Mike");
            assertThat(person.getBirthdate()).isEqualTo(LocalDate.of(1966, 6, 30));
            assertThat(person.getEmail()).isEqualTo("miketyson@mail.com");
        });
        server.verify();
    }

    @Test
    void onlyPersonRequestCarriesValidationRules() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(validRequest())).isEmpty();
        assertThat(validator.validate(new PersonRequestDTO()))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("firstName", "lastName", "birthdate", "phoneNumber", "email");
        assertThat(validator.validate(new PersonResponseDTO())).isEmpty();
    }

    private PersonRequestDTO validRequest() {
        PersonRequestDTO person = new PersonRequestDTO();
        person.setFirstName("Mike");
        person.setLastName("Tyson");
        person.setBirthdate(LocalDate.of(1966, 6, 30));
        person.setPhoneNumber("+7 1234859520");
        person.setEmail("miketyson@mail.com");
        return person;
    }
}
