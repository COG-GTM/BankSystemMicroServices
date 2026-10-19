CREATE TABLE public.people (
    id           UUID         NOT NULL,
    first_name   VARCHAR(30),
    last_name    VARCHAR(30),
    birthdate    DATE         NOT NULL,
    phone_number VARCHAR(255),
    email        VARCHAR(255),
    CONSTRAINT people_pkey PRIMARY KEY (id)
);
