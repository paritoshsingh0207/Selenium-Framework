# Google Photos Migrator — Java

Java-first Google Photos migration application. This implementation intentionally uses **no Selenium/WebDriver** and does **not** implement source deletion.

## Architecture

- Frontend: static HTML/CSS/JS in `docs/` for GitHub Pages.
- Backend: Java 21 + Spring Boot.
- Source: Google Photos Picker API (`photospicker.mediaitems.readonly`).
- Destination: Google Photos Library `appendonly` plus `readonly.appcreateddata` solely to verify media created by this migrator.
- Migration ledger/database: `.xlsx` using Apache POI.
- Persistent ledger: private Google Cloud Storage object in production.
- OAuth credentials: separate Source/Destination credentials; AES-256-GCM encrypted GCS vault in production.
- Runtime target: Google Cloud Run.
- CI/CD: GitHub Actions with Workload Identity Federation for Google Cloud.

## Safety rules

1. Source deletion is not implemented.
2. Source and destination Google account emails must be different.
3. OAuth secrets/tokens and Excel ledgers are never committed to GitHub.
4. Photos/videos are streamed between Google services and are not persisted in GitHub.
5. Destination OAuth has no delete permission and cannot read the user's general library; its read scope is limited to app-created media for post-upload verification.
6. Cloud Storage Excel writes use generation preconditions.
7. Source videos are downloaded only when Picker metadata reports processing status `READY`.
8. Destination videos remain in `WAITING_DESTINATION_READY` until Google reports them `READY`; waiting does not consume another upload attempt.
9. Videos and large media use Google Photos resumable uploads; smaller media may use raw upload.
10. Production API access is protected by `APP_ACCESS_KEY` / `X-Migrator-Key`.
11. OAuth state is stateless, HMAC-signed and time-limited so Cloud Run scale-to-zero does not break callbacks.
12. Production Cloud Run is constrained to one instance / one concurrent request to protect the single Excel ledger model.

## Development

```bash
mvn test
mvn spring-boot:run
```

Local defaults use an in-memory OAuth vault and temporary local Excel storage. Production uses GCS modes and must configure the security secrets below.

## Production environment variables / secrets

- `LEDGER_STORAGE_MODE=gcs`
- `LEDGER_BUCKET_NAME`
- `LEDGER_OBJECT_PREFIX` (default `migrations`)
- `FRONTEND_ORIGIN` (origin only, e.g. `https://paritoshsingh0207.github.io`)
- `FRONTEND_URL` (full GitHub Pages URL)
- `GOOGLE_OAUTH_CLIENT_ID`
- `GOOGLE_OAUTH_CLIENT_SECRET`
- `GOOGLE_OAUTH_REDIRECT_URI`
- `OAUTH_STATE_SECRET` (at least 32 characters)
- `OAUTH_VAULT_MODE=gcs`
- `OAUTH_VAULT_BUCKET`
- `OAUTH_VAULT_PREFIX` (default `oauth`)
- `OAUTH_VAULT_KEY_BASE64` (exactly 32 random bytes, Base64 encoded)
- `APP_ACCESS_KEY` (long random value)
- `TRANSFER_MAX_ATTEMPTS` (default `3`)

See `deployment/GOOGLE_CLOUD_SETUP.md` for the no-local-clone deployment checklist.

## Implemented milestone

- [x] Java 21 / Spring Boot foundation
- [x] Excel migration ledger
- [x] Cloud Storage generation-safe ledger adapter
- [x] Separate Account A / Account B OAuth flow
- [x] Different-account validation by Google email
- [x] Refresh-token handling
- [x] AES-GCM encrypted production credential vault
- [x] Stateless HMAC OAuth state for Cloud Run restarts
- [x] Private access-key gate for sensitive API endpoints
- [x] Picker session creation, polling and pagination
- [x] Picker media stream download
- [x] Raw + resumable destination upload paths
- [x] SHA-256 calculated while media streams to destination
- [x] Source/destination video processing wait states
- [x] Destination app-created media verification
- [x] Excel retry/resume states
- [x] Browser restart-safe automatic batching
- [x] Downloadable Excel report
- [x] Regression tests for video waiting / verification behavior
- [x] GitHub Pages deployment workflow
- [x] Manual Cloud Run deployment workflow using Workload Identity Federation
- [x] GitHub Actions Maven CI
- [x] Feature merged to `main`
- [ ] One-time GitHub Pages repository enablement
- [ ] One-time Google Cloud/OAuth infrastructure configuration
- [ ] Live 1-photo migration test
- [ ] Live 10-photo/video migration test
