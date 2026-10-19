package ru.kondrashov.personservice.services;

import java.util.UUID;

/**
 * Checks with AccountService whether a person still has open accounts. Erasure is only allowed once all
 * accounts are settled and closed.
 */
public interface OpenAccountsVerifier {

    boolean hasOpenAccounts(UUID personId);
}
