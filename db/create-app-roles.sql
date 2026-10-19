-- Creates least-privilege login roles for AccountService and PersonService and makes each role the
-- owner of its own databases (needed because hibernate.hbm2ddl.auto=create rebuilds the schema).
-- Run once per environment as a superuser, passing passwords from your secret store, e.g.:
--   psql -h <host> -U postgres -d postgres \
--        -v account_password="$ACCOUNT_DB_PASSWORD" -v person_password="$PERSON_DB_PASSWORD" \
--        -f db/create-app-roles.sql
-- Re-running the script rotates both passwords.
\set ON_ERROR_STOP on

SELECT 'CREATE ROLE account_service' WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'account_service')\gexec
ALTER ROLE account_service WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'account_password';

SELECT 'CREATE ROLE person_service' WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'person_service')\gexec
ALTER ROLE person_service WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'person_password';

SELECT format('CREATE DATABASE %I OWNER %I', db, owner)
FROM (VALUES ('account-service-db', 'account_service'),
             ('account-service-db-test', 'account_service'),
             ('person-service-db', 'person_service'),
             ('person-service-db-test', 'person_service')) AS t(db, owner)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = db)\gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', db, owner), format('REVOKE ALL ON DATABASE %I FROM PUBLIC', db)
FROM (VALUES ('account-service-db', 'account_service'),
             ('account-service-db-test', 'account_service'),
             ('person-service-db', 'person_service'),
             ('person-service-db-test', 'person_service')) AS t(db, owner)\gexec

-- Lock down the public schema of each database so only the owning service role can create objects in it.
\connect "account-service-db"
ALTER SCHEMA public OWNER TO account_service;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
\connect "account-service-db-test"
ALTER SCHEMA public OWNER TO account_service;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
\connect "person-service-db"
ALTER SCHEMA public OWNER TO person_service;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
\connect "person-service-db-test"
ALTER SCHEMA public OWNER TO person_service;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
