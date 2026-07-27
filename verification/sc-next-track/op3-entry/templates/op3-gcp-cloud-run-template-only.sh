#!/usr/bin/env bash
set -euo pipefail

EXECUTION_MODE="${EXECUTION_MODE:-TEMPLATE_ONLY}"
ACTUAL_EXECUTION="${ACTUAL_EXECUTION:-FORBIDDEN}"
BILLING_CHANGE="${BILLING_CHANGE:-FORBIDDEN}"
IAM_MUTATION="${IAM_MUTATION:-FORBIDDEN}"
RESOURCE_CREATION="${RESOURCE_CREATION:-FORBIDDEN}"
PROJECT_ID="${PROJECT_ID:-REQUIRED_INPUT}"
PROJECT_NUMBER="${PROJECT_NUMBER:-REQUIRED_INPUT}"
REGION="${REGION:-REQUIRED_INPUT}"
SERVICE="${SERVICE:-REQUIRED_INPUT}"
REVISION="${REVISION:-REQUIRED_INPUT}"
IMAGE_DIGEST="${IMAGE_DIGEST:-REQUIRED_INPUT}"
MAX_INSTANCES="${MAX_INSTANCES:-REQUIRED_INPUT}"
INGRESS_MODE="${INGRESS_MODE:-REQUIRED_INPUT}"
WIF_POOL_ID="${WIF_POOL_ID:-REQUIRED_INPUT}"
WIF_PROVIDER_ID="${WIF_PROVIDER_ID:-REQUIRED_INPUT}"
GITHUB_REPOSITORY_ID="${GITHUB_REPOSITORY_ID:-REQUIRED_INPUT}"
GITHUB_REPOSITORY_OWNER_ID="${GITHUB_REPOSITORY_OWNER_ID:-REQUIRED_INPUT}"
GITHUB_WORKFLOW_REF="${GITHUB_WORKFLOW_REF:-REQUIRED_INPUT}"
GITHUB_REF_CONDITION="${GITHUB_REF_CONDITION:-REQUIRED_INPUT}"
DEPLOY_PRINCIPAL="${DEPLOY_PRINCIPAL:-REQUIRED_INPUT}"
RUNTIME_INVOKER="${RUNTIME_INVOKER:-REQUIRED_INPUT}"
EVIDENCE_BUCKET="${EVIDENCE_BUCKET:-REQUIRED_INPUT}"
RETENTION_DAYS="${RETENTION_DAYS:-REQUIRED_INPUT}"
COST_CEILING="${COST_CEILING:-REQUIRED_INPUT}"
TEARDOWN_DEADLINE="${TEARDOWN_DEADLINE:-REQUIRED_INPUT}"
REGION_FINAL_APPROVAL="${REGION_FINAL_APPROVAL:-REQUIRED_INPUT}"
COST_OWNER_APPROVAL="${COST_OWNER_APPROVAL:-REQUIRED_INPUT}"
TEMPLATE_RENDER_ACK="${TEMPLATE_RENDER_ACK:-NO}"

fail() {
  printf 'TEMPLATE_ERROR: %s\n' "$1" >&2
  exit 64
}

[[ "$EXECUTION_MODE" == "TEMPLATE_ONLY" ]] || fail "EXECUTION_MODE must remain TEMPLATE_ONLY"
[[ "$ACTUAL_EXECUTION" == "FORBIDDEN" ]] || fail "ACTUAL_EXECUTION must remain FORBIDDEN"
[[ "$BILLING_CHANGE" == "FORBIDDEN" ]] || fail "BILLING_CHANGE must remain FORBIDDEN"
[[ "$IAM_MUTATION" == "FORBIDDEN" ]] || fail "IAM_MUTATION must remain FORBIDDEN"
[[ "$RESOURCE_CREATION" == "FORBIDDEN" ]] || fail "RESOURCE_CREATION must remain FORBIDDEN"

required=(
  PROJECT_ID PROJECT_NUMBER REGION SERVICE REVISION IMAGE_DIGEST MAX_INSTANCES INGRESS_MODE
  WIF_POOL_ID WIF_PROVIDER_ID GITHUB_REPOSITORY_ID GITHUB_REPOSITORY_OWNER_ID
  GITHUB_WORKFLOW_REF GITHUB_REF_CONDITION DEPLOY_PRINCIPAL RUNTIME_INVOKER
  EVIDENCE_BUCKET RETENTION_DAYS COST_CEILING TEARDOWN_DEADLINE
  REGION_FINAL_APPROVAL COST_OWNER_APPROVAL
)

for name in "${required[@]}"; do
  value="${!name:-}"
  [[ -n "$value" ]] || fail "$name is empty"
  [[ "$value" != "REQUIRED_INPUT" && "$value" != "UNASSIGNED" ]] || fail "$name is not assigned"
done

[[ "$REGION" == "asia-northeast3" ]] || fail "REGION must equal separately approved asia-northeast3"
[[ "$REGION_FINAL_APPROVAL" == "YES" ]] || fail "REGION_FINAL_APPROVAL must be YES to render"
[[ "$COST_OWNER_APPROVAL" == "YES" ]] || fail "COST_OWNER_APPROVAL must be YES to render"
[[ "$TEMPLATE_RENDER_ACK" == "YES" ]] || fail "TEMPLATE_RENDER_ACK must be YES"
[[ "$MAX_INSTANCES" =~ ^[1-9][0-9]*$ ]] || fail "MAX_INSTANCES must be a positive integer"
[[ "$RETENTION_DAYS" =~ ^[1-9][0-9]*$ ]] || fail "RETENTION_DAYS must be a positive integer"
[[ "$IMAGE_DIGEST" == *@sha256:* ]] || fail "IMAGE_DIGEST must use an immutable sha256 digest"
[[ "$INGRESS_MODE" == "all" || "$INGRESS_MODE" == "internal" || "$INGRESS_MODE" == "internal-and-cloud-load-balancing" ]] || fail "INGRESS_MODE is invalid"

cat <<'HEADER'
EXECUTION_MODE=TEMPLATE_ONLY
ACTUAL_EXECUTION=FORBIDDEN
BILLING_CHANGE=FORBIDDEN
IAM_MUTATION=FORBIDDEN
RESOURCE_CREATION=FORBIDDEN
OUTPUT_MODE=WOULD_RUN_ONLY
HEADER

emit() {
  printf 'WOULD_RUN: %s\n' "$1"
}

emit "gcloud config set project \"${PROJECT_ID}\""
emit "gcloud services enable run.googleapis.com artifactregistry.googleapis.com iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com cloudresourcemanager.googleapis.com serviceusage.googleapis.com storage.googleapis.com monitoring.googleapis.com logging.googleapis.com cloudbilling.googleapis.com --project=\"${PROJECT_ID}\""
emit "gcloud run deploy \"${SERVICE}\" --image=\"${IMAGE_DIGEST}\" --revision-suffix=\"${REVISION}\" --region=\"${REGION}\" --project=\"${PROJECT_ID}\" --no-traffic --no-allow-unauthenticated --ingress=\"${INGRESS_MODE}\" --min=0 --max=\"${MAX_INSTANCES}\""
emit "gcloud run services describe \"${SERVICE}\" --region=\"${REGION}\" --project=\"${PROJECT_ID}\" --format=json"
emit "gcloud run services get-iam-policy \"${SERVICE}\" --region=\"${REGION}\" --project=\"${PROJECT_ID}\" --format=json"
emit "gcloud iam workload-identity-pools create \"${WIF_POOL_ID}\" --location=global --project=\"${PROJECT_ID}\""
emit "gcloud iam workload-identity-pools providers create-oidc \"${WIF_PROVIDER_ID}\" --location=global --workload-identity-pool=\"${WIF_POOL_ID}\" --issuer-uri=\"https://token.actions.githubusercontent.com/\" --attribute-mapping=\"google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id,attribute.workflow_ref=assertion.job_workflow_ref,attribute.ref=assertion.ref\" --attribute-condition=\"assertion.repository_id=='${GITHUB_REPOSITORY_ID}' && assertion.repository_owner_id=='${GITHUB_REPOSITORY_OWNER_ID}' && assertion.job_workflow_ref=='${GITHUB_WORKFLOW_REF}' && ${GITHUB_REF_CONDITION}\" --project=\"${PROJECT_ID}\""
emit "gcloud run services add-iam-policy-binding \"${SERVICE}\" --region=\"${REGION}\" --project=\"${PROJECT_ID}\" --member=\"serviceAccount:${RUNTIME_INVOKER}\" --role=\"roles/run.invoker\""
emit "gcloud storage buckets create \"gs://${EVIDENCE_BUCKET}\" --project=\"${PROJECT_ID}\" --location=\"${REGION}\" --uniform-bucket-level-access --public-access-prevention"
emit "gcloud storage buckets update \"gs://${EVIDENCE_BUCKET}\" --retention-period=\"P${RETENTION_DAYS}D\""
emit "gcloud storage buckets describe \"gs://${EVIDENCE_BUCKET}\" --format=\"default(retention_policy)\""
emit "gcloud monitoring dashboards list --project=\"${PROJECT_ID}\""
emit "gcloud run services update-traffic \"${SERVICE}\" --to-revisions=\"${REVISION}=0\" --region=\"${REGION}\" --project=\"${PROJECT_ID}\""

cat <<'FOOTER'
NOT_RENDERED_AS_EXECUTED=YES
NO_GCLOUD_COMMAND_WAS_INVOKED=YES
NO_GOOGLE_API_WAS_CALLED=YES
RETENTION_LOCK_COMMAND_INTENTIONALLY_ABSENT=YES
FOOTER
