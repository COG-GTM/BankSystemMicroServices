CREATE TABLE IF NOT EXISTS public.people (
    id           uuid         NOT NULL,
    birthdate    date         NOT NULL,
    email        varchar(255),
    first_name   varchar(30),
    last_name    varchar(30),
    phone_number varchar(255),
    CONSTRAINT people_pkey PRIMARY KEY (id)
);
