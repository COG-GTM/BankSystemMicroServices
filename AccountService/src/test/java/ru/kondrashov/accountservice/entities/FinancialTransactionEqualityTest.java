package ru.kondrashov.accountservice.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialTransactionEqualityTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    private final Bill bill = new Bill(USD, BigDecimal.TEN, false, new Account("acc", null, UUID.randomUUID()));
    private final Bill otherBill = new Bill(EUR, BigDecimal.ONE, false, new Account("acc2", null, UUID.randomUUID()));

    private static <T extends FinancialTransaction> T withId(T transaction, UUID id) {
        transaction.setId(id);
        return transaction;
    }

    @Test
    void distinctPaymentsOnSameBillAreNotEqual() {
        Payment small = withId(new Payment(USD, new BigDecimal("5"), LocalDateTime.now(), "a", bill), UUID.randomUUID());
        Payment large = withId(new Payment(EUR, new BigDecimal("500"), LocalDateTime.now().minusDays(1), "b", bill), UUID.randomUUID());

        assertThat(small).isNotEqualTo(large);
        assertThat(new HashSet<>(Arrays.asList(small, large))).hasSize(2);
    }

    @Test
    void transientTransactionsOnSameBillAreNotEqual() {
        Payment first = new Payment(USD, new BigDecimal("5"), LocalDateTime.now(), "a", bill);
        Payment second = new Payment(USD, new BigDecimal("5"), LocalDateTime.now(), "a", bill);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo(first);
        assertThat(new HashSet<>(Arrays.asList(first, second))).hasSize(2);
    }

    @Test
    void sameIdIsEqualRegardlessOfOtherFields() {
        UUID id = UUID.randomUUID();
        Adjustment loaded = withId(new Adjustment(USD, BigDecimal.ONE, LocalDateTime.now(), "a", bill), id);
        Adjustment reloaded = withId(new Adjustment(EUR, BigDecimal.TEN, LocalDateTime.now(), "b", otherBill), id);

        assertThat(loaded).isEqualTo(reloaded);
        assertThat(loaded.hashCode()).isEqualTo(reloaded.hashCode());
    }

    @Test
    void transfersBetweenSameBillsAreDistinguishedById() {
        Transfer first = withId(new Transfer(USD, BigDecimal.ONE, LocalDateTime.now(), "t", bill, otherBill), UUID.randomUUID());
        Transfer second = withId(new Transfer(USD, BigDecimal.ONE, LocalDateTime.now(), "t", bill, otherBill), UUID.randomUUID());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void differentTransactionTypesWithSameIdAreNotEqual() {
        UUID id = UUID.randomUUID();
        Payment payment = withId(new Payment(USD, BigDecimal.ONE, LocalDateTime.now(), "x", bill), id);
        Adjustment adjustment = withId(new Adjustment(USD, BigDecimal.ONE, LocalDateTime.now(), "x", bill), id);

        assertThat(payment).isNotEqualTo(adjustment);
    }

    @Test
    void hashCodeIsStableWhenIdIsAssignedOnPersist() {
        Payment payment = new Payment(USD, BigDecimal.ONE, LocalDateTime.now(), "x", bill);
        Set<Payment> payments = new HashSet<>();
        payments.add(payment);

        payment.setId(UUID.randomUUID());

        assertThat(payments).contains(payment);
    }

    @Test
    void toStringIncludesTransactionFieldsButNotBillGraph() {
        UUID id = UUID.randomUUID();
        Transfer transfer = withId(new Transfer(USD, new BigDecimal("42"), LocalDateTime.now(), "rent", bill, otherBill), id);

        assertThat(transfer.toString())
                .contains(id.toString(), "42", "rent", "USD")
                .doesNotContain("Bill(", "Account(");
    }
}
