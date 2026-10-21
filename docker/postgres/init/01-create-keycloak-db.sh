#!/bin/sh
# Tạo DB + user riêng cho Keycloak trên cùng PostgreSQL (chỉ chạy khi volume postgres còn trống).
set -eu

# Truyền mật khẩu qua biến psql (:'pw' tự escape) để ký tự đặc biệt không phá câu SQL
psql -v ON_ERROR_STOP=1 -v pw="$KEYCLOAK_DB_PASSWORD" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE keycloak LOGIN PASSWORD :'pw';
CREATE DATABASE keycloak OWNER keycloak;
EOSQL
