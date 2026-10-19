package ru.kondrashov.commonservice.services;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CommonServiceImplPeoplePagingTest {

    private static final String PEOPLE_URL = "http://localhost:8082/v1/people";

    private static String people(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "{\"firstName\":\"P" + i + "\",\"lastName\":\"L\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Test
    void fetchesEveryPageUntilAShortPage() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        CommonServiceImpl service = new CommonServiceImpl(restTemplate, LogManager.getRootLogger());
        ReflectionTestUtils.setField(service, "peopleUrl", PEOPLE_URL);

        server.expect(requestTo(PEOPLE_URL + "?page=0&size=100")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(people(100), MediaType.APPLICATION_JSON));
        server.expect(requestTo(PEOPLE_URL + "?page=1&size=100")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(people(1), MediaType.APPLICATION_JSON));

        assertThat(service.getPeople()).hasSize(101);
        server.verify();
    }
}
