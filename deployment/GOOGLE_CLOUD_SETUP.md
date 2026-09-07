# Google Cloud deployment setup

This is the one-time infrastructure checklist for the Google Photos Migrator. No laptop clone is required; the commands can be run in Google Cloud Shell.

## 1. Required Google Cloud services

Enable at least:

- Cloud Run
- Cloud Build
- Artifact Registry
- Secret Manager
- Cloud Storage
- IAM Credentials / Security Token Service for GitHub Workload Identity Federation
- Google Photos Picker API
- Google Photos Library API

Use `asia-south1` (Mumbai) unless you intentionally choose another region.

## 2. Private Cloud Storage bucket

Create one private bucket for Excel migration ledgers and encrypted OAuth credential blobs. Do not make this bucket public.

The application writes:

- `migrations/<migration-id>.xlsx`
- `oauth/source.credential.enc`
- `oauth/destination.credential.enc`

Photos and videos are not intentionally stored in this bucket.

## 3. Google OAuth web client

Create an OAuth Web application client. Configure the final Cloud Run callback exactly as:

`https://<cloud-run-service-url>/api/oauth/callback`

The app requests only the account-specific scopes it needs:

- Account A: `https://www.googleapis.com/auth/photospicker.mediaitems.readonly`
- Account B write: `https://www.googleapis.com/auth/photoslibrary.appendonly`
- Account B verification read: `https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata`
- `openid email` to verify Account A and Account B are different Google accounts

The Account B read scope can read media created by this application; it does not grant general-library read access. No delete scope is requested.

## 4. Secret Manager

Create five secrets and place only their secret resource names in GitHub repository variables:

| GitHub variable | Secret value |
|---|---|
| `SECRET_GOOGLE_OAUTH_CLIENT_ID` | Google OAuth client ID |
| `SECRET_GOOGLE_OAUTH_CLIENT_SECRET` | Google OAuth client secret |
| `SECRET_OAUTH_STATE_SECRET` | random string of at least 32 characters |
| `SECRET_OAUTH_VAULT_KEY` | Base64 encoding of exactly 32 random bytes |
| `SECRET_APP_ACCESS_KEY` | long random private dashboard/API key |

The values themselves must stay in Secret Manager, not GitHub source code.

## 5. GitHub repository variables

Configure these non-secret repository/environment variables:

- `GCP_PROJECT_ID`
- `GCP_REGION=asia-south1`
- `CLOUD_RUN_SERVICE=google-photos-migrator`
- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_SERVICE_ACCOUNT`
- `LEDGER_BUCKET_NAME`
- `FRONTEND_ORIGIN=https://paritoshsingh0207.github.io`
- `FRONTEND_URL=https://paritoshsingh0207.github.io/Selenium-Framework/`
- `GOOGLE_OAUTH_REDIRECT_URI=https://<cloud-run-service-url>/api/oauth/callback`
- the five `SECRET_*` variables listed above, containing Secret Manager resource names (not secret values)

For the very first Cloud Run deployment, a temporary placeholder value may be used for `GOOGLE_OAUTH_REDIRECT_URI`. After Cloud Run returns the service URL, configure that exact callback in the Google OAuth client, update the GitHub variable, and redeploy before attempting OAuth.

## 6. Workload Identity Federation

Configure GitHub Actions -> Google Cloud authentication using Workload Identity Federation. Scope the provider to this repository and give the deployment service account only the roles required to build/deploy Cloud Run and access the referenced secrets and private ledger bucket.

Do not create or commit a long-lived service-account JSON key.

## 7. Cloud Run invocation and ledger safety

The browser-hosted GitHub Pages UI must be able to reach the Cloud Run HTTPS endpoint. The deployment workflow uses public Cloud Run invocation, while the application itself protects every sensitive `/api/**` endpoint using the `X-Migrator-Key` header. `/api/health` and the signed OAuth callback are the intentionally public application paths.

The deployment workflow also sets:

- `--concurrency=1`
- `--max-instances=1`
- request timeout `60m`

This keeps the single Excel-ledger design deterministic while browser-side automatic batching breaks large migrations into short restart-safe requests.

## 8. GitHub Pages

In repository **Settings -> Pages -> Build and deployment**, select **GitHub Actions** as the source. This is a one-time repository setting; the connected GitHub automation API used by this project cannot enable it. The `Google Photos Migrator Pages` workflow publishes only `docs/` from `main`.

## 9. First deployment sequence

1. Configure Google Cloud resources, bucket, service account, Workload Identity Federation, and secrets.
2. Enable GitHub Pages with GitHub Actions.
3. Set the GitHub variables; use a temporary OAuth redirect placeholder if the Cloud Run URL is not known yet.
4. Run `Google Photos Migrator Cloud Run` manually from GitHub Actions.
5. Copy the returned Cloud Run URL.
6. Add `https://<cloud-run-service-url>/api/oauth/callback` to the Google OAuth Web client.
7. Update `GOOGLE_OAUTH_REDIRECT_URI` to that exact URL and redeploy Cloud Run.
8. Run/rerun the Pages workflow.
9. Put the Cloud Run URL and the private app access key into the dashboard.
10. Connect Account A, then a different Account B.
11. Test one photo only and verify Account B plus the Excel ledger.
12. Test a mixed 10-item photo/video selection and exercise Resume if a video is still processing.
13. Only then increase selection/batch sizes.

Source deletion remains outside the application by design.
