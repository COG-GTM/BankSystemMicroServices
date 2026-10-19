CREATE TABLE IF NOT EXISTS public.accounts (
    id            uuid         NOT NULL,
    creation_date date         NOT NULL,
    name          varchar(255) NOT NULL,
    person_id     uuid         NOT NULL,
    CONSTRAINT accounts_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.bills (
    id           uuid           NOT NULL,
    amount       numeric(19, 2),
    currency     varchar(255)   NOT NULL,
    is_overdraft boolean        NOT NULL,
    account_id   uuid           NOT NULL,
    CONSTRAINT bills_pkey PRIMARY KEY (id),
    CONSTRAINT fk_bills_account FOREIGN KEY (account_id) REFERENCES public.accounts (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.payments (
    id       uuid           NOT NULL,
    amount   numeric(19, 2) CHECK (amount >= 0),
    currency varchar(255)   NOT NULL,
    message  varchar(255),
    time     timestamp,
    bill_id  uuid           NOT NULL,
    CONSTRAINT payments_pkey PRIMARY KEY (id),
    CONSTRAINT fk_payments_bill FOREIGN KEY (bill_id) REFERENCES public.bills (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.adjustments (
    id       uuid           NOT NULL,
    amount   numeric(19, 2) CHECK (amount >= 0),
    currency varchar(255)   NOT NULL,
    message  varchar(255),
    time     timestamp,
    bill_id  uuid           NOT NULL,
    CONSTRAINT adjustments_pkey PRIMARY KEY (id),
    CONSTRAINT fk_adjustments_bill FOREIGN KEY (bill_id) REFERENCES public.bills (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.transfers (
    id                  uuid           NOT NULL,
    amount              numeric(19, 2) CHECK (amount >= 0),
    currency            varchar(255)   NOT NULL,
    message             varchar(255),
    time                timestamp,
    beneficiary_bill_id uuid           NOT NULL,
    source_bill_id      uuid           NOT NULL,
    CONSTRAINT transfers_pkey PRIMARY KEY (id),
    CONSTRAINT fk_transfers_beneficiary_bill FOREIGN KEY (beneficiary_bill_id) REFERENCES public.bills (id) ON DELETE CASCADE,
    CONSTRAINT fk_transfers_source_bill FOREIGN KEY (source_bill_id) REFERENCES public.bills (id) ON DELETE CASCADE
);
