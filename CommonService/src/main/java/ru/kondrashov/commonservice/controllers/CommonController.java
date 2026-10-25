package ru.kondrashov.commonservice.controllers;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import ru.kondrashov.commonservice.controllers.dto.*;
import ru.kondrashov.commonservice.exceptions.DownstreamServiceException;
import ru.kondrashov.commonservice.services.CommonService;

import javax.validation.Valid;
import java.util.Collection;
import java.util.Currency;
import java.util.UUID;

@Controller
@RequestMapping(value = "/common/v1", produces = MediaType.TEXT_HTML_VALUE)
@RequiredArgsConstructor
public class CommonController {

    static final String DOWNSTREAM_ERROR_CODE = "downstream.error";

    private final CommonService commonService;
    private final Logger logger;

    @GetMapping("/info/people")
    @ResponseStatus (HttpStatus.OK)
    public String getPeople(Model model){
        model.addAttribute("people",commonService.getPeople());
        return "people/people";
    }

    @GetMapping("/info/people/{id}")
    @ResponseStatus (HttpStatus.OK)
    public String getPerson(@PathVariable UUID id, Model model) {
        model.addAttribute("person", commonService.getPersonById(id));
        model.addAttribute("accounts", commonService.getAccountsByPerson(id));
        return "people/person";
    }

    @GetMapping("/info/people/{personId}/accounts/{accountId}")
    @ResponseStatus (HttpStatus.OK)
    public String getAccount(@PathVariable("personId") UUID personId, @PathVariable("accountId") UUID accountId, Model model) {
        model.addAttribute(
                "account",
                commonService.
                        getAccountsByPerson(personId)
                        .stream()
                        .filter(account -> account.getId().equals(accountId))
                        .findFirst().orElseThrow(() -> new RuntimeException("Person with id " + personId+" dont have account with id "+accountId))
        );
        model.addAttribute("bills",commonService.getBillsByAccountId(personId, accountId));
        model.addAttribute("personId", personId);
        return "accounts/account";
    }

    @GetMapping("/info/people/{personId}/accounts/{accountId}/bills/{billId}")
    @ResponseStatus (HttpStatus.OK)
    public String getBill(@PathVariable("personId") UUID personId, @PathVariable("accountId") UUID accountId, @PathVariable("billId") UUID billId, Model model) {
        model.addAttribute(
                "bill",
                commonService.
                        getBillsByAccountId(personId, accountId)
                        .stream()
                        .filter(bill -> bill.getId().equals(billId))
                        .findFirst().orElseThrow(() -> new RuntimeException("Account with id " + accountId+" dont have bill with id "+billId))
        );
        model.addAttribute("personId", personId);
        Collection<FinancialTransactionRequestDTO> financialTransactionRequestDTOS = commonService.getFinancialTransactionsByBillId(personId, accountId, billId);
        model.addAttribute("transactions", financialTransactionRequestDTOS);

        return "bills/bill";
    }

    @GetMapping("/create/people/new")
    @ResponseStatus(HttpStatus.OK)
    public String newPerson(Model model){
        model.addAttribute("person", new PersonResponseDTO());
        return "people/new";
    }

    @GetMapping("/create/people/{personId}/accounts/new")
    @ResponseStatus(HttpStatus.OK)
    public String newAccount(@PathVariable("personId") UUID id, Model model){
        model.addAttribute("account", new AccountResponseDTO());
        populateNewAccountModel(model, id);
        return "accounts/new";
    }

    @GetMapping("/create/people/{personId}/accounts/{accountId}/bills/new")
    @ResponseStatus(HttpStatus.OK)
    public String newBill(@PathVariable("personId") UUID personId, @PathVariable("accountId") UUID accountId, Model model){
        model.addAttribute("bill", new BillResponseDTO());
        populateNewBillModel(model, personId, accountId);
        return "/bills/new";
    }

    @GetMapping("create/people/{personId}/accounts/{accountId}/bills/{billId}/transfers/new")
    @ResponseStatus(HttpStatus.OK)
    public String newTransfer (@PathVariable("accountId") UUID accountId, @PathVariable("billId") UUID billId, Model model){
        model.addAttribute("accountId", accountId);
        model.addAttribute("billId",billId);
        model.addAttribute("transfer", new TransferResponseDTO());
        model.addAttribute("people", commonService.getPeople());
        return "/bills/transfers/new";
    }

    @PostMapping("/create/people")
    public String createPerson(@ModelAttribute("person") @Valid PersonResponseDTO person, BindingResult bindingResult){
        if(bindingResult.hasErrors()){
            bindingResult.getFieldErrors().forEach(fieldError -> logger.error(fieldError));
            return "people/new";
        }
        try {
            commonService.createPerson(person);
            return "redirect:/common/v1/info/people";
        } catch (DownstreamServiceException e) {
            rejectDownstreamFailure("create person", e, bindingResult);
            return "people/new";
        }
    }

    @PostMapping("/create/people/{id}/accounts")
    public String createAccount(@ModelAttribute("account") @Valid AccountResponseDTO account, BindingResult bindingResult, @PathVariable("id") UUID id, Model model){
        if(bindingResult.hasErrors()){
            bindingResult.getFieldErrors().forEach(fieldError -> logger.error(fieldError));
            populateNewAccountModel(model, id);
            return "accounts/new";
        }
        try {
            account.setPersonId(id);
            commonService.createAccount(id, account);
            return "redirect:/common/v1/info/people/"+id;
        } catch (DownstreamServiceException e) {
            rejectDownstreamFailure("create account", e, bindingResult);
            populateNewAccountModel(model, id);
            return "accounts/new";
        }
    }

    @PostMapping("create/people/{personId}/accounts/{accountId}/bills")
    public String createBill(@ModelAttribute("bill") @Valid BillResponseDTO bill, BindingResult bindingResult, @PathVariable("personId") UUID personId, @PathVariable("accountId") UUID accountId, Model model){
        if(bindingResult.hasErrors()){
            bindingResult.getFieldErrors().forEach(fieldError -> logger.error(fieldError));
            populateNewBillModel(model, personId, accountId);
            return "/bills/new";
        }
        try {
            AccountRequestDTO accountRequestDTO = findAccount(personId, accountId);
            bill.setAccount(accountRequestDTO);
            commonService.createBill(personId,accountId,bill);
            return String.format("redirect:/common/v1/info/people/%s/accounts/%s",accountRequestDTO.getPersonId(),accountRequestDTO.getId());
        } catch (DownstreamServiceException e) {
            rejectDownstreamFailure("create bill", e, bindingResult);
            populateNewBillModel(model, personId, accountId);
            return "/bills/new";
        }
    }

    @GetMapping("/update/people/{id}/edit")
    @ResponseStatus(HttpStatus.OK)
    public String editPerson(@PathVariable("id") UUID id, Model model){
        model.addAttribute("person", commonService.getPersonById(id));
        populateEditPersonModel(model, id);
        return "people/edit";
    }

    @GetMapping("/update/people/{personId}/accounts/{accountId}/edit")
    @ResponseStatus(HttpStatus.OK)
    public String editAccount(@PathVariable("personId") UUID personId, @PathVariable("accountId") UUID accountId, Model model){
        model.addAttribute("account", findAccount(personId, accountId));
        populateEditAccountModel(model, personId, accountId);
        return "accounts/edit";
    }

    @GetMapping("/update/people/{personId}/accounts/{accountId}/bills/{billId}/edit")
    @ResponseStatus(HttpStatus.OK)
    public String editBill(@PathVariable("personId") UUID personId, @PathVariable("accountId") UUID accountId, @PathVariable("billId") UUID billId, Model model){
        model.addAttribute("bill", commonService.getBillsByAccountId(personId,accountId).stream().filter(bill->bill.getId().equals(billId)).findFirst().get());
        populateEditBillModel(model, personId, accountId, billId);
        return "bills/edit";
    }



    @PutMapping("/update/people/{id}")
    public String updatePerson(@PathVariable UUID id, @ModelAttribute("person") @Valid PersonResponseDTO personResponseDTO, BindingResult bindingResult, Model model) {
        if(bindingResult.hasErrors()){
            bindingResult.getFieldErrors().forEach(fieldError -> logger.error(fieldError));
            populateEditPersonModel(model, id);
            return "people/edit";
        }
        try {
            commonService.updatePerson(id, personResponseDTO);
            return "redirect:/common/v1/info/people/"+id;
        } catch (DownstreamServiceException e) {
            rejectDownstreamFailure("update person", e, bindingResult);
            populateEditPersonModel(model, id);
            return "people/edit";
        }
    }

    @PutMapping("/update/people/{personId}/accounts/{accountId}")
    public String updateAccount(@PathVariable("personId") UUID personId, @PathVariable("accountId") UUID accountId, @ModelAttribute("account") @Valid AccountResponseDTO accountResponseDTO, BindingResult bindingResult, Model model) {
        if(bindingResult.hasErrors()){
            bindingResult.getFieldErrors().forEach(fieldError -> logger.error(fieldError));
            populateEditAccountModel(model, personId, accountId);
            return "accounts/edit";
        }
        try {
            commonService.updateAccount(personId, accountId, accountResponseDTO);
            return "redirect:/common/v1/info/people/"+personId;
        } catch (DownstreamServiceException e) {
            rejectDownstreamFailure("update account", e, bindingResult);
            populateEditAccountModel(model, personId, accountId);
            return "accounts/edit";
        }
    }

    @PutMapping("/update/people/{personId}/accounts/{accountId}/bills/{billId}")
    public String updateBill(@PathVariable("personId") UUID personId, @PathVariable("accountId") UUID accountId, @PathVariable("billId") UUID billId, @ModelAttribute("bill") @Valid BillResponseDTO billResponseDTO, BindingResult bindingResult, Model model) {
        if(bindingResult.hasErrors()){
            bindingResult.getFieldErrors().forEach(fieldError -> logger.error(fieldError));
            populateEditBillModel(model, personId, accountId, billId);
            return "bills/edit";
        }
        try {
            billResponseDTO.setAccount(findAccount(personId, accountId));
            commonService.updateBill(personId, accountId, billId, billResponseDTO);
            return String.format("redirect:/common/v1/info/people/%s/accounts/%s", personId,accountId);
        } catch (DownstreamServiceException e) {
            rejectDownstreamFailure("update bill", e, bindingResult);
            populateEditBillModel(model, personId, accountId, billId);
            return "bills/edit";
        }
    }

    @DeleteMapping("/delete/people/{id}")
    @ResponseStatus(HttpStatus.FOUND)
    public String deletePerson(@PathVariable UUID id){
        commonService.deletePerson(id);
        return "redirect:/common/v1/info/people";
    }

    @DeleteMapping("/delete/people/{personId}/accounts/{accountId}")
    @ResponseStatus(HttpStatus.FOUND)
    public String deleteAccount(@PathVariable("personId") UUID personId, @PathVariable("accountId") UUID accountId){
        commonService.deleteAccount(personId, accountId);
        return "redirect:/common/v1/info/people/"+personId;
    }

    @DeleteMapping("/delete/people/{personId}/accounts/{accountId}/bills/{billId}")
    @ResponseStatus(HttpStatus.FOUND)
    public String deleteBill(@PathVariable("personId") UUID personId, @PathVariable("accountId") UUID accountId, @PathVariable("billId") UUID billId){
        commonService.deleteBill(personId, accountId, billId);
        return String.format("redirect:/common/v1/info/people/%s/accounts/%s", personId, accountId);
    }

    @ExceptionHandler(DownstreamServiceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String handleDownstreamFailure(DownstreamServiceException e, Model model) {
        logger.error("Downstream request failed: " + e.getMessage(), e);
        model.addAttribute("errorMessage", "Request failed: " + e.getUserMessage());
        return "error/downstream";
    }

    private void rejectDownstreamFailure(String action, DownstreamServiceException e, BindingResult bindingResult) {
        logger.error("Failed to " + action + ": " + e.getMessage(), e);
        bindingResult.reject(DOWNSTREAM_ERROR_CODE, "Could not " + action + ": " + e.getUserMessage());
    }

    private AccountRequestDTO findAccount(UUID personId, UUID accountId) {
        return commonService.getAccountsByPerson(personId).stream()
                .filter(account -> account.getId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Person with id " + personId + " dont have account with id " + accountId));
    }

    private void populateNewAccountModel(Model model, UUID personId) {
        model.addAttribute("id", personId);
    }

    private void populateNewBillModel(Model model, UUID personId, UUID accountId) {
        model.addAttribute("currencies", Currency.getAvailableCurrencies());
        model.addAttribute("accountId", accountId);
        model.addAttribute("personId", personId);
    }

    private void populateEditPersonModel(Model model, UUID id) {
        model.addAttribute("id", id);
    }

    private void populateEditAccountModel(Model model, UUID personId, UUID accountId) {
        model.addAttribute("id", accountId);
        model.addAttribute("accountId", accountId);
        model.addAttribute("personId", personId);
    }

    private void populateEditBillModel(Model model, UUID personId, UUID accountId, UUID billId) {
        model.addAttribute("currencies", Currency.getAvailableCurrencies());
        model.addAttribute("accountId", accountId);
        model.addAttribute("personId", personId);
        model.addAttribute("billId", billId);
    }
}
