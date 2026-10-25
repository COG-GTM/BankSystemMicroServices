package ru.kondrashov.commonservice.controllers;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import ru.kondrashov.commonservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.BillRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.PersonResponseDTO;
import ru.kondrashov.commonservice.exceptions.DownstreamServiceException;
import ru.kondrashov.commonservice.services.CommonService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Currency;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(CommonController.class)
class CommonControllerTest {

    private static final UUID PERSON_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BILL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommonService commonService;

    @MockBean
    private Logger logger;

    private static DownstreamServiceException conflict(String url, String body) {
        return new DownstreamServiceException(HttpMethod.POST, url, HttpStatus.CONFLICT, body, null);
    }

    private static AccountRequestDTO account() {
        AccountRequestDTO account = new AccountRequestDTO();
        account.setId(ACCOUNT_ID);
        account.setPersonId(PERSON_ID);
        account.setName("Main");
        return account;
    }

    @Test
    void createBillFailureLogsAndReRendersFunctionalForm() throws Exception {
        when(commonService.getAccountsByPerson(PERSON_ID)).thenReturn(Collections.singletonList(account()));
        doThrow(conflict("http://accounts/bills", "{\"message\":\"Bill in USD already exists\"}"))
                .when(commonService).createBill(eq(PERSON_ID), eq(ACCOUNT_ID), any());

        mockMvc.perform(post("/common/v1/create/people/{p}/accounts/{a}/bills", PERSON_ID, ACCOUNT_ID).param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(view().name("/bills/new"))
                .andExpect(model().attribute("currencies", Currency.getAvailableCurrencies()))
                .andExpect(model().attribute("personId", PERSON_ID))
                .andExpect(model().attribute("accountId", ACCOUNT_ID))
                .andExpect(model().attributeHasErrors("bill"))
                .andExpect(content().string(containsString(
                        "action=\"/common/v1/create/people/" + PERSON_ID + "/accounts/" + ACCOUNT_ID + "/bills\"")))
                .andExpect(content().string(containsString("<option value=\"USD\" selected=\"selected\">USD</option>")))
                .andExpect(content().string(containsString(
                        "Could not create bill: the downstream service responded 409 Conflict: Bill in USD already exists")));

        verify(logger).error(contains("Failed to create bill: POST http://accounts/bills failed: 409 Conflict"), any(Throwable.class));
    }

    @Test
    void createBillValidationErrorKeepsFormFunctional() throws Exception {
        mockMvc.perform(post("/common/v1/create/people/{p}/accounts/{a}/bills", PERSON_ID, ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("/bills/new"))
                .andExpect(model().attributeHasFieldErrors("bill", "currency"))
                .andExpect(model().attribute("personId", PERSON_ID))
                .andExpect(model().attribute("accountId", ACCOUNT_ID))
                .andExpect(content().string(containsString("<option value=\"EUR\">EUR</option>")));

        verify(commonService, never()).createBill(any(), any(), any());
    }

    @Test
    void createBillSuccessStillRedirects() throws Exception {
        when(commonService.getAccountsByPerson(PERSON_ID)).thenReturn(Collections.singletonList(account()));

        mockMvc.perform(post("/common/v1/create/people/{p}/accounts/{a}/bills", PERSON_ID, ACCOUNT_ID).param("currency", "USD"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/common/v1/info/people/" + PERSON_ID + "/accounts/" + ACCOUNT_ID));
    }

    @Test
    void createAccountFailureKeepsPersonIdInFormAction() throws Exception {
        doThrow(conflict("http://accounts", "")).when(commonService).createAccount(eq(PERSON_ID), any());

        mockMvc.perform(post("/common/v1/create/people/{id}/accounts", PERSON_ID).param("name", "Savings").param("creationDate", "2024-01-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/new"))
                .andExpect(model().attribute("id", PERSON_ID))
                .andExpect(content().string(containsString("action=\"/common/v1/create/people/" + PERSON_ID + "/accounts\"")))
                .andExpect(content().string(not(containsString("/people/null/"))))
                .andExpect(content().string(containsString("Could not create account: the downstream service responded 409 Conflict")))
                .andExpect(content().string(containsString("value=\"Savings\"")));

        verify(logger).error(contains("Failed to create account"), any(Throwable.class));
    }

    @Test
    void createAccountSuccessStillRedirects() throws Exception {
        mockMvc.perform(post("/common/v1/create/people/{id}/accounts", PERSON_ID).param("name", "Savings"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/common/v1/info/people/" + PERSON_ID));
    }

    @Test
    void createPersonUnreachableServiceShowsError() throws Exception {
        doThrow(new DownstreamServiceException(HttpMethod.POST, "http://people", null, null, new ResourceAccessException("Connection refused")))
                .when(commonService).createPerson(any(PersonResponseDTO.class));

        mockMvc.perform(post("/common/v1/create/people")
                        .param("firstName", "Ada").param("lastName", "Lovelace").param("birthdate", "1990-01-01")
                        .param("email", "ada@example.com").param("phoneNumber", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("people/new"))
                .andExpect(content().string(containsString("Could not create person: the downstream service is unavailable")));

        verify(logger).error(contains("Failed to create person: POST http://people failed: Connection refused"), any(Throwable.class));
    }

    @Test
    void updateBillFailureReRendersEditFormWithIds() throws Exception {
        when(commonService.getAccountsByPerson(PERSON_ID)).thenReturn(Collections.singletonList(account()));
        doThrow(new DownstreamServiceException(HttpMethod.PUT, "http://bill", HttpStatus.NOT_FOUND, "{\"message\":\"Bill not found\"}", null))
                .when(commonService).updateBill(eq(PERSON_ID), eq(ACCOUNT_ID), eq(BILL_ID), any());

        mockMvc.perform(put("/common/v1/update/people/{p}/accounts/{a}/bills/{b}", PERSON_ID, ACCOUNT_ID, BILL_ID)
                        .param("currency", "EUR").param("amount", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("bills/edit"))
                .andExpect(content().string(containsString(
                        "/common/v1/update/people/" + PERSON_ID + "/accounts/" + ACCOUNT_ID + "/bills/" + BILL_ID + "\"")))
                .andExpect(content().string(containsString("Could not update bill: the downstream service responded 404 Not Found: Bill not found")));

        verify(logger).error(contains("Failed to update bill"), any(Throwable.class));
    }

    @Test
    void updateAccountFailureReRendersEditFormWithIds() throws Exception {
        doThrow(new DownstreamServiceException(HttpMethod.PUT, "http://account", HttpStatus.INTERNAL_SERVER_ERROR, "", null))
                .when(commonService).updateAccount(eq(PERSON_ID), eq(ACCOUNT_ID), any());

        mockMvc.perform(put("/common/v1/update/people/{p}/accounts/{a}", PERSON_ID, ACCOUNT_ID).param("name", "Renamed"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/edit"))
                .andExpect(content().string(containsString("/common/v1/update/people/" + PERSON_ID + "/accounts/" + ACCOUNT_ID + "\"")))
                .andExpect(content().string(containsString("Could not update account: the downstream service responded 500 Internal Server Error")));
    }

    @Test
    void updatePersonFailureKeepsIdInFormAction() throws Exception {
        doThrow(new DownstreamServiceException(HttpMethod.PUT, "http://person", HttpStatus.NOT_FOUND, "", null))
                .when(commonService).updatePerson(eq(PERSON_ID), any());

        mockMvc.perform(put("/common/v1/update/people/{id}", PERSON_ID)
                        .param("firstName", "Ada").param("lastName", "Lovelace").param("birthdate", "1990-01-01")
                        .param("email", "ada@example.com").param("phoneNumber", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("people/edit"))
                .andExpect(content().string(containsString("action=\"/common/v1/update/people/" + PERSON_ID + "\"")))
                .andExpect(content().string(containsString("Could not update person: the downstream service responded 404 Not Found")));
    }

    @Test
    void deleteFailureRendersBadGatewayErrorPage() throws Exception {
        doThrow(new DownstreamServiceException(HttpMethod.DELETE, "http://bill", HttpStatus.NOT_FOUND, "{\"message\":\"Bill not found\"}", null))
                .when(commonService).deleteBill(PERSON_ID, ACCOUNT_ID, BILL_ID);

        mockMvc.perform(delete("/common/v1/delete/people/{p}/accounts/{a}/bills/{b}", PERSON_ID, ACCOUNT_ID, BILL_ID))
                .andExpect(status().isBadGateway())
                .andExpect(view().name("error/downstream"))
                .andExpect(content().string(containsString("Request failed: the downstream service responded 404 Not Found: Bill not found")));

        verify(logger).error(contains("Downstream request failed: DELETE http://bill failed: 404 Not Found"), any(Throwable.class));
    }

    @Test
    void deleteSuccessStillRedirects() throws Exception {
        mockMvc.perform(delete("/common/v1/delete/people/{p}/accounts/{a}/bills/{b}", PERSON_ID, ACCOUNT_ID, BILL_ID))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/common/v1/info/people/" + PERSON_ID + "/accounts/" + ACCOUNT_ID));
    }
}
