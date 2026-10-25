package ru.kondrashov.accountservice.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityIdentityTest {

    private static final Currency USD = Currency.getInstance("USD");

    private static Account account() {
        return new Account("account", LocalDate.now(), UUID.randomUUID());
    }

    private static Bill bill(Account account) {
        return new Bill(USD, BigDecimal.TEN, false, account);
    }

    @Test
    void transientEntitiesAreOnlyEqualToThemselves() {
        Account account = account();
        Bill first = bill(account);
        Bill second = bill(account);

        assertThat(first).isEqualTo(first);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void equalityIsBasedOnIdOnly() {
        UUID id = UUID.randomUUID();
        Account account = account();
        account.setId(id);
        Account sameRow = new Account("renamed", LocalDate.now().minusDays(1), UUID.randomUUID());
        sameRow.setId(id);

        Bill bill = bill(account);
        bill.setId(UUID.randomUUID());
        Bill sameBillOtherState = new Bill(Currency.getInstance("EUR"), BigDecimal.ONE, true, account());
        sameBillOtherState.setId(bill.getId());

        assertThat(account).isEqualTo(sameRow).hasSameHashCodeAs(sameRow);
        assertThat(bill).isEqualTo(sameBillOtherState).hasSameHashCodeAs(sameBillOtherState);
    }

    @Test
    void hashCodeIsStableWhenIdIsAssignedOnPersist() {
        Account account = account();
        Bill bill = bill(account);
        Payment payment = new Payment(USD, BigDecimal.ONE, LocalDateTime.now(), "payment", bill);
        Set<Object> entities = new HashSet<>();
        entities.add(account);
        entities.add(bill);
        entities.add(payment);

        account.setId(UUID.randomUUID());
        bill.setId(UUID.randomUUID());
        payment.setId(UUID.randomUUID());
        bill.setAmount(BigDecimal.ZERO);

        assertThat(entities.contains(account)).isTrue();
        assertThat(entities.contains(bill)).isTrue();
        assertThat(entities.contains(payment)).isTrue();
    }

    @Test
    void transactionsOfDifferentTypesWithSameIdAreNotEqual() {
        UUID id = UUID.randomUUID();
        Bill bill = bill(account());
        Payment payment = new Payment(USD, BigDecimal.ONE, LocalDateTime.now(), "m", bill);
        payment.setId(id);
        Adjustment adjustment = new Adjustment(USD, BigDecimal.ONE, LocalDateTime.now(), "m", bill);
        adjustment.setId(id);

        assertThat(payment).isNotEqualTo(adjustment);
    }

    @Test
    void transactionsWithSameStateButDifferentIdsAreNotEqual() {
        Bill bill = bill(account());
        LocalDateTime time = LocalDateTime.now();
        Payment first = new Payment(USD, BigDecimal.ONE, time, "m", bill);
        first.setId(UUID.randomUUID());
        Payment second = new Payment(USD, BigDecimal.ONE, time, "m", bill);
        second.setId(UUID.randomUUID());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void toStringReferencesAssociationsByIdOnly() {
        Account account = new Account("secret-account-name", LocalDate.now(), UUID.randomUUID());
        account.setId(UUID.randomUUID());
        Bill source = bill(account);
        source.setId(UUID.randomUUID());
        Bill beneficiary = bill(account);
        beneficiary.setId(UUID.randomUUID());
        Transfer transfer = new Transfer(USD, BigDecimal.ONE, LocalDateTime.now(), "transfer", source, beneficiary);
        transfer.setId(UUID.randomUUID());
        Payment payment = new Payment(USD, BigDecimal.ONE, LocalDateTime.now(), "payment", source);

        assertThat(source.toString())
                .contains("accountId=" + account.getId())
                .doesNotContain("secret-account-name");
        assertThat(transfer.toString())
                .contains("id=" + transfer.getId())
                .contains("sourceBillId=" + source.getId())
                .contains("beneficiaryBillId=" + beneficiary.getId())
                .doesNotContain("secret-account-name");
        assertThat(payment.toString())
                .contains("billId=" + source.getId())
                .doesNotContain("Bill(");
    }
}
