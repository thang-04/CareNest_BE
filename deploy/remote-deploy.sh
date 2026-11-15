#!/usr/bin/env bash
# Chạy trên VPS bởi .github/workflows/deploy.yml (cả production lẫn dev); chạy tay: docs/system/INCIDENT_RUNBOOK.md.
#   remote-deploy.sh [deploy]   kiểm trước → backup DB → đưa file mới vào → up → chờ health; fail ⇒ tự rollback
#   remote-deploy.sh rollback   quay về trạng thái ghi ở .rollback/state.env (CI gọi khi smoke test HTTPS fail)
# Cwd = thư mục BE trên VPS. File mới do CI chép vào .incoming/ (cùng đường dẫn như trong repo).
# Job luôn thoát mã 1 khi đã rollback, để CI báo đỏ.
set -euo pipefail

MODE=${1:-deploy}
: "${DEPLOY_ENV:?production | dev}"
: "${COMPOSE_FILE:?vd. compose.vps.yaml}"
: "${ENV_EXAMPLE:?vd. deploy/vps.env.example}"
: "${MANAGED_PATHS:?danh sách file/thư mục CI chép vào, cách nhau bởi dấu cách}"
: "${API_HEALTH_URL:?}"
: "${KC_HEALTH_URL:?}"
BACKUP_DIR=${BACKUP_ROOT:-/data/thangnd/CAPSTONE/backups}/$DEPLOY_ENV
KEEP=5
STATE_DIR=.rollback
STATE_FILE=$STATE_DIR/state.env
PG_NAME_FILTER=carenest-be-postgres   # Postgres thuộc stack production, dev dùng chung

log() { echo "[deploy:$DEPLOY_ENV] $*"; }
die() { echo "[deploy:$DEPLOY_ENV] ERROR: $*" >&2; exit 1; }

# dc old|new <args>: compose với bộ image tương ứng. File compose đang ở chỗ chạy (cũ trước khi áp, mới sau khi áp)
dc() {
  local which=$1; shift
  if [ "$which" = old ]; then
    env API_IMAGE="$OLD_API" KEYCLOAK_IMAGE="$OLD_KC" docker compose --project-directory "$PWD" -f "$COMPOSE_FILE" "$@"
  else
    env API_IMAGE="$NEW_API" KEYCLOAK_IMAGE="$NEW_KC" docker compose --project-directory "$PWD" -f "$COMPOSE_FILE" "$@"
  fi
}

pg_sql() { docker exec "$PG" psql -U "$PG_SUPER" -v ON_ERROR_STOP=1 -At "$@"; }

flyway_rank() { pg_sql -d "$1" -c 'select coalesce(max(installed_rank),0) from flyway_schema_history' 2>/dev/null || echo 0; }

wait_healthy() {
  local i
  for i in $(seq 1 60); do
    if curl -fsS -o /dev/null "$API_HEALTH_URL" && curl -fsS -o /dev/null "$KC_HEALTH_URL"; then return 0; fi
    sleep 3
  done
  return 1
}

# Mọi thao tác phá DB đi qua đây: DB phải thuộc đúng môi trường, dump phải nằm trong thư mục backup của môi trường
restore_db() {
  local db=$1 owner=$2 dump=$3 role_flag=()
  case "$DEPLOY_ENV:$db" in
    dev:carenest_dev|dev:keycloak_dev) role_flag=(--role="$owner") ;;
    production:*_dev) die "refuse to restore dev database $db during production deploy" ;;
    production:*) ;;
    *) die "refuse to restore database $db in environment $DEPLOY_ENV" ;;
  esac
  case "$dump" in "$BACKUP_DIR"/*) ;; *) die "refuse dump outside $BACKUP_DIR: $dump" ;; esac
  [ -s "$dump" ] || die "dump $dump is missing or empty"
  pg_sql -d postgres -c "DROP DATABASE \"$db\" WITH (FORCE)" -c "CREATE DATABASE \"$db\" OWNER \"$owner\"" \
    -c "REVOKE CONNECT ON DATABASE \"$db\" FROM PUBLIC"
  docker exec -i "$PG" pg_restore -U "$PG_SUPER" -d "$db" --no-owner "${role_flag[@]}" < "$dump"
}

prune_backups() {
  local pattern
  for pattern in app kc failed; do
    # shellcheck disable=SC2012
    { ls -1t "$BACKUP_DIR"/"$DEPLOY_ENV-$pattern"-*.dump 2>/dev/null || true; } | tail -n +$((KEEP + 1)) | xargs -r rm -f --
  done
}

locate_postgres() {
  PG=$(docker ps -qf "name=$PG_NAME_FILTER" | head -1)
  [ -n "$PG" ] || return 1
  PG_SUPER=$(docker exec "$PG" printenv POSTGRES_USER)
  if [ "$DEPLOY_ENV" = dev ]; then
    APP_DB=carenest_dev; KC_DB=keycloak_dev; APP_OWNER=carenest_dev
  else
    APP_DB=$(sed -n 's/^POSTGRES_DB=\(.\+\)$/\1/p' .env | tail -1); APP_DB=${APP_DB:-carenest}
    KC_DB=keycloak; APP_OWNER=$PG_SUPER
  fi
}

# Quay về trạng thái ghi trong state.env; luôn trả mã 1 để job đỏ
do_rollback() {
  local reason=$1
  log "ROLLBACK START: $reason"
  # shellcheck disable=SC1090
  . "$STATE_FILE"
  [ "${HAVE_OLD:-0}" = 1 ] || { log "ROLLBACK FAILED: no previous version recorded (first deploy)"; return 1; }
  dc new stop api || true
  if [ -f "$STATE_DIR/files.tar" ]; then tar -C "$PWD" -xf "$STATE_DIR/files.tar" || { log "ROLLBACK FAILED: cannot restore files"; return 1; }; fi
  if [ -n "${APP_DUMP:-}" ]; then
    locate_postgres || { log "ROLLBACK FAILED: postgres is not running"; return 1; }
    local now; now=$(flyway_rank "$APP_DB")
    if [ "$now" != "$RANK_BEFORE" ]; then
      log "flyway rank $RANK_BEFORE → $now: restoring $APP_DB from $APP_DUMP"
      docker exec "$PG" pg_dump -U "$PG_SUPER" -Fc "$APP_DB" > "$BACKUP_DIR/$DEPLOY_ENV-failed-$(date +%Y%m%d-%H%M%S).dump" || true
      restore_db "$APP_DB" "$APP_OWNER" "$APP_DUMP" || { log "ROLLBACK FAILED: database restore failed, api left stopped"; return 1; }
    fi
  fi
  dc old up -d --no-build --remove-orphans || { log "ROLLBACK FAILED: cannot start previous images"; return 1; }
  if wait_healthy; then
    log "ROLLED BACK to $OLD_API ($reason)"
    [ "${KC_DUMP:-}" = "" ] || log "NOTE: Keycloak database was NOT restored; dump $KC_DUMP (INCIDENT_RUNBOOK.md mục 5.4)"
    return 0
  fi
  dc old ps || true; dc old logs --tail 80 api keycloak || true
  log "ROLLBACK FAILED: previous version is not healthy"
  return 1
}

if [ "$MODE" = rollback ]; then
  [ -f "$STATE_FILE" ] || die "no $STATE_FILE"
  # shellcheck disable=SC1090
  . "$STATE_FILE"
  do_rollback "${2:-requested}" || true
  exit 1
fi

# ---------- 1. Kiểm trước (chưa đụng gì) ----------
NEW_API=${API_IMAGE:?}; NEW_KC=${KEYCLOAK_IMAGE:?}
INCOMING=.incoming
test -f .env || die ".env missing in $PWD"
for f in "$INCOMING/$COMPOSE_FILE" "$INCOMING/$ENV_EXAMPLE"; do test -f "$f" || die "missing $f (CI upload)"; done

missing=$(comm -23 \
  <(sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=.*/\1/p' "$INCOMING/$ENV_EXAMPLE" | sort -u) \
  <(sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=.*/\1/p' .env | sort -u) | tr '\n' ' ')
[ -z "$missing" ] || die "keys in $ENV_EXAMPLE but not in .env: $missing"
env API_IMAGE="$NEW_API" KEYCLOAK_IMAGE="$NEW_KC" docker compose --project-directory "$PWD" -f "$INCOMING/$COMPOSE_FILE" config -q \
  || die "docker compose config failed for the new compose file"
docker pull "$NEW_API"
docker pull "$NEW_KC"

# ---------- 2. Ghi trạng thái cũ ----------
OLD_API=; OLD_KC=; HAVE_OLD=0
old_api_id=$(env API_IMAGE="$NEW_API" KEYCLOAK_IMAGE="$NEW_KC" docker compose --project-directory "$PWD" -f "$COMPOSE_FILE" ps -aq api 2>/dev/null | head -1 || true)
old_kc_id=$(env API_IMAGE="$NEW_API" KEYCLOAK_IMAGE="$NEW_KC" docker compose --project-directory "$PWD" -f "$COMPOSE_FILE" ps -aq keycloak 2>/dev/null | head -1 || true)
if [ -n "$old_api_id" ] && [ -n "$old_kc_id" ]; then
  OLD_API=$(docker inspect -f '{{.Config.Image}}' "$old_api_id")
  OLD_KC=$(docker inspect -f '{{.Config.Image}}' "$old_kc_id")
  HAVE_OLD=1
fi

PG=; PG_SUPER=
if ! locate_postgres; then
  [ "$DEPLOY_ENV" = production ] && [ "$HAVE_OLD" = 0 ] || die "Postgres of the production stack is not running"
  log "no postgres container yet: first install, no backup"
fi
install -m 700 -d "$BACKUP_DIR" "$STATE_DIR"

tracked=()
for p in $MANAGED_PATHS; do [ -e "$p" ] && tracked+=("$p"); done
if [ "${#tracked[@]}" -gt 0 ]; then tar -cf "$STATE_DIR/files.tar" "${tracked[@]}"; else rm -f "$STATE_DIR/files.tar"; fi

APP_DUMP=; KC_DUMP=; RANK_BEFORE=0
write_state() {
  {
    printf 'HAVE_OLD=%q\nOLD_API=%q\nOLD_KC=%q\nNEW_API=%q\nNEW_KC=%q\n' "$HAVE_OLD" "$OLD_API" "$OLD_KC" "$NEW_API" "$NEW_KC"
    printf 'APP_DUMP=%q\nKC_DUMP=%q\nRANK_BEFORE=%q\n' "$APP_DUMP" "$KC_DUMP" "$RANK_BEFORE"
  } > "$STATE_FILE"
}
write_state

# ---------- 3–4. Tag đổi ⇒ dừng api, dump DB, kiểm dump ----------
if [ "$HAVE_OLD" = 1 ] && [ -n "$PG" ] && { [ "$OLD_API" != "$NEW_API" ] || [ "$OLD_KC" != "$NEW_KC" ]; }; then
  size=$(pg_sql -d "$APP_DB" -c 'select pg_database_size(current_database())')
  avail=$(df -Pk "$BACKUP_DIR" | awk 'NR==2{print $4 * 1024}')
  [ "$avail" -gt $((size * 2)) ] || die "not enough disk in $BACKUP_DIR: free $avail bytes, database $size bytes"

  ts=$(date +%Y%m%d-%H%M%S)
  log "stopping api and dumping $APP_DB"
  dc old stop api
  dump="$BACKUP_DIR/$DEPLOY_ENV-app-$ts.dump"
  if ! { (umask 077; docker exec "$PG" pg_dump -U "$PG_SUPER" -Fc "$APP_DB" > "$dump") \
         && docker exec -i "$PG" pg_restore -l < "$dump" > /dev/null; }; then
    rm -f "$dump"
    dc old start api || true
    die "pg_dump of $APP_DB failed; previous api restarted, nothing deployed"
  fi
  APP_DUMP=$dump
  RANK_BEFORE=$(flyway_rank "$APP_DB")
  if [ "$OLD_KC" != "$NEW_KC" ]; then
    kc_dump="$BACKUP_DIR/$DEPLOY_ENV-kc-$ts.dump"
    if (umask 077; docker exec "$PG" pg_dump -U "$PG_SUPER" -Fc "$KC_DB" > "$kc_dump") && docker exec -i "$PG" pg_restore -l < "$kc_dump" > /dev/null; then
      KC_DUMP=$kc_dump
    else
      rm -f "$kc_dump"
      dc old start api || true
      die "pg_dump of $KC_DB failed; previous api restarted, nothing deployed"
    fi
  fi
  write_state
fi

# ---------- 5. Đưa file mới vào chỗ, khởi động, chờ health ----------
apply_and_wait() {
  local p
  for p in $MANAGED_PATHS; do
    [ -e "$INCOMING/$p" ] || { log "missing $INCOMING/$p"; return 1; }
    mkdir -p "$(dirname "$p")"
    cp -a "$INCOMING/$p" "$(dirname "$p")/" || return 1
  done
  dc new up -d --no-build --remove-orphans || return 1
  wait_healthy
}

if ! apply_and_wait; then
  dc new ps || true; dc new logs --tail 80 api keycloak || true
  if do_rollback "new version not healthy"; then log "deploy failed, previous version restored"; fi
  exit 1
fi

# ---------- 7. Health đạt ----------
printf 'API_IMAGE=%s\nKEYCLOAK_IMAGE=%s\n' "$NEW_API" "$NEW_KC" > .deploy.env
# So sánh image ID để giữ cả tag được container production hoặc dev dùng, cùng bản liền trước.
used_images=$(mktemp)
docker ps -aq | xargs -r docker inspect --format '{{.Image}}' > "$used_images"
while IFS= read -r tag; do
  if [ "$tag" = "$NEW_API" ] || [ "$tag" = "$NEW_KC" ] || [ "$tag" = "$OLD_API" ] || [ "$tag" = "$OLD_KC" ] || [ "${tag##*:}" = '<none>' ]; then continue; fi
  image_id=$(docker image inspect --format '{{.Id}}' "$tag")
  if ! grep -Fxq "$image_id" "$used_images"; then docker rmi "$tag"; fi
done < <(docker images "${NEW_API%:*}" --format '{{.Repository}}:{{.Tag}}')
rm -f "$used_images"
docker image prune -f
prune_backups
log "deploy OK: $NEW_API"
