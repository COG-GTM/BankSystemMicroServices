package ru.kondrashov.personservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.kondrashov.personservice.controllers.dto.PersonRequestDTO;
import ru.kondrashov.personservice.services.PeopleService;
import ru.kondrashov.personservice.utils.PeopleMapping;

import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PeopleController.class)
@ActiveProfiles("test")
class PeopleControllerSecurityTest {

    private static final UUID ID = UUID.fromString("c5a6757f-fec4-41f3-a6a6-eb03d70c0d6f");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PeopleService peopleService;

    @MockBean
    private PeopleMapping peopleMapping;

    @MockBean
    private Logger logger;

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.with(httpBasic("test-admin", "test-admin-password-0001"));
    }

    private MockHttpServletRequestBuilder writer(MockHttpServletRequestBuilder request) {
        return request.with(httpBasic("test-writer", "test-writer-password-0001"));
    }

    private MockHttpServletRequestBuilder reader(MockHttpServletRequestBuilder request) {
        return request.with(httpBasic("test-reader", "test-reader-password-0001"));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) throws Exception {
        PersonRequestDTO body = new PersonRequestDTO("Mike", "Tyson", LocalDate.of(1966, 6, 30), "+7 1234859520", "miketyson@mail.com");
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    @Test
    void anonymousCallersAreRejectedOnEveryEndpoint() throws Exception {
        mockMvc.perform(get("/v1/people")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/people/{id}", ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/v1/people"))).andExpect(status().isUnauthorized());
        mockMvc.perform(json(put("/v1/people/{id}", ID))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/people/{id}", ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v2/api-docs")).andExpect(status().isUnauthorized());

        verifyNoInteractions(peopleService);
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(get("/v1/people").with(httpBasic("test-admin", "not-the-password-000")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/people/{id}", ID).with(httpBasic("unknown", "test-admin-password-0001")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(peopleService);
    }

    @Test
    void readerCanOnlyReadSinglePerson() throws Exception {
        mockMvc.perform(reader(get("/v1/people/{id}", ID))).andExpect(status().isOk());
        verify(peopleService).get(ID);

        mockMvc.perform(reader(get("/v1/people"))).andExpect(status().isForbidden());
        mockMvc.perform(reader(json(post("/v1/people")))).andExpect(status().isForbidden());
        mockMvc.perform(reader(json(put("/v1/people/{id}", ID)))).andExpect(status().isForbidden());
        mockMvc.perform(reader(delete("/v1/people/{id}", ID))).andExpect(status().isForbidden());
    }

    @Test
    void writerCanCreateAndUpdateButNotListOrDelete() throws Exception {
        mockMvc.perform(writer(json(post("/v1/people")))).andExpect(status().isCreated());
        mockMvc.perform(writer(json(put("/v1/people/{id}", ID)))).andExpect(status().isAccepted());
        verify(peopleService).update(eq(ID), any());

        mockMvc.perform(writer(get("/v1/people"))).andExpect(status().isForbidden());
        mockMvc.perform(writer(delete("/v1/people/{id}", ID))).andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAndDelete() throws Exception {
        mockMvc.perform(admin(delete("/v1/people/{id}", ID))).andExpect(status().isNoContent());
        verify(peopleService).delete(ID);

        when(peopleService.getAll(any())).thenReturn(Collections.emptyList());
        mockMvc.perform(admin(get("/v1/people"))).andExpect(status().isOk());
    }

    @Test
    void listIsPagedWithBoundedSize() throws Exception {
        when(peopleService.getAll(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(admin(get("/v1/people"))).andExpect(status().isOk());
        mockMvc.perform(admin(get("/v1/people").param("page", "3").param("size", "100"))).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(peopleService, org.mockito.Mockito.times(2)).getAll(pages.capture());
        assertThat(pages.getAllValues().get(0).getPageNumber()).isZero();
        assertThat(pages.getAllValues().get(0).getPageSize()).isEqualTo(PeopleController.DEFAULT_PAGE_SIZE);
        assertThat(pages.getAllValues().get(0).getSort()).isEqualTo(Sort.by("lastName", "firstName", "id"));
        assertThat(pages.getAllValues().get(1).getPageNumber()).isEqualTo(3);
        assertThat(pages.getAllValues().get(1).getPageSize()).isEqualTo(PeopleController.MAX_PAGE_SIZE);

        mockMvc.perform(admin(get("/v1/people").param("size", "101"))).andExpect(status().isBadRequest());
        mockMvc.perform(admin(get("/v1/people").param("size", "0"))).andExpect(status().isBadRequest());
        mockMvc.perform(admin(get("/v1/people").param("page", "-1"))).andExpect(status().isBadRequest());
    }
}
