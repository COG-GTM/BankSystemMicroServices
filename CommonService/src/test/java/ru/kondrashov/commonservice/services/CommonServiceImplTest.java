package ru.kondrashov.commonservice.services;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class CommonServiceImplTest {

    private MockRestServiceServer server;
    private CommonServiceImpl commonService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        commonService = new CommonServiceImpl(restTemplate, LogManager.getRootLogger());
        ReflectionTestUtils.setField(commonService, "personUrl", "http://people/v1/people/%s");
        ReflectionTestUtils.setField(commonService, "accountsUrl", "http://accounts/v1/people/%s/accounts");
        ReflectionTestUtils.setField(commonService, "accountUrl", "http://accounts/v1/people/%s/accounts/%s");
    }

    @Test
    void deletePersonDeletesAllAccountsInOneCallThenPerson() {
        UUID personId = UUID.randomUUID();
        server.expect(ExpectedCount.once(), requestTo("http://accounts/v1/people/" + personId + "/accounts"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(ExpectedCount.once(), requestTo("http://people/v1/people/" + personId))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        commonService.deletePerson(personId);

        server.verify();
    }

    @Test
    void deletePersonKeepsPersonWhenAccountDeletionFails() {
        UUID personId = UUID.randomUUID();
        server.expect(ExpectedCount.once(), requestTo("http://accounts/v1/people/" + personId + "/accounts"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> commonService.deletePerson(personId))
                .isInstanceOf(HttpServerErrorException.class);

        server.verify();
    }
}
