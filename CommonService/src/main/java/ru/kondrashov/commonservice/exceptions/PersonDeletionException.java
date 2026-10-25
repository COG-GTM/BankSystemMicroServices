package ru.kondrashov.commonservice.exceptions;

import java.util.UUID;

/**
 * Thrown when the person delete cascade (accounts, then person) stops part way.
 * {@link #isAccountsDeleted()} tells whether AccountService already removed the person's accounts.
 * {@link #isOutcomeKnown()} is false when the failed step got no HTTP response, so it may have
 * completed anyway. Both steps are idempotent, so repeating the delete is always safe.
 */
public class PersonDeletionException extends RuntimeException {

    public enum Step { DELETE_ACCOUNTS, DELETE_PERSON }

    private final UUID personId;
    private final Step failedStep;
    private final boolean accountsDeleted;
    private final boolean outcomeKnown;

    public PersonDeletionException(UUID personId, Step failedStep, boolean outcomeKnown, Throwable cause) {
        super(describe(personId, failedStep, outcomeKnown, cause), cause);
        this.personId = personId;
        this.failedStep = failedStep;
        this.accountsDeleted = failedStep == Step.DELETE_PERSON;
        this.outcomeKnown = outcomeKnown;
    }

    private static String describe(UUID personId, Step failedStep, boolean outcomeKnown, Throwable cause) {
        String state;
        if (failedStep == Step.DELETE_ACCOUNTS) {
            state = outcomeKnown
                    ? "nothing was deleted"
                    : "AccountService did not answer, so the person's accounts may or may not have been deleted;"
                      + " the person record was not touched; retry the delete to finish";
        } else {
            state = outcomeKnown
                    ? "the person's accounts were already deleted but the person record still exists;"
                      + " retry the delete to finish"
                    : "the person's accounts were already deleted; PersonService did not answer, so the person record"
                      + " may or may not have been deleted; retry the delete to finish";
        }
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

    public boolean isOutcomeKnown() {
        return outcomeKnown;
    }
}
