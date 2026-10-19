package ru.kondrashov.accountservice.audit;

/**
 * How the actor of an audited mutation was identified.
 * HEADER is caller-asserted (X-Actor-Id) and not authenticated by this service.
 */
public enum ActorSource {
    PRINCIPAL,
    HEADER,
    NONE,
    SYSTEM
}
