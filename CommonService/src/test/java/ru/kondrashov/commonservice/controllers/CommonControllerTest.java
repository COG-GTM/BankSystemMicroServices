package ru.kondrashov.commonservice.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.kondrashov.commonservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.AccountResponseDTO;
import ru.kondrashov.commonservice.controllers.dto.BillResponseDTO;
import ru.kondrashov.commonservice.controllers.dto.PersonResponseDTO;
import ru.kondrashov.commonservice.services.CommonService;
import ru.kondrashov.commonservice.services.DownstreamServiceException;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(CommonController.class)
@Import(CommonControllerTest.LoggerConfig.class)
class CommonControllerTest {

    @TestConfiguration
    static class LoggerConfig {
        @Bean
        Logger logger() {
            return LogManager.getLogger(CommonControllerTest.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommonService commonService;

    private final UUID personId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID billId = UUID.randomUUID();

    private static DownstreamServiceException downstream(HttpMethod method, int status) {
        return new DownstreamServiceException(method, "http://downstream/v1", status, "{\"message\":\"internal detail\"}", null);
    }

    private static MockHttpServletRequestBuilder withPerson(MockHttpServletRequestBuilder request) {
        return request
                .param("firstName", "Ada")
                .param("lastName", "Lovelace")
                .param("birthdate", "1990-01-01")
                .param("email", "ada@example.com")
                .param("phoneNumber", "+100000000");
    }

    @Test
    void createPersonSuccessRedirects() throws Exception {
        mockMvc.perform(withPerson(post("/common/v1/create/people")))
                .andExpect(redirectedUrl("/common/v1/info/people"));
    }

    @Test
    void createPersonDownstreamRejectionRendersFormWithError() throws Exception {
        doThrow(downstream(HttpMethod.POST, 400)).when(commonService).createPerson(any(PersonResponseDTO.class));

        mockMvc.perform(withPerson(post("/common/v1/create/people")))
                .andExpect(view().name("people/new"))
                .andExpect(content().string(containsString("Could not create person: downstream service responded 400 Bad Request.")))
                .andExpect(content().string(containsString("value=\"ada@example.com\"")))
                .andExpect(content().string(not(containsString("internal detail"))));
    }

    @Test
    void createPersonNoLongerSwallowsUnexpectedExceptions() {
        doThrow(new NullPointerException("bug")).when(commonService).createPerson(any(PersonResponseDTO.class));

        assertThatThrownBy(() -> mockMvc.perform(withPerson(post("/common/v1/create/people"))))
                .hasRootCauseInstanceOf(NullPointerException.class);
    }

    @Test
    void createAccountDownstreamRejectionRendersFormWithError() throws Exception {
        doThrow(downstream(HttpMethod.POST, 500)).when(commonService).createAccount(eq(personId), any(AccountResponseDTO.class));

        mockMvc.perform(post("/common/v1/create/people/{id}/accounts", personId)
                        .param("name", "Savings").param("creationDate", "2024-01-01"))
                .andExpect(view().name("accounts/new"))
                .andExpect(content().string(containsString("Could not create account: downstream service responded 500 Internal Server Error.")))
                .andExpect(content().string(containsString("/common/v1/create/people/" + personId + "/accounts")));
    }

    @Test
    void createBillDownstreamRejectionRendersFormWithError() throws Exception {
        AccountRequestDTO account = new AccountRequestDTO();
        account.setId(accountId);
        account.setPersonId(personId);
        when(commonService.getAccountsByPerson(personId)).thenReturn(Collections.singletonList(account));
        doThrow(downstream(HttpMethod.POST, 400)).when(commonService).createBill(eq(personId), eq(accountId), any(BillResponseDTO.class));

        mockMvc.perform(post("/common/v1/create/people/{p}/accounts/{a}/bills", personId, accountId)
                        .param("currency", "USD").param("isOverdraft", "0"))
                .andExpect(view().name("/bills/new"))
                .andExpect(content().string(containsString("Could not create bill: downstream service responded 400 Bad Request.")))
                .andExpect(content().string(containsString("/common/v1/create/people/" + personId + "/accounts/" + accountId + "/bills")))
                .andExpect(content().string(containsString(">USD<")));
    }

    @Test
    void updatePersonDownstreamRejectionRendersFormWithError() throws Exception {
        doThrow(downstream(HttpMethod.PUT, 404)).when(commonService).updatePerson(eq(personId), any(PersonResponseDTO.class));

        mockMvc.perform(withPerson(put("/common/v1/update/people/{id}", personId)))
                .andExpect(view().name("people/edit"))
                .andExpect(content().string(containsString("Could not update person: downstream service responded 404 Not Found.")))
                .andExpect(content().string(containsString("/common/v1/update/people/" + personId)));
    }

    @Test
    void deleteDownstreamFailureRendersErrorPage() throws Exception {
        doThrow(downstream(HttpMethod.DELETE, 409)).when(commonService).deleteBill(personId, accountId, billId);

        mockMvc.perform(delete("/common/v1/delete/people/{p}/accounts/{a}/bills/{b}", personId, accountId, billId))
                .andExpect(status().isBadGateway())
                .andExpect(view().name("error/downstream"))
                .andExpect(content().string(containsString("Request failed: downstream service responded 409 Conflict.")))
                .andExpect(content().string(not(containsString("internal detail"))));
    }
}
