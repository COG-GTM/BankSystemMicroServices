package ru.kondrashov.accountservice.security;

public enum ClientRole {
    /** Back-office / trusted services (e.g. CommonService): any person, all operations. */
    ADMIN,
    /** A single customer: reads, payments and transfers on their own person only. */
    CUSTOMER;

    public String authority() {
        return "ROLE_" + name();
    }
}
