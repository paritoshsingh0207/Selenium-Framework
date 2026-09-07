# Google Cloud deployment setup

GitHub Pages is already enabled and deployed. The remaining one-time Google Cloud work is mostly automated by `deployment/bootstrap-gcp.sh`.

## 1. Run the bootstrap in Google Cloud Shell

Open Google Cloud Shell in the Google Cloud project you want to use, clone this repository branch or `main` after this change is merged, and run:

```bash
bash deployment/bootstrap-gcp.sh YOUR_PROJECT_ID
```

The script is idempotent and creates/configures:

- required Google Cloud APIs
- dedicated GitHub deployer service account
- dedicated Cloud Run runtime service account
- private Cloud Storage bucket for Excel ledgers and encrypted OAuth credentials
- Secret Manager resources
- random OAuth-state, credential-vault and private dashboard keys
- GitHub Workload Identity Federation restricted to `paritoshsingh0207/Selenium-Framework`
- required deployment/runtime IAM grants
- a public placeholder Cloud Run service so later CI deployments preserve public invocation without changing IAM repeatedly
- GitHub repository variables automatically when an authenticated `gh` CLI is available

It prints the exact Cloud Run service URL and OAuth callback URI at the end.

## 2. Create the Google OAuth Web client

In Google Cloud Console, create an OAuth 2.0 **Web application** client and add the callback printed by the bootstrap script exactly:

`https://<cloud-run-service-url>/api/oauth/callback`

The app requests only these account-specific scopes:

- Account A: `https://www.googleapis.com/auth/photospicker.mediaitems.readonly`
- Account B write: `https://www.googleapis.com/auth/photoslibrary.appendonly`
- Account B verification: `https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata`
- `openid email` to ensure Account A and Account B are different Google accounts

The Account B verification scope is limited to media created by this migrator. The app requests no delete scope.

Add the OAuth client values to Secret Manager, not GitHub:

```bash
printf '%s' 'YOUR_CLIENT_ID' | gcloud secrets versions add google-oauth-client-id --project=YOUR_PROJECT_ID --data-file=-
printf '%s' 'YOUR_CLIENT_SECRET' | gcloud secrets versions add google-oauth-client-secret --project=YOUR_PROJECT_ID --data-file=-
```

## 3. GitHub repository variables

If `gh` was authenticated in Cloud Shell, the bootstrap script sets the variables automatically. Otherwise it prints ready-to-run `gh variable set` commands for:

- `GCP_PROJECT_ID`
- `GCP_REGION=asia-south1`
- `CLOUD_RUN_SERVICE=google-photos-migrator`
- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_SERVICE_ACCOUNT`
- `CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT`
- `LEDGER_BUCKET_NAME`
- `FRONTEND_ORIGIN=https://paritoshsingh0207.github.io`
- `FRONTEND_URL=https://paritoshsingh0207.github.io/Selenium-Framework/`
- `GOOGLE_OAUTH_REDIRECT_URI`
- `SECRET_GOOGLE_OAUTH_CLIENT_ID=google-oauth-client-id`
- `SECRET_GOOGLE_OAUTH_CLIENT_SECRET=google-oauth-client-secret`
- `SECRET_OAUTH_STATE_SECRET=oauth-state-secret`
- `SECRET_OAUTH_VAULT_KEY=oauth-vault-key`
- `SECRET_APP_ACCESS_KEY=app-access-key`

Only Secret Manager resource names go into GitHub variables; the secret values remain in Google Cloud.

## 4. Deploy the real application

After the OAuth client ID/secret exist and the repository variables are set, run **Google Photos Migrator Cloud Run** from GitHub Actions.

The production workflow:

- authenticates with keyless Workload Identity Federation
- deploys from source
- runs under the dedicated runtime service account
- uses `concurrency=1` and `max-instances=1` to protect the single Excel ledger model
- preserves the public invocation IAM already established by the bootstrap placeholder service
- overwrites runtime environment/secrets authoritatively so stale configuration is not retained

## 5. First controlled migration

1. Open `https://paritoshsingh0207.github.io/Selenium-Framework/`.
2. Enter the deployed Cloud Run URL.
3. Retrieve the private dashboard key locally when needed:

```bash
gcloud secrets versions access latest --secret=app-access-key --project=YOUR_PROJECT_ID
```

Do not paste that key into chat or GitHub.

4. Connect Account A.
5. Connect a different Account B.
6. Select and migrate **one photo** first.
7. Verify it exists in Account B and download/check the Excel ledger.
8. Test a mixed 10-item photo/video batch.
9. Only after those checks pass, increase selection and batch sizes.

Source deletion remains outside the application by design.
