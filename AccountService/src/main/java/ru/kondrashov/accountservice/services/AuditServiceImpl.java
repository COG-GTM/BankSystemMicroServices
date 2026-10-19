package ru.kondrashov.accountservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.kondrashov.accountservice.audit.AuditOutcome;
import ru.kondrashov.accountservice.entities.AuditEvent;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.repositories.AuditEventRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AuditService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    static final String AUDIT_LOGGER_NAME = "AUDIT";
    private static final Logger AUDIT_LOG = LogManager.getLogger(AUDIT_LOGGER_NAME);

    private final AuditEventRepository auditEventRepository;
    private final BillsRepository billsRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public AuditEvent recordSuccess(AuditEvent auditEvent) {
        return persist(auditEvent);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AuditEvent recordFailure(AuditEvent auditEvent) {
        return persist(auditEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> getEventsByBillId(UUID personId, UUID accountId, UUID billId) {
        boolean owned = billsRepository.getBillById(billId)
                .map(bill -> bill.getAccount() != null
                        && accountId.equals(bill.getAccount().getId())
                        && personId.equals(bill.getAccount().getPersonId()))
                                // the last successful operation recorded the bill's final owner from the entity, not from the path
                .orElseGet(() -> auditEventRepository.findFirstByBillIdAndOutcomeOrderByOccurredAtDesc(billId, AuditOutcome.SUCCESS)
                        .map(last -> personId.equals(last.getPersonId()) && accountId.equals(last.getAccountId()))
                        .orElse(false));
        if (!owned) {
            throw new BillNotFoundException("Bill with id = " + billId + " not found.");
        }
        return auditEventRepository.findByBillIdOrCounterpartyBillIdOrderByOccurredAtAsc(billId, billId);
    }

    private AuditEvent persist(AuditEvent auditEvent) {
        AuditEvent saved = auditEventRepository.save(auditEvent);
        String line = toJson(saved);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    AUDIT_LOG.info(line);
                }
            });
        } else {
            AUDIT_LOG.info(line);
        }
        return saved;
    }

    private String toJson(AuditEvent auditEvent) {
        try {
            return objectMapper.writeValueAsString(auditEvent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit event could not be serialized", e);
        }
    }
}
