package ru.kondrashov.accountservice.audit;

public enum AuditOperation {
    BILL_CREATED,
    BILL_UPDATED,
    BILL_DELETED,
    PAYMENT,
    ADJUSTMENT,
    TRANSFER
}
