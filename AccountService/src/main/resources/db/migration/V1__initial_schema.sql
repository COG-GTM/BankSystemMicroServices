CREATE TABLE public.accounts (
    id            UUID         NOT NULL,
    name          VARCHAR(255) NOT NULL,
    creation_date DATE         NOT NULL,
    person_id     UUID         NOT NULL,
    CONSTRAINT accounts_pkey PRIMARY KEY (id)
);

CREATE TABLE public.bills (
    id           UUID           NOT NULL,
    currency     VARCHAR(255)   NOT NULL,
    amount       NUMERIC(19, 2),
    is_overdraft BOOLEAN        NOT NULL,
    account_id   UUID           NOT NULL,
    CONSTRAINT bills_pkey PRIMARY KEY (id),
    CONSTRAINT bills_account_id_fkey FOREIGN KEY (account_id)
        REFERENCES public.accounts (id) ON DELETE CASCADE
);

CREATE TABLE public.payments (
    id       UUID           NOT NULL,
    currency VARCHAR(255)   NOT NULL,
    amount   NUMERIC(19, 2) CHECK (amount >= 0),
    time     TIMESTAMP,
    message  VARCHAR(255),
    bill_id  UUID           NOT NULL,
    CONSTRAINT payments_pkey PRIMARY KEY (id),
    CONSTRAINT payments_bill_id_fkey FOREIGN KEY (bill_id)
        REFERENCES public.bills (id) ON DELETE CASCADE
);

CREATE TABLE public.adjustments (
    id       UUID           NOT NULL,
    currency VARCHAR(255)   NOT NULL,
    amount   NUMERIC(19, 2) CHECK (amount >= 0),
    time     TIMESTAMP,
    message  VARCHAR(255),
    bill_id  UUID           NOT NULL,
    CONSTRAINT adjustments_pkey PRIMARY KEY (id),
    CONSTRAINT adjustments_bill_id_fkey FOREIGN KEY (bill_id)
        REFERENCES public.bills (id) ON DELETE CASCADE
);

CREATE TABLE public.transfers (
    id                  UUID           NOT NULL,
    currency            VARCHAR(255)   NOT NULL,
    amount              NUMERIC(19, 2) CHECK (amount >= 0),
    time                TIMESTAMP,
    message             VARCHAR(255),
    source_bill_id      UUID           NOT NULL,
    beneficiary_bill_id UUID           NOT NULL,
    CONSTRAINT transfers_pkey PRIMARY KEY (id),
    CONSTRAINT transfers_source_bill_id_fkey FOREIGN KEY (source_bill_id)
        REFERENCES public.bills (id) ON DELETE CASCADE,
    CONSTRAINT transfers_beneficiary_bill_id_fkey FOREIGN KEY (beneficiary_bill_id)
        REFERENCES public.bills (id) ON DELETE CASCADE
);
