package ru.kondrashov.commonservice.exceptions;

import java.util.UUID;

/**
 * Thrown when the person delete cascade (accounts, then person) stops part way.
 * {@link #isAccountsDeleted()} tells whether AccountService already removed the person's accounts.
 */
public class PersonDeletionException extends RuntimeException {

    public enum Step { DELETE_ACCOUNTS, DELETE_PERSON }

    private final UUID personId;
    private final Step failedStep;
    private final boolean accountsDeleted;

    public PersonDeletionException(UUID personId, Step failedStep, boolean accountsDeleted, Throwable cause) {
        super(describe(personId, failedStep, accountsDeleted, cause), cause);
        this.personId = personId;
        this.failedStep = failedStep;
        this.accountsDeleted = accountsDeleted;
    }

    private static String describe(UUID personId, Step failedStep, boolean accountsDeleted, Throwable cause) {
        String state = accountsDeleted
                ? "the person's accounts were already deleted but the person record still exists; retry the delete to finish"
                : "nothing was deleted";
        return "Deleting person " + personId + " failed at step " + failedStep + ": " + state
                + " (cause: " + cause.getMessage() + ")";
    }

    public UUID getPersonId() {
        return personId;
    }

    public Step getFailedStep() {
        return failedStep;
    }

    public boolean isAccountsDeleted() {
        return accountsDeleted;
    }
}
