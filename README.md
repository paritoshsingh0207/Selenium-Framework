# Google Photos Migrator — Java

Java-first Google Photos migration application. This implementation intentionally uses **no Selenium/WebDriver** and does **not** implement source deletion.

## Zero-billing constraint

This project is now explicitly constrained to **₹0 / no Google Cloud billing attachment**.

Allowed:
- GitHub repository and GitHub Actions CI
- GitHub Pages frontend
- Java code and Excel ledger logic
- Google Photos APIs only in a design that does not require a billed backend

Not allowed for this project:
- Google Cloud billing link
- Cloud Run
- Cloud Storage
- Secret Manager
- Artifact Registry / Cloud Build deployment
- any paid hosted backend

The earlier Cloud Run deployment path has been retired. `deployment/bootstrap-gcp.sh` now exits without creating resources, and the Cloud Run GitHub Actions workflow has been removed.

## Existing application capabilities

- Java 21 + Spring Boot backend code
- Google Photos Picker API source flow
- Google Photos Library destination upload flow
- separate source/destination OAuth identities
- different-account validation
- resumable media upload support
- source/destination video processing states
- SHA-256 during transfer
- Excel retry/resume ledger
- browser dashboard under `docs/`
- GitHub Pages deployment
- Maven CI and regression tests

These backend capabilities remain in source for reuse, but they are **not currently deployed** because the previous production target required billing.

## Safety rules

1. Source deletion is not implemented.
2. Source and destination Google account emails must be different.
3. OAuth secrets/tokens and Excel ledgers must never be committed to GitHub.
4. Photos/videos must never be committed to GitHub.
5. No billing account may be attached for this project.
6. No live migration should start until the zero-billing runtime design is validated.

## Current status

- [x] Core Java migration logic
- [x] Excel migration ledger
- [x] Dual-account OAuth implementation
- [x] Picker session flow
- [x] Streaming/resumable destination upload logic
- [x] Video processing verification
- [x] Browser dashboard
- [x] GitHub Pages deployed
- [x] GitHub Actions Maven CI
- [x] Billed Cloud Run deployment path retired
- [ ] Zero-billing runtime architecture redesign
- [ ] Live 1-photo migration test on zero-billing architecture
- [ ] Live mixed photo/video migration test

See `deployment/GOOGLE_CLOUD_SETUP.md` for cleanup instructions if the old billed Google Cloud bootstrap was started.
