package ru.kondrashov.accountservice.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.Hibernate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.UUID;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@Table(name = "bills", schema = "public")
public class Bill {

    @Id
    @GeneratedValue (strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    private Currency currency;

    private BigDecimal amount = BigDecimal.ZERO;

    @NotNull
    private Boolean isOverdraft;

    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @NotNull
    @ToString.Exclude
    private Account account;

    public Bill(@NotNull Currency currency, BigDecimal amount, @NotNull boolean isOverdraft, @NotNull Account account) {
        this.currency = currency;
        this.amount = amount;
        this.isOverdraft = isOverdraft;
        this.account = account;
    }

    @ToString.Include(name = "accountId")
    private UUID accountId() {
        return account == null ? null : account.getId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Bill other = (Bill) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
