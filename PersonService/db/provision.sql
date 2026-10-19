-- Provisions the PersonService database with a least-privilege application role.
--
-- Run as a PostgreSQL administrator, connected to the PersonService database:
--   psql -d person-service-db -v app_password="$PERSON_DB_PASSWORD" -f PersonService/db/provision.sql
--
-- person_service_owner (NOLOGIN) owns the schema objects; person_service_app is the
-- login role the service connects as (PERSON_DB_USERNAME) and has DML on public.people only.
-- Re-running the script is safe and rotates the application password.

\set ON_ERROR_STOP on

SELECT 'CREATE ROLE person_service_owner NOLOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'person_service_owner') \gexec

SELECT 'CREATE ROLE person_service_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'person_service_app') \gexec

SELECT format('ALTER ROLE person_service_app WITH LOGIN PASSWORD %L', :'app_password') \gexec

SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database()) \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO person_service_app', current_database()) \gexec

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO person_service_app;

CREATE TABLE IF NOT EXISTS public.people (
    id           uuid         NOT NULL,
    birthdate    date         NOT NULL,
    email        varchar(255),
    first_name   varchar(30),
    last_name    varchar(30),
    phone_number varchar(255),
    CONSTRAINT people_pkey PRIMARY KEY (id)
);
ALTER TABLE public.people OWNER TO person_service_owner;

REVOKE ALL ON public.people FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.people TO person_service_app;
