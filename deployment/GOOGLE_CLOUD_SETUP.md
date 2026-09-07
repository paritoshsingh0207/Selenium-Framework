# Zero-billing cleanup and policy

This project must not use any Google Cloud resource that requires an attached billing account.

## Current hosting policy

- GitHub Pages: allowed
- Google Cloud billing link: not allowed
- Cloud Run: not allowed
- Cloud Storage: not allowed for this project
- Secret Manager: not allowed for this project
- Artifact Registry / Cloud Build: not allowed for this project
- Paid server/backend services: not allowed

The previous Google Cloud deployment path is retired.

## Cleanup for the temporary migration project

The dedicated project created during setup was:

`gphotos-migrator-ps0207`

If the previous bootstrap was started after billing was linked, first stop it with `Ctrl+C` if it is still running.

Then run from Google Cloud Shell:

```bash
PROJECT_ID="gphotos-migrator-ps0207"

gcloud billing projects unlink "$PROJECT_ID"
gcloud billing projects describe "$PROJECT_ID"
gcloud projects delete "$PROJECT_ID" --quiet
```

Google Cloud project shutdown stops resource usage and billing for that project and puts it into Google's recovery/deletion period.

Return Cloud Shell to the prior project if desired:

```bash
gcloud config set project gen-lang-client-0038725722
```

The temporary Cloud Shell clone can also be removed without affecting GitHub:

```bash
cd ~
rm -rf Selenium-Framework
```

Do not close the user's overall Cloud Billing account; only unlink/delete the dedicated migration project.

## Repository status

The Cloud Run deployment workflow has been removed. `deployment/bootstrap-gcp.sh` is intentionally retained only as a guard that exits without creating resources.

The next implementation phase must use a zero-billing architecture before any live migration is attempted.
