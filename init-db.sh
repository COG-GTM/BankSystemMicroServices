#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE "person-service-db";
    CREATE DATABASE "account-service-db";
    CREATE DATABASE "person-service-db-test";
    CREATE DATABASE "account-service-db-test";
EOSQL
