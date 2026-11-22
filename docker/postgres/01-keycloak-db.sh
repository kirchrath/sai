#!/bin/bash
# Legt beim ersten Start eine eigene Datenbank fuer Keycloak an.
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
    CREATE DATABASE keycloak OWNER "$POSTGRES_USER";
SQL
