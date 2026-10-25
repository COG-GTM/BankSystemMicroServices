package ru.kondrashov.commonservice.controllers.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@Data
public class BillResponseDTO {

    private UUID id;

    @NotNull
    private Currency currency;

    private BigDecimal amount ;

    private Boolean isOverdraft;

    @NotNull
    private AccountResponseDTO account;

    public Boolean isOverdraft(){
        return isOverdraft;
    }

    public void isOverdraft(String isOverdraft){
        this.isOverdraft = Boolean.getBoolean(isOverdraft);
    }
}
