package ru.kondrashov.commonservice.controllers;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.commonservice.controllers.dto.BillRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.TransferResponseDTO;
import ru.kondrashov.commonservice.services.CommonService;
import ru.kondrashov.commonservice.services.TransferRejectedException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CommonController.class)
class CommonControllerTransferTest {

    private static final String FORM_URL = "/common/v1/create/people/{personId}/accounts/{accountId}/bills/{billId}/transfers/new";
    private static final String SUBMIT_URL = "/common/v1/create/people/{personId}/accounts/{accountId}/bills/{billId}/transfers";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommonService commonService;

    @MockBean
    private Logger logger;

    private final UUID personId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID billId = UUID.randomUUID();
    private final UUID beneficiaryBillId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BillRequestDTO sourceBill = new BillRequestDTO();
        sourceBill.setId(billId);
        sourceBill.setCurrency(Currency.getInstance("EUR"));
        sourceBill.setAmount(new BigDecimal("100.00"));
        when(commonService.getBillsByAccountId(personId, accountId)).thenReturn(Collections.singletonList(sourceBill));
    }

    @Test
    void newTransferRendersFormThatPostsToMappedHandler() throws Exception {
        mockMvc.perform(get(FORM_URL, personId, accountId, billId))
                .andExpect(status().isOk())
                .andExpect(view().name("bills/transfers/new"))
                .andExpect(model().attribute("transfer", hasProperty("currency", equalTo(Currency.getInstance("EUR")))))
                .andExpect(content().string(containsString(
                        String.format("action=\"/common/v1/create/people/%s/accounts/%s/bills/%s/transfers\"", personId, accountId, billId))));
    }

    @Test
    void reviewShowsConfirmationWithoutSendingTransfer() throws Exception {
        mockMvc.perform(validSubmission())
                .andExpect(status().isOk())
                .andExpect(view().name("bills/transfers/confirm"))
                .andExpect(content().string(containsString(beneficiaryBillId.toString())))
                .andExpect(content().string(containsString("25.50 USD")));

        verify(commonService, never()).createTransfer(any(), any(), any(), any());
    }

    @Test
    void confirmSendsTransferAndRedirectsToBill() throws Exception {
        mockMvc.perform(validSubmission().param("action", "confirm"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(String.format("/common/v1/info/people/%s/accounts/%s/bills/%s", personId, accountId, billId)))
                .andExpect(flash().attribute("transferSuccess", "Transferred 25.50 USD to bill " + beneficiaryBillId + "."));

        verify(commonService).createTransfer(eq(personId), eq(accountId), eq(billId), argThat(t ->
                t.getBeneficiaryBillId().equals(beneficiaryBillId)
                        && t.getCurrency().getCurrencyCode().equals("USD")
                        && t.getAmount().compareTo(new BigDecimal("25.50")) == 0
                        && t.getMessage().equals("Rent")));
    }

    @Test
    void editReturnsToPrefilledForm() throws Exception {
        mockMvc.perform(validSubmission().param("action", "edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("bills/transfers/new"))
                .andExpect(content().string(containsString("value=\"" + beneficiaryBillId + "\"")));

        verify(commonService, never()).createTransfer(any(), any(), any(), any());
    }

    @Test
    void invalidFieldsRerenderFormWithMessages() throws Exception {
        mockMvc.perform(post(SUBMIT_URL, personId, accountId, billId)
                        .param("beneficiaryBillId", "not-a-uuid")
                        .param("currency", "USD")
                        .param("amount", "0")
                        .param("message", "")
                        .param("action", "confirm"))
                .andExpect(status().isOk())
                .andExpect(view().name("bills/transfers/new"))
                .andExpect(model().attributeHasFieldErrors("transfer", "beneficiaryBillId", "amount", "message"))
                .andExpect(content().string(containsString("Beneficiary bill ID should be a valid UUID")))
                .andExpect(content().string(containsString("Amount should be greater than zero")))
                .andExpect(content().string(containsString("Message should not be empty")));

        verify(commonService, never()).createTransfer(any(), any(), any(), any());
    }

    @Test
    void transferToSameBillIsRejected() throws Exception {
        mockMvc.perform(post(SUBMIT_URL, personId, accountId, billId)
                        .param("beneficiaryBillId", billId.toString())
                        .param("currency", "USD")
                        .param("amount", "1")
                        .param("message", "Self")
                        .param("action", "confirm"))
                .andExpect(view().name("bills/transfers/new"))
                .andExpect(model().attributeHasFieldErrorCode("transfer", "beneficiaryBillId", "transfer.sameBill"));

        verify(commonService, never()).createTransfer(any(), any(), any(), any());
    }

    @Test
    void rejectionFromAccountServiceIsShownOnForm() throws Exception {
        doThrow(new TransferRejectedException(HttpStatus.BAD_REQUEST, "Not enough money for payment on bill with ID: " + billId))
                .when(commonService).createTransfer(any(), any(), any(), any());

        String body = mockMvc.perform(validSubmission().param("action", "confirm"))
                .andExpect(status().isOk())
                .andExpect(view().name("bills/transfers/new"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Transfer was not completed").contains("Not enough money for payment on bill with ID: " + billId);
    }

    @Test
    void unknownSourceBillIsNotAccepted() {
        when(commonService.getBillsByAccountId(personId, accountId)).thenReturn(Collections.emptyList());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                mockMvc.perform(validSubmission().param("action", "confirm")));
        verify(commonService, never()).createTransfer(any(), any(), any(), any());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validSubmission() {
        return post(SUBMIT_URL, personId, accountId, billId)
                .param("beneficiaryBillId", beneficiaryBillId.toString())
                .param("currency", "USD")
                .param("amount", "25.50")
                .param("message", "Rent");
    }
}
