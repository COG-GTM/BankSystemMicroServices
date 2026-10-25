package ru.kondrashov.accountservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.services.interfaces.AdjustmentsService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;
import ru.kondrashov.accountservice.services.interfaces.TransferService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
class ServiceLayerTransactionIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Autowired
    private AccountsService accountsService;

    @Autowired
    private BillsService billsService;

    @Autowired
    private BillsRepository billsRepository;

    @MockBean
    private TransferService transferService;

    @MockBean
    private AdjustmentsService adjustmentsService;

    @Test
    void commitTransferRollsBackBothBillsWhenTheTransferCannotBeRecorded() {
        Account account = newAccount();
        Bill source = newBill(account, "50");
        Bill beneficiary = newBill(account, "50");
        doThrow(new IllegalStateException("transfers table unavailable")).when(transferService).save(any());

        Transfer transfer = new Transfer(USD, new BigDecimal("20"), LocalDateTime.now(), "rent", source, beneficiary);
        assertThatThrownBy(() -> billsService.commitTransfer(source.getId(), beneficiary.getId(), transfer))
                .isInstanceOf(IllegalStateException.class);

        assertThat(amountOf(source)).isEqualByComparingTo("50");
        assertThat(amountOf(beneficiary)).isEqualByComparingTo("50");
    }

    @Test
    void commitAdjustmentRollsBackTheBillWhenTheAdjustmentCannotBeRecorded() {
        Account account = newAccount();
        Bill bill = newBill(account, "50");
        doThrow(new IllegalStateException("adjustments table unavailable")).when(adjustmentsService).save(any());

        Adjustment adjustment = new Adjustment(USD, new BigDecimal("20"), LocalDateTime.now(), "bonus", bill);
        assertThatThrownBy(() -> billsService.commitAdjustment(bill.getId(), adjustment))
                .isInstanceOf(IllegalStateException.class);

        assertThat(amountOf(bill)).isEqualByComparingTo("50");
    }

    private Account newAccount() {
        Account account = new Account("account", LocalDate.now(), UUID.randomUUID());
        accountsService.save(account);
        return account;
    }

    private Bill newBill(Account account, String amount) {
        Bill bill = new Bill(USD, new BigDecimal(amount), false, account);
        billsService.save(bill);
        return bill;
    }

    private BigDecimal amountOf(Bill bill) {
        return billsRepository.getBillById(bill.getId()).get().getAmount();
    }
}
