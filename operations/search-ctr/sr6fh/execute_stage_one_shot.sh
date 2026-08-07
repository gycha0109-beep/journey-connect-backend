#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
CONTRACT_FILE="$ROOT_DIR/operations/search-ctr/sr6fh/stage-execution-contract.env"
RAW_DIR="${SR6FH_RAW_EVIDENCE_DIR:-$ROOT_DIR/build/sr6fh/raw}"
SAFE_DIR="${SR6FH_SAFE_EVIDENCE_DIR:-$ROOT_DIR/build/sr6fh/safe}"
RUNTIME_DB_ENV="$RAW_DIR/runtime-db.env"
ENDPOINT_EVIDENCE="$RAW_DIR/endpoint-evidence.json"
REVOKE_REQUIRED=0
REVOKE_COMPLETED=0

mkdir -p "$RAW_DIR" "$SAFE_DIR"

fail() {
  printf 'SR6FH_EXECUTION=FAILED: %s\n' "$1" >&2
  exit 1
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "missing required environment variable: $name"
}

sanitize_evidence() {
  python3 "$ROOT_DIR/operations/search-ctr/sr6fh/sanitize_stage_evidence.py" \
    "$RAW_DIR" "$SAFE_DIR" || true
}

revoke_membership() {
  if [[ "$REVOKE_REQUIRED" -eq 1 && "$REVOKE_COMPLETED" -eq 0 ]]; then
    if admin_psql \
      -v sr6fg_environment="$SR6FH_AUTHORIZED_ENVIRONMENT" \
      -v sr6fg_approval_ref="$SR6FH_AUTHORIZED_APPROVAL_REF" \
      -f "$ROOT_DIR/operations/search-ctr/sr6fg/03_revoke_stage_reliability.sql" \
      >"$RAW_DIR/revoke.log" 2>&1; then
      REVOKE_COMPLETED=1
    else
      printf 'SR6FH_EMERGENCY_REVOKE=FAILED\n' >&2
      return 1
    fi
  fi
}

cleanup() {
  local exit_code=$?
  set +e
  revoke_membership
  local revoke_code=$?
  sanitize_evidence
  if [[ "$revoke_code" -ne 0 ]]; then
    printf 'SR6FH_SECURITY_STATE=REVOKE_FAILED\n' >&2
    exit 97
  fi
  exit "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

set -a
# shellcheck disable=SC1090
source "$CONTRACT_FILE"
set +a

[[ "$SR6FH_EXECUTION_STATUS" == "READY_FOR_ONE_SHOT" ]] \
  || fail "execution contract is not READY_FOR_ONE_SHOT"
[[ "$SR6FH_AUTHORIZED_STAGE_ENDPOINT_SHA256" =~ ^[0-9a-f]{64}$ ]] \
  || fail "authorized stage endpoint fingerprint is unassigned"
[[ "${SR6FH_CONFIRMATION:-}" == "EXECUTE_SR6FH_STAGE_ONE_SHOT" ]] \
  || fail "manual confirmation does not match"
[[ "${SR6FH_EXPECTED_SOURCE_SHA:-}" =~ ^[0-9a-f]{40}$ ]] \
  || fail "expected source SHA must be a full lowercase commit SHA"
checked_out_sha="$(git -C "$ROOT_DIR" rev-parse HEAD)"
[[ "$checked_out_sha" == "$SR6FH_EXPECTED_SOURCE_SHA" ]] \
  || fail "workflow checkout SHA does not match the approved source SHA"
[[ "${SR6FH_PRODUCER_BUILD_ID:-}" == "${SR6FH_AUTHORIZED_PRODUCER_PREFIX}${SR6FH_EXPECTED_SOURCE_SHA}" ]] \
  || fail "producer build ID is not bound to the exact source SHA"
[[ "${SR6FH_REQUESTED_APPROVAL_REF:-}" == "$SR6FH_AUTHORIZED_APPROVAL_REF" ]] \
  || fail "approval reference does not match the authorization"
[[ "${SR6FH_REQUESTED_WINDOW_START:-}" == "$SR6FH_AUTHORIZED_WINDOW_START" ]] \
  || fail "window start does not match the authorization"
[[ "$SR6FH_FINALITY_WRITE" == "DISABLED" ]] \
  || fail "finality write must remain disabled"

for name in \
  SR6FH_STAGE_ADMIN_DATABASE_URL \
  SR6FH_STAGE_ADMIN_USERNAME \
  SR6FH_STAGE_ADMIN_PASSWORD \
  SPRING_DATASOURCE_URL \
  SPRING_DATASOURCE_USERNAME \
  SPRING_DATASOURCE_PASSWORD \
  APP_SECURITY_JWT_SECRET; do
  require_env "$name"
done

export SR6FH_ENDPOINT_EVIDENCE_FILE="$ENDPOINT_EVIDENCE"
export SR6FH_RUNTIME_DB_ENV_FILE="$RUNTIME_DB_ENV"
python3 "$ROOT_DIR/operations/search-ctr/sr6fh/validate_stage_endpoints.py" \
  >"$RAW_DIR/endpoint-validation.log" 2>&1
# shellcheck disable=SC1090
source "$RUNTIME_DB_ENV"
export PGHOST PGPORT PGDATABASE
export PGSSLMODE="${PGSSLMODE:-}"

admin_psql() {
  PGUSER="$SR6FH_STAGE_ADMIN_USERNAME" \
  PGPASSWORD="$SR6FH_STAGE_ADMIN_PASSWORD" \
    psql "$@"
}

backend_psql() {
  PGUSER="$SPRING_DATASOURCE_USERNAME" \
  PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" \
    psql "$@"
}

printf '{"contractVersion":"search-ctr-stage-one-shot-run-v1","sourceSha":"%s","environment":"stage","windowStart":"%s","windowEnd":"%s","approvalRef":"%s","producerBuildId":"%s"}\n' \
  "$SR6FH_EXPECTED_SOURCE_SHA" \
  "$SR6FH_AUTHORIZED_WINDOW_START" \
  "$SR6FH_AUTHORIZED_WINDOW_END" \
  "$SR6FH_AUTHORIZED_APPROVAL_REF" \
  "$SR6FH_PRODUCER_BUILD_ID" \
  >"$RAW_DIR/execution-request.json"

admin_psql \
  -v sr6fh_environment="$SR6FH_AUTHORIZED_ENVIRONMENT" \
  -v sr6fh_approval_ref="$SR6FH_AUTHORIZED_APPROVAL_REF" \
  -v sr6fh_window_start="$SR6FH_AUTHORIZED_WINDOW_START" \
  -v sr6fh_producer_build_id="$SR6FH_PRODUCER_BUILD_ID" \
  -f "$ROOT_DIR/operations/search-ctr/sr6fh/01_preflight_stage.sql" \
  >"$RAW_DIR/preflight.log" 2>&1

admin_psql \
  -v sr6fg_environment="$SR6FH_AUTHORIZED_ENVIRONMENT" \
  -v sr6fg_approval_ref="$SR6FH_AUTHORIZED_APPROVAL_REF" \
  -f "$ROOT_DIR/operations/search-ctr/sr6fg/01_grant_stage_reliability.sql" \
  >"$RAW_DIR/grant.log" 2>&1
REVOKE_REQUIRED=1

admin_psql \
  -v sr6fg_environment="$SR6FH_AUTHORIZED_ENVIRONMENT" \
  -v sr6fg_approval_ref="$SR6FH_AUTHORIZED_APPROVAL_REF" \
  -f "$ROOT_DIR/operations/search-ctr/sr6fg/02_verify_stage_reliability.sql" \
  >"$RAW_DIR/capability-verify.log" 2>&1

export SPRING_PROFILES_ACTIVE=stage
export SPRING_MAIN_WEB_APPLICATION_TYPE=none
export SPRING_FLYWAY_ENABLED=false
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
export APP_DATABASE_ROLE_ROUTING_VERIFY_ON_STARTUP=true
export APP_DATABASE_ROLE_ROUTING_REQUIRE_RESTRICTED_LOGIN=true
export APP_DATABASE_ROLE_ROUTING_REQUIRE_RELIABILITY=true
export APP_CORS_ALLOWED_ORIGINS=http://127.0.0.1
export APP_INTELLIGENCE_SEARCH_CTR_MANUAL_ENABLED=true
export APP_INTELLIGENCE_SEARCH_CTR_MANUAL_KILL_SWITCH=false
export APP_INTELLIGENCE_SEARCH_CTR_MANUAL_ENVIRONMENT=stage
export APP_INTELLIGENCE_SEARCH_CTR_MANUAL_WINDOW_START="$SR6FH_AUTHORIZED_WINDOW_START"
export APP_INTELLIGENCE_SEARCH_CTR_MANUAL_PRODUCER_BUILD_ID="$SR6FH_PRODUCER_BUILD_ID"
export APP_INTELLIGENCE_SEARCH_CTR_MANUAL_APPROVAL_REF="$SR6FH_AUTHORIZED_APPROVAL_REF"

(
  cd "$ROOT_DIR/jc-backend"
  timeout --signal=TERM --kill-after=10s 180s \
    ./gradlew --init-script ../operations/search-ctr/sr6fh/stage-one-shot.init.gradle \
      searchCtrStageOneShot --no-build-cache --no-daemon
) >"$RAW_DIR/application-one-shot.log" 2>&1

admin_psql \
  -v sr6fh_environment="$SR6FH_AUTHORIZED_ENVIRONMENT" \
  -v sr6fh_approval_ref="$SR6FH_AUTHORIZED_APPROVAL_REF" \
  -v sr6fh_window_start="$SR6FH_AUTHORIZED_WINDOW_START" \
  -v sr6fh_producer_build_id="$SR6FH_PRODUCER_BUILD_ID" \
  -f "$ROOT_DIR/operations/search-ctr/sr6fh/04_collect_stage_evidence.sql" \
  >"$RAW_DIR/stage-result.json" 2>"$RAW_DIR/stage-result.stderr.log"

revoke_membership

admin_psql \
  -f "$ROOT_DIR/operations/search-ctr/sr6fh/05_verify_stage_revoked.sql" \
  >"$RAW_DIR/revoke-catalog-verify.log" 2>&1

set +e
backend_psql -v ON_ERROR_STOP=1 -c 'SET ROLE jc_reliability;' \
  >"$RAW_DIR/post-revoke-negative.log" 2>&1
negative_status=$?
set -e
[[ "$negative_status" -ne 0 ]] \
  || fail "post-revoke SET ROLE unexpectedly succeeded"
printf 'SR6FH_POST_REVOKE_SET_ROLE=DENIED\n' >"$RAW_DIR/post-revoke-status.log"

sanitize_evidence
printf 'SR6FH_EXECUTION=SUCCESS\n'
