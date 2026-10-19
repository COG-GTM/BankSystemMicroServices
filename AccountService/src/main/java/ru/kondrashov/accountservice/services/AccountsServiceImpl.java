package ru.kondrashov.accountservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.AccountAuditAction;
import ru.kondrashov.accountservice.entities.AccountAuditRecord;
import ru.kondrashov.accountservice.entities.AccountStatus;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.exceptions.AccountHasNonZeroBalanceException;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.repositories.AccountAuditRepository;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountsServiceImpl implements AccountsService {

    private final AccountsRepository accountsRepository;
    private final BillsRepository billsRepository;
    private final AccountAuditRepository accountAuditRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AccountsServiceImpl(AccountsRepository accountsRepository,
                               BillsRepository billsRepository,
                               AccountAuditRepository accountAuditRepository,
                               ObjectMapper objectMapper) {
        this(accountsRepository, billsRepository, accountAuditRepository, objectMapper, Clock.systemUTC());
    }

    AccountsServiceImpl(AccountsRepository accountsRepository,
                        BillsRepository billsRepository,
                        AccountAuditRepository accountAuditRepository,
                        ObjectMapper objectMapper,
                        Clock clock) {
        this.accountsRepository = accountsRepository;
        this.billsRepository = billsRepository;
        this.accountAuditRepository = accountAuditRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }


    @Override
    public Collection<Account> getAccounts() {
        return accountsRepository.getAccountsByStatus(AccountStatus.OPEN);
    }

    @Override
    public Collection<Account> getAccountsByPersonId(UUID id) {
        return accountsRepository.getAccountsByPersonIdAndStatus(id, AccountStatus.OPEN);
    }

    @Override
    public Account getAccount(UUID id) {
        return accountsRepository.getAccountsByIdAndStatus(id, AccountStatus.OPEN)
                .orElseThrow(()-> new AccountNotFoundException("Account with id = "+id+" not found."));
    }

    @Override
    public void save(Account account) {
        account.setId(null);
        account.setStatus(AccountStatus.OPEN);
        account.setClosedAt(null);
        accountsRepository.save(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID personId, UUID id, Account account, String actor) {
        Account updatedAccount = getAccountOfPerson(personId, id);
        String before = snapshot(updatedAccount, null);

        boolean ownershipChanged = !Objects.equals(updatedAccount.getPersonId(), account.getPersonId());

        updatedAccount.setName(account.getName());
        updatedAccount.setCreationDate(account.getCreationDate());
        updatedAccount.setPersonId(account.getPersonId());

        accountsRepository.save(updatedAccount);
        audit(updatedAccount.getId(),
                ownershipChanged ? AccountAuditAction.OWNERSHIP_CHANGED : AccountAuditAction.UPDATED,
                actor, before, snapshot(updatedAccount, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(UUID personId, UUID id, String actor) {
        Account account = getAccountOfPerson(personId, id);
        List<Bill> bills = billsRepository.getBillsByAccount_Id(id).stream().collect(Collectors.toList());

        List<UUID> nonZeroBills = bills.stream()
                .filter(bill -> bill.getAmount() != null && bill.getAmount().compareTo(BigDecimal.ZERO) != 0)
                .map(Bill::getId)
                .collect(Collectors.toList());
        if (!nonZeroBills.isEmpty()) {
            throw new AccountHasNonZeroBalanceException("Account with id = "+id+" cannot be closed: bills "+nonZeroBills+" have a non-zero balance.");
        }

        String before = snapshot(account, bills);

        account.setStatus(AccountStatus.CLOSED);
        account.setClosedAt(LocalDateTime.now(clock));
        accountsRepository.save(account);

        audit(account.getId(), AccountAuditAction.CLOSED, actor, before, snapshot(account, bills));
    }

    private Account getAccountOfPerson(UUID personId, UUID id) {
        Account account = getAccount(id);
        if (!Objects.equals(account.getPersonId(), personId)) {
            throw new AccountNotFoundException("Account with id = "+id+" not found.");
        }
        return account;
    }

    private void audit(UUID accountId, AccountAuditAction action, String actor, String before, String after) {
        accountAuditRepository.save(new AccountAuditRecord(
                accountId,
                action,
                actor == null || actor.trim().isEmpty() ? "unknown" : actor.trim(),
                before,
                after,
                LocalDateTime.now(clock)));
    }

    private String snapshot(Account account, List<Bill> bills) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", String.valueOf(account.getId()));
        state.put("name", account.getName());
        state.put("creationDate", String.valueOf(account.getCreationDate()));
        state.put("personId", String.valueOf(account.getPersonId()));
        state.put("status", String.valueOf(account.getStatus()));
        state.put("closedAt", account.getClosedAt() == null ? null : account.getClosedAt().toString());
        if (bills != null) {
            state.put("bills", bills.stream().map(bill -> {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("id", String.valueOf(bill.getId()));
                b.put("currency", String.valueOf(bill.getCurrency()));
                b.put("amount", bill.getAmount() == null ? null : bill.getAmount().toPlainString());
                return b;
            }).collect(Collectors.toList()));
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialise account audit state", e);
        }
    }
}
