package ru.kondrashov.accountservice.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import ru.kondrashov.accountservice.audit.AuditOperation;
import ru.kondrashov.accountservice.audit.BillAuditRecorder;
import ru.kondrashov.accountservice.controllers.dto.*;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.AuditEvent;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.exceptions.MakerCheckerViolationException;
import ru.kondrashov.accountservice.services.interfaces.AuditService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;
import ru.kondrashov.accountservice.utils.interfaces.AdjustmentMapping;
import ru.kondrashov.accountservice.utils.interfaces.BillsMapping;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.utils.interfaces.PaymentsMapping;
import ru.kondrashov.accountservice.utils.interfaces.TransferMapping;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/v1/people/{personId}/accounts/{accountId}/bills",produces = MediaType.APPLICATION_JSON_VALUE)
@Api(description= "Controller which provides methods for operations with bills", tags = {"BILLS"})
@RequiredArgsConstructor
public class BillsController {

    private final BillsService billsService;
    private final BillsMapping billsMapping;
    private final PaymentsMapping paymentsMapping;
    private final AdjustmentMapping adjustmentMapping;
    private final TransferMapping transferMapping;
    private final BillAuditRecorder auditRecorder;
    private final AuditService auditService;
    private final Logger logger;

    @GetMapping
    @ApiOperation(value = "Get all bills by account ID", tags = { "BILLS" })
    @ResponseStatus(HttpStatus.OK)
    public Collection<BillResponseDTO> getBillsByAccountId(
            @ApiParam(value = "Id of the person. Cannot be empty.") @PathVariable("personId") UUID personId,
            @ApiParam(value = "Id of the account to be obtained. Cannot be empty.") @PathVariable("accountId") UUID accountId,
            @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{personId}/accounts/{accountId}/bills\""+" received a GET request from the server "+header.getHost()+" with content type "+header.getContentType());
        return billsService.getBillsByAccountId(accountId)
                .stream()
                .map(billsMapping::mapToBillResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping
    @ApiOperation(value = "Save bill", tags = { "BILLS" })
    @ResponseStatus(HttpStatus.CREATED)
    public void save(
            @ApiParam(value = "Id of the person. Cannot be empty.") @PathVariable("personId") UUID personId,
            @ApiParam(value = "Id of the account to be obtained. Cannot be empty.") @PathVariable("accountId") UUID accountId,
            @ApiParam("Bill to add. Cannot null or empty.") @RequestBody @Valid BillRequestDTO billRequestDTO,
            @RequestHeader HttpHeaders header){

        logger.info("The controller "+"\"/v1/people/{personId}/accounts/{accountId}/bills\""+" received a POST request from the server "+header.getHost()+" with content type "+header.getContentType());
        AuditEvent.AuditEventBuilder event = auditEvent(AuditOperation.BILL_CREATED, personId, accountId, billRequestDTO.getAmount(), billRequestDTO.getCurrency());
        auditRecorder.record(event, null, null, () -> {
            Bill bill = billsMapping.mapToBill(billRequestDTO);
            billsService.save(bill);
            if (bill != null) {
                event.billId(bill.getId())
                        .balanceAfter(bill.getAmount())
                        .balanceAfterCurrency(BillAuditRecorder.currencyCode(bill));
                BillAuditRecorder.recordOwner(event, bill);
            }
            return null;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    @ApiOperation(value = "Update bill", tags = { "BILLS" })
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void update(
            @ApiParam(value = "Id of the person. Cannot be empty.") @PathVariable("personId") UUID personId,
            @ApiParam(value = "Id of the account to be obtained. Cannot be empty.") @PathVariable("accountId") UUID accountId,
            @ApiParam(value = "Id of the bill to be update. Cannot be empty.") @PathVariable("id") UUID id,
            @ApiParam("Bill to update. Cannot null or empty.") @RequestBody @Valid BillRequestDTO bill,
            @RequestHeader HttpHeaders header){

        logger.info("The controller "+"\"/v1/people/{personId}/accounts/{accountId}/bills/{id}\""+" received a PUT request from the server "+header.getHost()+" with content type "+header.getContentType());
        auditRecorder.record(auditEvent(AuditOperation.BILL_UPDATED, personId, accountId, bill.getAmount(), bill.getCurrency()), id, null, () -> {
            auditRecorder.requireActor();
            billsService.update(id,billsMapping.mapToBill(bill));
            return null;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    @ApiOperation(value = "Delete bill", tags = { "BILLS" })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@ApiParam(value = "Id of the person. Cannot be empty.") @PathVariable("personId") UUID personId,
                       @ApiParam(value = "Id of the account to be obtained. Cannot be empty.") @PathVariable("accountId") UUID accountId,
                       @ApiParam(value = "Id of the bill to be delete. Cannot be empty.") @PathVariable("id") UUID id,
                       @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{personId}/accounts/{accountId}/bills/{id}\""+" received a DELETE request from the server "+header.getHost()+" with content type "+header.getContentType());
        auditRecorder.record(auditEvent(AuditOperation.BILL_DELETED, personId, accountId, null, null), id, null, () -> {
            auditRecorder.requireActor();
            billsService.delete(id);
            return null;
        });
    }


    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{billId}/payments")
    @ApiOperation(value = "Commit payment for bill", tags = { "BILLS" })
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void commitPayment(@ApiParam(value = "Id of the person. Cannot be empty.")  @PathVariable("personId") UUID personId,
                              @ApiParam(value = "Id of the account. Cannot be empty.") @PathVariable("accountId") UUID accountId,
                              @ApiParam(value = "Id of the bill. Cannot be empty.") @PathVariable("billId") UUID billId,
                              @ApiParam("Payment to commit. Cannot null or empty.") @RequestBody @Valid PaymentRequestDTO paymentRequestDTO,
                              @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments\""+" received a PUT request from the server "+header.getHost()+" with content type "+header.getContentType());
        auditRecorder.record(auditEvent(AuditOperation.PAYMENT, personId, accountId, paymentRequestDTO.getAmount(), paymentRequestDTO.getCurrency()), billId, null, () -> {
            String actor = auditRecorder.requireActor();
            Payment payment = paymentsMapping.mapToPayment(paymentRequestDTO);
            payment.setInitiatedBy(actor);
            billsService.commitPayment(billId, payment);
            return payment.getId();
        });
    }

    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}/adjustments")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ApiOperation(value = "Commit adjustment for bill", tags = { "BILLS" })
    public void commitAdjustment(@ApiParam(value = "Id of the person. Cannot be empty.")  @PathVariable("personId") UUID personId,
                                 @ApiParam(value = "Id of the account. Cannot be empty.") @PathVariable("accountId") UUID accountId,
                                 @ApiParam(value = "Id of the bill. Cannot be empty.") @PathVariable("id") UUID billId,
                                 @ApiParam("Adjustment to commit. Cannot null or empty.") @RequestBody @Valid AdjustmentRequestDTO adjustmentRequestDTO,
                                 @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{personId}/accounts/{accountId}/bills/{billId}/adjustments\""+" received a PUT request from the server "+header.getHost()+" with content type "+header.getContentType());
        String approver = adjustmentRequestDTO.getApprovedBy();
        auditRecorder.record(auditEvent(AuditOperation.ADJUSTMENT, personId, accountId, adjustmentRequestDTO.getAmount(), adjustmentRequestDTO.getCurrency()).approvedBy(approver), billId, null, () -> {
            String actor = auditRecorder.requireActor();
            if (actor.equalsIgnoreCase(approver)) {
                throw new MakerCheckerViolationException("Adjustment must be approved by a different user than the one who initiates it");
            }
            Adjustment adjustment = adjustmentMapping.mapToAdjustment(adjustmentRequestDTO);
            adjustment.setInitiatedBy(actor);
            adjustment.setApprovedBy(approver);
            billsService.commitAdjustment(billId, adjustment);
            return adjustment.getId();
        });
    }

    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{sourceId}/transfer/{beneficiaryBillId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ApiOperation(value = "Commit transfer for bills", tags = { "BILLS" })
    public void commitTransfer(@ApiParam(value = "Id of the person. Cannot be empty.") @PathVariable("personId") UUID personId,
                               @ApiParam(value = "Id of the account. Cannot be empty.") @PathVariable("accountId") UUID accountId,
                               @ApiParam(value = "Id of the source bill. Cannot be empty.") @PathVariable("sourceId") UUID sourceId,
                               @ApiParam(value = "Id of the beneficiary bill. Cannot be empty.") @PathVariable("beneficiaryBillId") UUID beneficiaryBillId,
                               @ApiParam("Transfer to commit. Cannot null or empty.") @RequestBody @Valid TransferRequestDTO transferRequestDTO,
                               @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{personId}/accounts/{accountId}/bills/{sourceId}/transfer/{beneficiaryBillId}\""+" received a PUT request from the server "+header.getHost()+" with content type "+header.getContentType());
        auditRecorder.record(auditEvent(AuditOperation.TRANSFER, personId, accountId, transferRequestDTO.getAmount(), transferRequestDTO.getCurrency()), sourceId, beneficiaryBillId, () -> {
            String actor = auditRecorder.requireActor();
            Transfer transfer = transferMapping.mapToTransfer(transferRequestDTO);
            transfer.setInitiatedBy(actor);
            billsService.commitTransfer(sourceId, beneficiaryBillId, transfer);
            return transfer.getId();
        });
    }

    @GetMapping("/{id}/audit-events")
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Get the audit trail of a bill", tags = { "BILLS" })
    public Collection<AuditEventResponseDTO> getAuditEventsByBillId(@ApiParam(value = "Id of the person. Cannot be empty.") @PathVariable("personId") UUID personId,
                                                                    @ApiParam(value = "Id of the account. Cannot be empty.") @PathVariable("accountId") UUID accountId,
                                                                    @ApiParam(value = "Id of the bill. Cannot be empty.") @PathVariable("id") UUID billId,
                                                                    @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{personId}/accounts/{accountId}/bills/{id}/audit-events\""+" received a GET request from the server "+header.getHost()+" with content type "+header.getContentType());
        return auditService.getEventsByBillId(personId, accountId, billId)
                .stream()
                .map(event -> AuditEventResponseDTO.from(event, billId))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/transactions")
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Get all financial transactions of bill", tags = { "BILLS" })
    public Collection<FinancialTransactionResponseDTO> getFinancialTransactionsByBillId(@ApiParam(value = "Id of the person. Cannot be empty.") @PathVariable("personId") UUID personId,
                                                                                        @ApiParam(value = "Id of the account. Cannot be empty.") @PathVariable("accountId") UUID accountId,
                                                                                        @ApiParam(value = "Id of the bill. Cannot be empty.") @PathVariable("id") UUID billId,
                                                                                        @RequestHeader HttpHeaders header){
        logger.info("The controller "+"\"/v1/people/{personId}/accounts/{accountId}/bills/{id}/transactions\""+" received a GET request from the server "+header.getHost()+" with content type "+header.getContentType());
        Collection<FinancialTransactionResponseDTO> financialTransactionResponseDTOS = new ArrayList<>();

        financialTransactionResponseDTOS.addAll(billsService.
                getAdjustmentsByBillId(billId).
                stream().
                map(adjustmentMapping::mapToFinancialTransactionResponseDTO).
                collect(Collectors.toList()));

        financialTransactionResponseDTOS.
                addAll(
                        setAmountNegative(
                                billsService.
                                        getPaymentsByBillId(billId).
                                        stream().
                                        map(paymentsMapping::mapToFinancialTransactionResponseDTO).
                                        collect(Collectors.toList())
                        )
                );

        financialTransactionResponseDTOS.addAll(billsService.
                getTransfersByBeneficiaryBillId(billId).
                stream().
                map(transferMapping::mapToFinancialTransactionResponseDTO).
                collect(Collectors.toList())
        );
        financialTransactionResponseDTOS.addAll(setAmountNegative(billsService.getTransfersBySourceBillId(billId).stream().map(transferMapping::mapToFinancialTransactionResponseDTO).collect(Collectors.toList())));

        return financialTransactionResponseDTOS;
    }

    private static AuditEvent.AuditEventBuilder auditEvent(AuditOperation operation, UUID personId, UUID accountId, BigDecimal amount, Currency currency) {
        return AuditEvent.builder()
                .operation(operation)
                .personId(personId)
                .accountId(accountId)
                .amount(amount)
                .currency(currency == null ? null : currency.getCurrencyCode());
    }

    private Collection<FinancialTransactionResponseDTO> setAmountNegative(Collection<FinancialTransactionResponseDTO> financialTransactionResponseDTOS){

        financialTransactionResponseDTOS.forEach(financialTransactionResponseDTO -> financialTransactionResponseDTO.setAmount(financialTransactionResponseDTO.getAmount().negate()));

        return financialTransactionResponseDTOS;
    }
}
