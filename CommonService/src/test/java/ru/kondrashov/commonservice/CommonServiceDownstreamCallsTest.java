package ru.kondrashov.commonservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import ru.kondrashov.commonservice.controllers.dto.PersonResponseDTO;
import ru.kondrashov.commonservice.services.CommonService;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
class CommonServiceDownstreamCallsTest {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CommonService commonService;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void personPiiIsSentOverHttpsWithServiceToken() throws Exception {
        server.expect(requestTo("https://localhost:8082/v1/people"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-service-token"))
                .andRespond(withStatus(HttpStatus.CREATED));

        PersonResponseDTO person = new PersonResponseDTO();
        person.setFirstName("Jane");
        person.setLastName("Doe");
        person.setBirthdate(LocalDate.of(1990, 1, 1));
        person.setEmail("jane@example.com");
        person.setPhoneNumber("+1 5550100");
        commonService.createPerson(person);

        server.verify();
    }

    @Test
    void deletesCarryServiceToken() {
        UUID personId = UUID.randomUUID();
        server.expect(requestTo("https://localhost:8081/v1/people/" + personId + "/accounts"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-service-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://localhost:8082/v1/people/" + personId))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-service-token"))
                .andRespond(withSuccess());

        commonService.deletePerson(personId);

        server.verify();
    }
}
