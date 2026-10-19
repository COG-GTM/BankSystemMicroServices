package ru.kondrashov.accountservice.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.kondrashov.accountservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.AccountResponseDTO;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.utils.interfaces.AccountMapping;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping( value = "/v1/people/{personId}/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@Api(description = "Controller which provides methods for operations with accounts", tags = {"ACCOUNTS"})
@RequiredArgsConstructor
public class AccountsController {

    static final String ACTOR_HEADER = "X-Actor";

    private final AccountsService accountsService;
    private final AccountMapping accountMapping;
    private final Logger logger;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Find accounts by person ID", tags = { "ACCOUNTS" })
    public Collection<AccountResponseDTO> getAccountsByPersonId(
            @ApiParam("Id of the person to be obtained. Cannot be empty.") @PathVariable("personId") UUID id,
            @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{personId}/accounts\""+" received a GET request from the server "+header.getHost()+" with content type "+header.getContentType());
        return accountsService
                .getAccountsByPersonId(id)
                .stream()
                .map(account -> accountMapping.mapToAccountResponseDTO(account))
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation(value = "Add a new account", tags = { "ACCOUNTS" })
    public void save(@ApiParam("Id of the person to be obtained. Cannot be empty.") @PathVariable("personId") UUID id,
                     @ApiParam("Account to add. Cannot null or empty.") @RequestBody @Valid AccountRequestDTO account,
                     @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{personId}/accounts\""+" received a POST request from the server "+header.getHost()+" with content type "+header.getContentType());
        accountsService.save(accountMapping.mapToAccount(account));
    }

    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ApiOperation(value = "Update a account", tags = { "ACCOUNTS" })
    public void update(@ApiParam("Id of the person to be obtained. Cannot be empty.") @PathVariable("personId") UUID personId,
                       @ApiParam("Id of the account to be obtained. Cannot be empty.") @PathVariable("id") UUID id,
                       @ApiParam("Account to update. Cannot null or empty.") @RequestBody @Valid AccountRequestDTO account,
                       @ApiParam("Identity of the caller, recorded in the account audit log.") @RequestHeader(value = ACTOR_HEADER, required = false) String actor,
                       @RequestHeader HttpHeaders header,
                       HttpServletRequest request){
        logger.info("The controller "+"\"/v1/people/{personId}/accounts/{id}\""+" received a PUT request from the server "+header.getHost()+" with content type "+header.getContentType());
        accountsService.update(personId, id, accountMapping.mapToAccount(account), resolveActor(actor, request));
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiOperation(value = "Close an account (soft delete). Refused with 409 while any bill has a non-zero balance; bills and transactions are retained.", tags = { "ACCOUNTS" })
    public void delete(@ApiParam("Id of the person to be obtained. Cannot be empty.") @PathVariable("personId") UUID personId,
                       @ApiParam("Id of the account to be obtained. Cannot be empty.") @PathVariable("id") UUID id,
                       @ApiParam("Identity of the caller, recorded in the account audit log.") @RequestHeader(value = ACTOR_HEADER, required = false) String actor,
                       @RequestHeader HttpHeaders header,
                       HttpServletRequest request){
        logger.info("The controller "+"\"/v1/people/{personId}/accounts/{id}\""+" received a DELETE request from the server "+header.getHost()+" with content type "+header.getContentType());
        accountsService.close(personId, id, resolveActor(actor, request));
    }

    private static String resolveActor(String actor, HttpServletRequest request) {
        String remote = "remote=" + request.getRemoteAddr();
        if (actor == null || actor.trim().isEmpty()) {
            return remote;
        }
        return actor.trim().replaceAll("[\\r\\n\\t]", "_") + " (" + remote + ")";
    }
}
