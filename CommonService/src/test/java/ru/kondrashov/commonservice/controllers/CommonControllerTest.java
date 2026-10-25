package ru.kondrashov.commonservice.controllers;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import ru.kondrashov.commonservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.BillRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.BillResponseDTO;
import ru.kondrashov.commonservice.controllers.dto.FinancialTransactionRequestDTO;
import ru.kondrashov.commonservice.services.CommonService;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommonControllerTest {

    private final UUID personId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID billId = UUID.randomUUID();

    private CommonService commonService;
    private ExecutorService executor;
    private CommonController controller;

    @BeforeEach
    void setUp() {
        commonService = mock(CommonService.class);
        executor = Executors.newFixedThreadPool(2);
        controller = new CommonController(commonService, LogManager.getRootLogger(), executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void billPageFetchesBillByIdAndTransactionsConcurrently() {
        BillRequestDTO bill = new BillRequestDTO();
        Collection<FinancialTransactionRequestDTO> transactions = Collections.singletonList(new FinancialTransactionRequestDTO());
        CountDownLatch transactionsStarted = new CountDownLatch(1);
        CountDownLatch billStarted = new CountDownLatch(1);
        when(commonService.getFinancialTransactionsByBillId(personId, accountId, billId)).thenAnswer(invocation -> {
            transactionsStarted.countDown();
            assertTrue(billStarted.await(5, TimeUnit.SECONDS));
            return transactions;
        });
        when(commonService.getBill(personId, accountId, billId)).thenAnswer(invocation -> {
            billStarted.countDown();
            assertTrue(transactionsStarted.await(5, TimeUnit.SECONDS));
            return bill;
        });
        ExtendedModelMap model = new ExtendedModelMap();

        String view = assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),
                () -> controller.getBill(personId, accountId, billId, model));

        assertEquals("bills/bill", view);
        assertSame(bill, model.get("bill"));
        assertSame(transactions, model.get("transactions"));
        assertEquals(personId, model.get("personId"));
        verify(commonService, never()).getBillsByAccountId(any(), any());
    }

    @Test
    void billPageKeepsNotFoundException() {
        RuntimeException notFound = new RuntimeException("Account with id " + accountId + " dont have bill with id " + billId);
        when(commonService.getBill(personId, accountId, billId)).thenThrow(notFound);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> controller.getBill(personId, accountId, billId, new ExtendedModelMap()));

        assertSame(notFound, e);
    }

    @Test
    void billPageUnwrapsTransactionFailure() {
        IllegalStateException failure = new IllegalStateException("transactions unavailable");
        when(commonService.getBill(personId, accountId, billId)).thenReturn(new BillRequestDTO());
        when(commonService.getFinancialTransactionsByBillId(personId, accountId, billId)).thenThrow(failure);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> controller.getBill(personId, accountId, billId, new ExtendedModelMap()));

        assertSame(failure, e);
    }

    @Test
    void accountPageFetchesAccountByIdAndBillsConcurrently() {
        AccountRequestDTO account = new AccountRequestDTO();
        Collection<BillRequestDTO> bills = Collections.singletonList(new BillRequestDTO());
        CountDownLatch billsStarted = new CountDownLatch(1);
        CountDownLatch accountStarted = new CountDownLatch(1);
        when(commonService.getBillsByAccountId(personId, accountId)).thenAnswer(invocation -> {
            billsStarted.countDown();
            assertTrue(accountStarted.await(5, TimeUnit.SECONDS));
            return bills;
        });
        when(commonService.getAccount(personId, accountId)).thenAnswer(invocation -> {
            accountStarted.countDown();
            assertTrue(billsStarted.await(5, TimeUnit.SECONDS));
            return account;
        });
        ExtendedModelMap model = new ExtendedModelMap();

        String view = assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),
                () -> controller.getAccount(personId, accountId, model));

        assertEquals("accounts/account", view);
        assertSame(account, model.get("account"));
        assertSame(bills, model.get("bills"));
        verify(commonService, never()).getAccountsByPerson(any());
    }

    @Test
    void editPagesAndBillFormsLookUpSingleResourceById() throws Exception {
        AccountRequestDTO account = new AccountRequestDTO();
        account.setId(accountId);
        account.setPersonId(personId);
        BillRequestDTO bill = new BillRequestDTO();
        when(commonService.getAccount(personId, accountId)).thenReturn(account);
        when(commonService.getBill(personId, accountId, billId)).thenReturn(bill);

        ExtendedModelMap editAccountModel = new ExtendedModelMap();
        controller.editAccount(personId, accountId, editAccountModel);
        assertSame(account, editAccountModel.get("account"));

        ExtendedModelMap editBillModel = new ExtendedModelMap();
        controller.editBill(personId, accountId, billId, editBillModel);
        assertSame(bill, editBillModel.get("bill"));

        BillResponseDTO newBill = new BillResponseDTO();
        String createRedirect = controller.createBill(newBill, new BeanPropertyBindingResult(newBill, "bill"), personId, accountId);
        assertSame(account, newBill.getAccount());
        assertEquals(String.format("redirect:/common/v1/info/people/%s/accounts/%s", personId, accountId), createRedirect);

        BillResponseDTO updatedBill = new BillResponseDTO();
        controller.updateBill(personId, accountId, billId, updatedBill, new BeanPropertyBindingResult(updatedBill, "bill"));
        assertSame(account, updatedBill.getAccount());

        verify(commonService).createBill(personId, accountId, newBill);
        verify(commonService).updateBill(personId, accountId, billId, updatedBill);
        verify(commonService, never()).getAccountsByPerson(any());
        verify(commonService, never()).getBillsByAccountId(any(), any());
    }
}
