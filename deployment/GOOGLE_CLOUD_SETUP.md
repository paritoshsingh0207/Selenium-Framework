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

Create an OAuth Web application client. Configure the Cloud Run callback exactly as:

`https://<cloud-run-service-url>/api/oauth/callback`

The app requests only the account-specific scopes it needs:

- Account A: `photospicker.mediaitems.readonly`
- Account B: `photoslibrary.appendonly`
- `openid email` to verify Account A and Account B are different Google accounts

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

## 6. Workload Identity Federation

Configure GitHub Actions -> Google Cloud authentication using Workload Identity Federation. Scope the provider to this repository and give the deployment service account only the roles required to deploy Cloud Run and access the referenced secrets.

Do not create or commit a long-lived service-account JSON key.

## 7. Cloud Run public invocation

The browser-hosted GitHub Pages UI must be able to reach the Cloud Run HTTPS endpoint. The Cloud Run service therefore needs public invocation, while the application itself protects every sensitive `/api/**` endpoint using the `X-Migrator-Key` header. `/api/health` and the signed OAuth callback are the only intentionally public API paths.

Set Cloud Run IAM for unauthenticated invocation once during infrastructure setup. The deployment workflow intentionally does not change service IAM on every deployment.

## 8. GitHub Pages

In repository **Settings -> Pages -> Build and deployment**, select **GitHub Actions** as the source. The `Google Photos Migrator Pages` workflow publishes only `docs/`.

## 9. First deployment sequence

1. Configure Google Cloud resources and secrets.
2. Enable GitHub Pages with GitHub Actions.
3. Run `Google Photos Migrator Cloud Run` manually from GitHub Actions.
4. Put the returned Cloud Run URL in the dashboard.
5. Update `GOOGLE_OAUTH_REDIRECT_URI` to the exact Cloud Run callback URL if necessary and redeploy.
6. Run the Pages workflow.
7. Test Account A and Account B OAuth.
8. Test one photo only.
9. Check Account B and the downloaded Excel ledger.
10. Only then increase the transfer batch size.

Source deletion remains outside the application by design.
