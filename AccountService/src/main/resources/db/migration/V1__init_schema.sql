CREATE TABLE accounts (
    id            uuid         NOT NULL,
    creation_date date,
    name          varchar(255),
    person_id     uuid,
    CONSTRAINT accounts_pkey PRIMARY KEY (id)
);

CREATE TABLE bills (
    id           uuid NOT NULL,
    amount       numeric(19, 2),
    currency     varchar(255),
    is_overdraft boolean,
    account_id   uuid,
    CONSTRAINT bills_pkey PRIMARY KEY (id),
    CONSTRAINT fk_bills_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE
);

CREATE TABLE payments (
    id       uuid NOT NULL,
    amount   numeric(19, 2),
    currency varchar(255),
    message  varchar(255),
    time     timestamp,
    bill_id  uuid,
    CONSTRAINT payments_pkey PRIMARY KEY (id),
    CONSTRAINT fk_payments_bill FOREIGN KEY (bill_id) REFERENCES bills (id) ON DELETE CASCADE
);

CREATE TABLE adjustments (
    id       uuid NOT NULL,
    amount   numeric(19, 2),
    currency varchar(255),
    message  varchar(255),
    time     timestamp,
    bill_id  uuid,
    CONSTRAINT adjustments_pkey PRIMARY KEY (id),
    CONSTRAINT fk_adjustments_bill FOREIGN KEY (bill_id) REFERENCES bills (id) ON DELETE CASCADE
);

CREATE TABLE transfers (
    id                  uuid NOT NULL,
    amount              numeric(19, 2),
    currency            varchar(255),
    message             varchar(255),
    time                timestamp,
    beneficiary_bill_id uuid,
    source_bill_id      uuid,
    CONSTRAINT transfers_pkey PRIMARY KEY (id),
    CONSTRAINT fk_transfers_beneficiary_bill FOREIGN KEY (beneficiary_bill_id) REFERENCES bills (id) ON DELETE CASCADE,
    CONSTRAINT fk_transfers_source_bill FOREIGN KEY (source_bill_id) REFERENCES bills (id) ON DELETE CASCADE
);
