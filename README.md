# Google Photos Migrator — Java branch

This branch contains a Java-first Google Photos migration application. It intentionally uses **no Selenium/WebDriver**.

## Architecture

- Frontend: static HTML/CSS/JS in `docs/` for GitHub Pages.
- Backend: Java 21 + Spring Boot.
- Source access: Google Photos Picker API (`photospicker.mediaitems.readonly`).
- Destination access: Google Photos Library append-only upload scope.
- Migration ledger: `.xlsx` workbook using Apache POI.
- Persistent ledger storage: private Google Cloud Storage bucket in production.
- Runtime: Google Cloud Run.
- CI: GitHub Actions.

## Safety rules

1. Source deletion is not implemented.
2. OAuth secrets/tokens and Excel ledgers are ignored by Git.
3. Photos/videos are never committed to GitHub.
4. Destination permissions are append-only.
5. The Excel workbook uses generation-aware writes in Cloud Storage to prevent stale overwrites.

## Local development

```bash
mvn test
mvn spring-boot:run
```

If `LEDGER_BUCKET_NAME` is unset, the application uses a temporary local ledger only for development. Production must configure a private Cloud Storage bucket.

## Production environment variables

- `LEDGER_BUCKET_NAME`
- `LEDGER_OBJECT_PREFIX` (default `migrations`)
- `FRONTEND_ORIGIN`
- `GOOGLE_OAUTH_CLIENT_ID`
- `GOOGLE_OAUTH_CLIENT_SECRET`
- `GOOGLE_OAUTH_REDIRECT_URI`

## Current implementation milestone

- [x] Java 21 / Spring Boot skeleton
- [x] Excel ledger model
- [x] Cloud Storage generation-safe ledger adapter
- [x] Picker API REST client
- [x] Library upload REST client
- [x] SHA-256 utility
- [x] Static GitHub Pages shell
- [x] Unit-test and CI scaffolding
- [ ] Dual-account OAuth flow
- [ ] Picker-session UI
- [ ] Transfer coordinator and batching
- [ ] Retry/resume orchestration
- [ ] Downloadable Excel report endpoint
- [ ] Cloud Run deployment pipeline
