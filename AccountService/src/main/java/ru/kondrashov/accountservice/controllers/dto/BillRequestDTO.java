package ru.kondrashov.accountservice.controllers.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Currency;

/**
 * Client-writable bill attributes. The balance is owned by the ledger (payments, adjustments, transfers)
 * and the owning account comes from the request path, so neither can be supplied here.
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BillRequestDTO {

    public static final Set<String> LEDGER_CONTROLLED_FIELDS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("amount", "account")));

    @NotNull
    private Currency currency;

    @NotNull
    private Boolean isOverdraft;

    @JsonAnySetter
    public void rejectLedgerControlledField(String name, Object value) {
        if (LEDGER_CONTROLLED_FIELDS.contains(name)) {
            throw new IllegalArgumentException("Field '" + name + "' cannot be set on a bill; "
                    + "balances change only through payments, adjustments or transfers, "
                    + "and the account is taken from the request path.");
        }
    }
}
