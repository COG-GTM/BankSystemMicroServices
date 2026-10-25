package ru.kondrashov.commonservice.controllers;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import ru.kondrashov.commonservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.BillRequestDTO;
import ru.kondrashov.commonservice.exceptions.ResourceNotFoundException;
import ru.kondrashov.commonservice.services.CommonService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(CommonController.class)
class CommonControllerTest {

    private static final UUID PERSON_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID BILL_ID = UUID.randomUUID();
    private static final UUID UNKNOWN_ID = UUID.randomUUID();

    private static final String ACCOUNT_BASE = "/common/v1/%s/people/" + PERSON_ID + "/accounts/";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommonService commonService;

    @MockBean
    private Logger logger;

    private AccountRequestDTO account;
    private BillRequestDTO bill;

    @BeforeEach
    void setUp() {
        account = new AccountRequestDTO();
        account.setId(ACCOUNT_ID);
        account.setName("Main");
        account.setCreationDate(LocalDate.of(2021, 1, 1));
        account.setPersonId(PERSON_ID);

        bill = new BillRequestDTO();
        bill.setId(BILL_ID);
        bill.setCurrency(Currency.getInstance("USD"));
        bill.setAmount(BigDecimal.TEN);
        bill.isOverdraft("false");
        bill.setAccount(account);

        when(commonService.getAccountsByPerson(PERSON_ID)).thenReturn(Collections.singletonList(account));
        when(commonService.getBillsByAccountId(PERSON_ID, ACCOUNT_ID)).thenReturn(Collections.singletonList(bill));
        when(commonService.getBillsByAccountId(PERSON_ID, UNKNOWN_ID)).thenReturn(Collections.emptyList());
        when(commonService.getFinancialTransactionsByBillId(any(), any(), any())).thenReturn(Collections.emptyList());
    }

    @Test
    void getAccountRendersKnownAccount() throws Exception {
        mockMvc.perform(get(String.format(ACCOUNT_BASE, "info") + ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/account"))
                .andExpect(model().attribute("account", account));
    }

    @Test
    void getAccountWithUnknownIdIsNotFound() throws Exception {
        expectAccountNotFound(get(String.format(ACCOUNT_BASE, "info") + UNKNOWN_ID));
    }

    @Test
    void getBillRendersKnownBill() throws Exception {
        mockMvc.perform(get(String.format(ACCOUNT_BASE, "info") + ACCOUNT_ID + "/bills/" + BILL_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("bills/bill"))
                .andExpect(model().attribute("bill", bill));
    }

    @Test
    void getBillWithUnknownIdIsNotFound() throws Exception {
        expectBillNotFound(get(String.format(ACCOUNT_BASE, "info") + ACCOUNT_ID + "/bills/" + UNKNOWN_ID), ACCOUNT_ID);
    }

    @Test
    void editAccountWithUnknownIdIsNotFound() throws Exception {
        expectAccountNotFound(get(String.format(ACCOUNT_BASE, "update") + UNKNOWN_ID + "/edit"));
    }

    @Test
    void editAccountRendersKnownAccount() throws Exception {
        mockMvc.perform(get(String.format(ACCOUNT_BASE, "update") + ACCOUNT_ID + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/edit"))
                .andExpect(model().attribute("account", account));
    }

    @Test
    void editBillWithUnknownIdIsNotFound() throws Exception {
        expectBillNotFound(get(String.format(ACCOUNT_BASE, "update") + ACCOUNT_ID + "/bills/" + UNKNOWN_ID + "/edit"), ACCOUNT_ID);
    }

    @Test
    void editBillWithUnknownAccountIsNotFound() throws Exception {
        expectBillNotFound(get(String.format(ACCOUNT_BASE, "update") + UNKNOWN_ID + "/bills/" + BILL_ID + "/edit"), UNKNOWN_ID);
    }

    @Test
    void editBillRendersKnownBill() throws Exception {
        mockMvc.perform(get(String.format(ACCOUNT_BASE, "update") + ACCOUNT_ID + "/bills/" + BILL_ID + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("bills/edit"))
                .andExpect(model().attribute("bill", bill));
    }

    @Test
    void createBillWithUnknownAccountIsNotFound() throws Exception {
        expectAccountNotFound(post(String.format(ACCOUNT_BASE, "create") + UNKNOWN_ID + "/bills").param("currency", "USD"));
        verify(commonService, never()).createBill(any(), any(), any());
    }

    @Test
    void createBillForKnownAccountRedirectsToAccount() throws Exception {
        mockMvc.perform(post(String.format(ACCOUNT_BASE, "create") + ACCOUNT_ID + "/bills").param("currency", "USD"))
                .andExpect(redirectedUrl("/common/v1/info/people/" + PERSON_ID + "/accounts/" + ACCOUNT_ID));
        verify(commonService).createBill(eq(PERSON_ID), eq(ACCOUNT_ID), any());
    }

    @Test
    void updateBillWithUnknownAccountIsNotFound() throws Exception {
        expectAccountNotFound(put(String.format(ACCOUNT_BASE, "update") + UNKNOWN_ID + "/bills/" + BILL_ID).param("currency", "USD"));
        verify(commonService, never()).updateBill(any(), any(), any(), any());
    }

    @Test
    void updateBillForKnownAccountRedirectsToAccount() throws Exception {
        mockMvc.perform(put(String.format(ACCOUNT_BASE, "update") + ACCOUNT_ID + "/bills/" + BILL_ID).param("currency", "USD"))
                .andExpect(redirectedUrl("/common/v1/info/people/" + PERSON_ID + "/accounts/" + ACCOUNT_ID));
        verify(commonService).updateBill(eq(PERSON_ID), eq(ACCOUNT_ID), eq(BILL_ID), any());
    }

    @Test
    void newBillUsesRelativeViewName() throws Exception {
        expectBillForm(mockMvc.perform(get(String.format(ACCOUNT_BASE, "create") + ACCOUNT_ID + "/bills/new")))
                .andExpect(status().isOk());
    }

    @Test
    void createBillWithValidationErrorsRendersUsableForm() throws Exception {
        expectBillForm(mockMvc.perform(post(String.format(ACCOUNT_BASE, "create") + ACCOUNT_ID + "/bills")))
                .andExpect(model().attributeHasFieldErrors("bill", "currency"));
        verify(commonService, never()).createBill(any(), any(), any());
    }

    @Test
    void createBillServiceFailureRendersUsableForm() throws Exception {
        doThrow(new IllegalStateException("downstream")).when(commonService).createBill(any(), any(), any());
        expectBillForm(mockMvc.perform(post(String.format(ACCOUNT_BASE, "create") + ACCOUNT_ID + "/bills").param("currency", "USD")))
                .andExpect(model().attribute("bill", hasProperty("currency", is(Currency.getInstance("USD")))));
    }

    @Test
    void newTransferUsesRelativeViewName() throws Exception {
        mockMvc.perform(get(String.format(ACCOUNT_BASE, "create") + ACCOUNT_ID + "/bills/" + BILL_ID + "/transfers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("bills/transfers/new"));
    }

    private ResultActions expectBillForm(ResultActions result) throws Exception {
        String action = "/common/v1/create/people/" + PERSON_ID + "/accounts/" + ACCOUNT_ID + "/bills";
        return result
                .andExpect(view().name("bills/new"))
                .andExpect(model().attribute("personId", PERSON_ID))
                .andExpect(model().attribute("accountId", ACCOUNT_ID))
                .andExpect(content().string(containsString("action=\"" + action + "\"")))
                .andExpect(content().string(containsString("<option value=\"USD\"")));
    }

    private void expectAccountNotFound(RequestBuilder request) throws Exception {
        expectNotFound(request, "Person with id " + PERSON_ID + " dont have account with id " + UNKNOWN_ID);
    }

    private void expectBillNotFound(RequestBuilder request, UUID accountId) throws Exception {
        UUID billId = accountId.equals(UNKNOWN_ID) ? BILL_ID : UNKNOWN_ID;
        expectNotFound(request, "Account with id " + accountId + " dont have bill with id " + billId);
    }

    private void expectNotFound(RequestBuilder request, String message) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isInstanceOf(ResourceNotFoundException.class);
                    assertThat(result.getResolvedException()).hasMessage(message);
                });
    }
}
