#!/usr/bin/env bash
set -Eeuo pipefail

# Google Photos Migrator one-time Google Cloud bootstrap.
# Run in Google Cloud Shell as a project Owner (or equivalent IAM admin).
# Usage:
#   bash deployment/bootstrap-gcp.sh YOUR_PROJECT_ID
# Optional environment values:
#   GOOGLE_OAUTH_CLIENT_ID='...'
#   GOOGLE_OAUTH_CLIENT_SECRET='...'
#   LEDGER_BUCKET_NAME='custom-globally-unique-bucket'

GITHUB_REPO="paritoshsingh0207/Selenium-Framework"
GITHUB_OWNER="paritoshsingh0207"
REGION="${GCP_REGION:-asia-south1}"
CLOUD_RUN_SERVICE="${CLOUD_RUN_SERVICE:-google-photos-migrator}"
POOL_ID="${WIF_POOL_ID:-github}"
PROVIDER_ID="${WIF_PROVIDER_ID:-selenium-framework}"
DEPLOYER_SA_ID="${DEPLOYER_SA_ID:-photos-migrator-deployer}"
RUNTIME_SA_ID="${RUNTIME_SA_ID:-photos-migrator-runtime}"

PROJECT_ID="${1:-${PROJECT_ID:-}}"
if [[ -z "${PROJECT_ID}" ]]; then
  PROJECT_ID="$(gcloud config get-value project 2>/dev/null || true)"
fi
if [[ -z "${PROJECT_ID}" || "${PROJECT_ID}" == "(unset)" ]]; then
  echo "Usage: bash deployment/bootstrap-gcp.sh YOUR_PROJECT_ID" >&2
  exit 2
fi

command -v gcloud >/dev/null 2>&1 || { echo "gcloud is required. Run this from Google Cloud Shell." >&2; exit 2; }
command -v openssl >/dev/null 2>&1 || { echo "openssl is required." >&2; exit 2; }

echo "==> Using Google Cloud project: ${PROJECT_ID}"
gcloud config set project "${PROJECT_ID}" >/dev/null
PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"
if [[ -z "${PROJECT_NUMBER}" ]]; then
  echo "Could not resolve project number for ${PROJECT_ID}." >&2
  exit 3
fi

DEPLOYER_SA="${DEPLOYER_SA_ID}@${PROJECT_ID}.iam.gserviceaccount.com"
RUNTIME_SA="${RUNTIME_SA_ID}@${PROJECT_ID}.iam.gserviceaccount.com"
COMPUTE_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
BUCKET_NAME="${LEDGER_BUCKET_NAME:-${PROJECT_ID}-${PROJECT_NUMBER}-photos-migrator}"

FRONTEND_ORIGIN="https://paritoshsingh0207.github.io"
FRONTEND_URL="https://paritoshsingh0207.github.io/Selenium-Framework/"

SECRET_GOOGLE_OAUTH_CLIENT_ID="google-oauth-client-id"
SECRET_GOOGLE_OAUTH_CLIENT_SECRET="google-oauth-client-secret"
SECRET_OAUTH_STATE_SECRET="oauth-state-secret"
SECRET_OAUTH_VAULT_KEY="oauth-vault-key"
SECRET_APP_ACCESS_KEY="app-access-key"

retry() {
  local attempts="$1"; shift
  local delay="$1"; shift
  local n=1
  until "$@"; do
    if (( n >= attempts )); then return 1; fi
    sleep "${delay}"
    n=$((n + 1))
  done
}

ensure_service_account() {
  local id="$1" display="$2"
  if ! gcloud iam service-accounts describe "${id}@${PROJECT_ID}.iam.gserviceaccount.com" --project="${PROJECT_ID}" >/dev/null 2>&1; then
    gcloud iam service-accounts create "${id}" --project="${PROJECT_ID}" --display-name="${display}"
  fi
}

grant_project_role() {
  local member="$1" role="$2"
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="${member}" \
    --role="${role}" \
    --condition=None \
    --quiet >/dev/null
}

ensure_secret() {
  local secret="$1"
  if ! gcloud secrets describe "${secret}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
    gcloud secrets create "${secret}" --project="${PROJECT_ID}" --replication-policy="automatic"
  fi
}

secret_has_enabled_version() {
  local secret="$1"
  [[ -n "$(gcloud secrets versions list "${secret}" --project="${PROJECT_ID}" --filter='state=ENABLED' --format='value(name)' --limit=1 2>/dev/null)" ]]
}

add_secret_if_empty() {
  local secret="$1" value="$2"
  if ! secret_has_enabled_version "${secret}"; then
    printf '%s' "${value}" | gcloud secrets versions add "${secret}" --project="${PROJECT_ID}" --data-file=- >/dev/null
  fi
}

set_repo_var_if_possible() {
  local name="$1" value="$2"
  if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    gh variable set "${name}" --repo "${GITHUB_REPO}" --body "${value}"
    return 0
  fi
  return 1
}

echo "==> Enabling required APIs"
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  storage.googleapis.com \
  compute.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  photospicker.googleapis.com \
  photoslibrary.googleapis.com \
  --project="${PROJECT_ID}"

echo "==> Creating dedicated deployer and runtime identities"
ensure_service_account "${DEPLOYER_SA_ID}" "Google Photos Migrator GitHub deployer"
ensure_service_account "${RUNTIME_SA_ID}" "Google Photos Migrator Cloud Run runtime"

echo "==> Granting least-privilege deployment roles"
for role in roles/run.admin roles/run.sourceDeveloper roles/serviceusage.serviceUsageConsumer; do
  grant_project_role "serviceAccount:${DEPLOYER_SA}" "${role}"
done

gcloud iam service-accounts add-iam-policy-binding "${RUNTIME_SA}" \
  --project="${PROJECT_ID}" \
  --member="serviceAccount:${DEPLOYER_SA}" \
  --role="roles/iam.serviceAccountUser" \
  --condition=None \
  --quiet >/dev/null

# Current Cloud Run source deployments use the Compute Engine default service account
# for builds unless a custom build service account is configured. Google documents
# roles/run.builder for that identity. API enablement can take a short time to create it.
echo "==> Waiting for Cloud Build service identity"
if retry 30 2 gcloud iam service-accounts describe "${COMPUTE_SA}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  grant_project_role "serviceAccount:${COMPUTE_SA}" "roles/run.builder"
  gcloud iam service-accounts add-iam-policy-binding "${COMPUTE_SA}" \
    --project="${PROJECT_ID}" \
    --member="serviceAccount:${DEPLOYER_SA}" \
    --role="roles/iam.serviceAccountUser" \
    --condition=None \
    --quiet >/dev/null
else
  echo "WARNING: ${COMPUTE_SA} was not visible yet. If source deployment later reports a build-permission error, rerun this script." >&2
fi

echo "==> Creating private ledger / credential bucket: gs://${BUCKET_NAME}"
if ! gcloud storage buckets describe "gs://${BUCKET_NAME}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud storage buckets create "gs://${BUCKET_NAME}" \
    --project="${PROJECT_ID}" \
    --location="${REGION}" \
    --uniform-bucket-level-access
fi

gcloud storage buckets add-iam-policy-binding "gs://${BUCKET_NAME}" \
  --member="serviceAccount:${RUNTIME_SA}" \
  --role="roles/storage.objectAdmin" >/dev/null

echo "==> Creating Secret Manager resources"
for secret in \
  "${SECRET_GOOGLE_OAUTH_CLIENT_ID}" \
  "${SECRET_GOOGLE_OAUTH_CLIENT_SECRET}" \
  "${SECRET_OAUTH_STATE_SECRET}" \
  "${SECRET_OAUTH_VAULT_KEY}" \
  "${SECRET_APP_ACCESS_KEY}"; do
  ensure_secret "${secret}"
  for sa in "${RUNTIME_SA}" "${DEPLOYER_SA}"; do
    gcloud secrets add-iam-policy-binding "${secret}" \
      --project="${PROJECT_ID}" \
      --member="serviceAccount:${sa}" \
      --role="roles/secretmanager.secretAccessor" \
      --condition=None \
      --quiet >/dev/null
  done
done

# Generate application-only secrets once. Values are never printed.
add_secret_if_empty "${SECRET_OAUTH_STATE_SECRET}" "$(openssl rand -base64 48 | tr -d '\n')"
add_secret_if_empty "${SECRET_OAUTH_VAULT_KEY}" "$(openssl rand -base64 32 | tr -d '\n')"
add_secret_if_empty "${SECRET_APP_ACCESS_KEY}" "$(openssl rand -base64 48 | tr -d '\n')"

# OAuth credentials can optionally be injected when known.
if [[ -n "${GOOGLE_OAUTH_CLIENT_ID:-}" ]]; then
  add_secret_if_empty "${SECRET_GOOGLE_OAUTH_CLIENT_ID}" "${GOOGLE_OAUTH_CLIENT_ID}"
fi
if [[ -n "${GOOGLE_OAUTH_CLIENT_SECRET:-}" ]]; then
  add_secret_if_empty "${SECRET_GOOGLE_OAUTH_CLIENT_SECRET}" "${GOOGLE_OAUTH_CLIENT_SECRET}"
fi

echo "==> Configuring GitHub Workload Identity Federation"
if ! gcloud iam workload-identity-pools describe "${POOL_ID}" --project="${PROJECT_ID}" --location="global" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create "${POOL_ID}" \
    --project="${PROJECT_ID}" \
    --location="global" \
    --display-name="GitHub Actions"
fi

if ! gcloud iam workload-identity-pools providers describe "${PROVIDER_ID}" \
  --project="${PROJECT_ID}" --location="global" --workload-identity-pool="${POOL_ID}" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers create-oidc "${PROVIDER_ID}" \
    --project="${PROJECT_ID}" \
    --location="global" \
    --workload-identity-pool="${POOL_ID}" \
    --display-name="Selenium-Framework GitHub Actions" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
    --attribute-condition="assertion.repository == '${GITHUB_REPO}'"
fi

POOL_NAME="$(gcloud iam workload-identity-pools describe "${POOL_ID}" --project="${PROJECT_ID}" --location="global" --format='value(name)')"
PROVIDER_NAME="$(gcloud iam workload-identity-pools providers describe "${PROVIDER_ID}" --project="${PROJECT_ID}" --location="global" --workload-identity-pool="${POOL_ID}" --format='value(name)')"

gcloud iam service-accounts add-iam-policy-binding "${DEPLOYER_SA}" \
  --project="${PROJECT_ID}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/${POOL_NAME}/attribute.repository/${GITHUB_REPO}" \
  --condition=None \
  --quiet >/dev/null

echo "==> Ensuring the Cloud Run service exists and is publicly invokable"
if ! gcloud run services describe "${CLOUD_RUN_SERVICE}" --project="${PROJECT_ID}" --region="${REGION}" >/dev/null 2>&1; then
  gcloud run deploy "${CLOUD_RUN_SERVICE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --image="us-docker.pkg.dev/cloudrun/container/hello:latest" \
    --service-account="${RUNTIME_SA}" \
    --allow-unauthenticated \
    --concurrency=1 \
    --max-instances=1 \
    --quiet
fi

SERVICE_URL="$(gcloud run services describe "${CLOUD_RUN_SERVICE}" --project="${PROJECT_ID}" --region="${REGION}" --format='value(status.url)')"
OAUTH_REDIRECT_URI="${SERVICE_URL}/api/oauth/callback"

echo "==> Preparing GitHub repository variables"
declare -A REPO_VARS=(
  [GCP_PROJECT_ID]="${PROJECT_ID}"
  [GCP_REGION]="${REGION}"
  [CLOUD_RUN_SERVICE]="${CLOUD_RUN_SERVICE}"
  [GCP_WORKLOAD_IDENTITY_PROVIDER]="${PROVIDER_NAME}"
  [GCP_SERVICE_ACCOUNT]="${DEPLOYER_SA}"
  [CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT]="${RUNTIME_SA}"
  [LEDGER_BUCKET_NAME]="${BUCKET_NAME}"
  [FRONTEND_ORIGIN]="${FRONTEND_ORIGIN}"
  [FRONTEND_URL]="${FRONTEND_URL}"
  [GOOGLE_OAUTH_REDIRECT_URI]="${OAUTH_REDIRECT_URI}"
  [SECRET_GOOGLE_OAUTH_CLIENT_ID]="${SECRET_GOOGLE_OAUTH_CLIENT_ID}"
  [SECRET_GOOGLE_OAUTH_CLIENT_SECRET]="${SECRET_GOOGLE_OAUTH_CLIENT_SECRET}"
  [SECRET_OAUTH_STATE_SECRET]="${SECRET_OAUTH_STATE_SECRET}"
  [SECRET_OAUTH_VAULT_KEY]="${SECRET_OAUTH_VAULT_KEY}"
  [SECRET_APP_ACCESS_KEY]="${SECRET_APP_ACCESS_KEY}"
)

GH_CONFIGURED=true
for key in "${!REPO_VARS[@]}"; do
  if ! set_repo_var_if_possible "${key}" "${REPO_VARS[$key]}"; then
    GH_CONFIGURED=false
    break
  fi
done

cat <<EOF

Google Cloud bootstrap complete.

Cloud Run URL:
  ${SERVICE_URL}

Google OAuth Web callback URI:
  ${OAUTH_REDIRECT_URI}

GitHub Pages dashboard:
  ${FRONTEND_URL}
EOF

if [[ "${GH_CONFIGURED}" == true ]]; then
  echo "GitHub repository variables were configured through the authenticated gh CLI."
else
  cat <<EOF

The gh CLI is not authenticated, so repository variables were not changed automatically.
After authenticating gh (gh auth login), run these commands:
EOF
  for key in $(printf '%s\n' "${!REPO_VARS[@]}" | sort); do
    printf "gh variable set %q --repo %q --body %q\n" "${key}" "${GITHUB_REPO}" "${REPO_VARS[$key]}"
  done
fi

if ! secret_has_enabled_version "${SECRET_GOOGLE_OAUTH_CLIENT_ID}" || ! secret_has_enabled_version "${SECRET_GOOGLE_OAUTH_CLIENT_SECRET}"; then
  cat <<EOF

OAuth client credentials are still required.
1. In Google Cloud Console, create an OAuth 2.0 Web application client.
2. Add this Authorized redirect URI exactly:
   ${OAUTH_REDIRECT_URI}
3. Then add the two values to Secret Manager without putting them in GitHub:

   printf '%s' 'YOUR_CLIENT_ID' | gcloud secrets versions add ${SECRET_GOOGLE_OAUTH_CLIENT_ID} --project=${PROJECT_ID} --data-file=-
   printf '%s' 'YOUR_CLIENT_SECRET' | gcloud secrets versions add ${SECRET_GOOGLE_OAUTH_CLIENT_SECRET} --project=${PROJECT_ID} --data-file=-
EOF
fi

cat <<EOF

To retrieve the private dashboard access key later (do not post it in chat):
  gcloud secrets versions access latest --secret=${SECRET_APP_ACCESS_KEY} --project=${PROJECT_ID}

After the OAuth client ID/secret exist and GitHub variables are configured, run the
'Google Photos Migrator Cloud Run' workflow from GitHub Actions.
EOF
