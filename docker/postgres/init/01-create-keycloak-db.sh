#!/bin/sh
# Tạo DB + user riêng cho Keycloak trên cùng PostgreSQL (chỉ chạy khi volume postgres còn trống).
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
CREATE ROLE keycloak LOGIN PASSWORD '${KEYCLOAK_DB_PASSWORD}';
CREATE DATABASE keycloak OWNER keycloak;
EOSQL
