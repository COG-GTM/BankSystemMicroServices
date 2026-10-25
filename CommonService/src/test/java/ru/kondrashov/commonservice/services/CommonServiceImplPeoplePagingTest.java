package ru.kondrashov.commonservice.services;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import ru.kondrashov.commonservice.controllers.dto.PeoplePageDTO;
import ru.kondrashov.commonservice.controllers.dto.PersonRequestDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CommonServiceImplPeoplePagingTest {

    private static final String PEOPLE_URL = "http://localhost:8082/v1/people";
    private static final String TWO_PEOPLE = "["
            + "{\"id\":\"00000000-0000-0000-0000-000000000001\",\"firstName\":\"Muhammad\",\"lastName\":\"Ali\"},"
            + "{\"id\":\"00000000-0000-0000-0000-000000000002\",\"firstName\":\"George\",\"lastName\":\"Foreman\"}]";

    private MockRestServiceServer server;
    private CommonServiceImpl commonService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        commonService = new CommonServiceImpl(restTemplate, LogManager.getRootLogger());
        ReflectionTestUtils.setField(commonService, "peopleUrl", PEOPLE_URL);
    }

    @Test
    void requestsOnePageAndReadsPagingHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Total-Count", "7");
        headers.set("X-Total-Pages", "4");
        server.expect(requestTo(PEOPLE_URL + "?page=1&size=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(TWO_PEOPLE, MediaType.APPLICATION_JSON).headers(headers));

        PeoplePageDTO page = commonService.getPeople(1, 2);

        server.verify();
        assertThat(page.getPeople()).extracting(PersonRequestDTO::getLastName).containsExactly("Ali", "Foreman");
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotalElements()).isEqualTo(7);
        assertThat(page.getTotalPages()).isEqualTo(4);
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void lastPageHasNoNext() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Total-Count", "4");
        headers.set("X-Total-Pages", "2");
        server.expect(requestTo(PEOPLE_URL + "?page=1&size=2"))
                .andRespond(withSuccess(TWO_PEOPLE, MediaType.APPLICATION_JSON).headers(headers));

        PeoplePageDTO page = commonService.getPeople(1, 2);

        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isTrue();
    }

    @Test
    void maxIntPageHasNoNext() {
        assertThat(new PeoplePageDTO(java.util.Collections.<PersonRequestDTO>emptyList(),
                Integer.MAX_VALUE, 50, 100, 2).hasNext()).isFalse();
    }

    @Test
    void fallsBackWhenPagingHeadersAreMissing() {
        server.expect(requestTo(PEOPLE_URL + "?page=0&size=50"))
                .andRespond(withSuccess(TWO_PEOPLE, MediaType.APPLICATION_JSON));

        PeoplePageDTO page = commonService.getPeople(0, 50);

        assertThat(page.getPeople()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isFalse();
    }
}
