package ru.kondrashov.accountservice.audit;

import lombok.Value;

@Value
public class AuditActor {
    String name;
    ActorSource source;
    String clientAddress;
    String requestId;
}
