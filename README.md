# Google Photos Migrator — Java branch

Java-first Google Photos migration application. This branch intentionally uses **no Selenium/WebDriver**.

## Architecture

- Frontend: static HTML/CSS/JS in `docs/` for GitHub Pages.
- Backend: Java 21 + Spring Boot.
- Source: Google Photos Picker API (`photospicker.mediaitems.readonly`).
- Destination: Google Photos Library append-only scope.
- Migration ledger/database: `.xlsx` using Apache POI.
- Persistent ledger: private Google Cloud Storage object in production.
- OAuth credentials: separate Source/Destination credentials; AES-256-GCM encrypted GCS vault in production.
- Runtime target: Google Cloud Run.
- CI: GitHub Actions.

## Safety rules

1. Source deletion is not implemented.
2. Source and destination Google account emails must be different.
3. OAuth secrets/tokens and Excel ledgers are never committed to GitHub.
4. Photos/videos are streamed between Google services and are not persisted in GitHub.
5. Destination permissions remain append-only.
6. Cloud Storage Excel writes use generation preconditions.
7. Videos are downloaded only when Picker metadata reports processing status `READY`.

## Development

```bash
mvn test
mvn spring-boot:run
```

Local defaults use an in-memory OAuth vault and temporary local Excel storage. Production should use GCS modes.

## Production environment variables

- `LEDGER_STORAGE_MODE=gcs`
- `LEDGER_BUCKET_NAME`
- `LEDGER_OBJECT_PREFIX` (default `migrations`)
- `FRONTEND_ORIGIN` (origin only, e.g. `https://paritoshsingh0207.github.io`)
- `FRONTEND_URL` (full GitHub Pages URL)
- `GOOGLE_OAUTH_CLIENT_ID`
- `GOOGLE_OAUTH_CLIENT_SECRET`
- `GOOGLE_OAUTH_REDIRECT_URI`
- `OAUTH_VAULT_MODE=gcs`
- `OAUTH_VAULT_BUCKET` (can be the ledger bucket)
- `OAUTH_VAULT_PREFIX` (default `oauth`)
- `OAUTH_VAULT_KEY_BASE64` (exactly 32 random bytes, Base64 encoded; inject from Secret Manager)
- `TRANSFER_MAX_ATTEMPTS` (default `3`)

## Implemented milestone

- [x] Java 21 / Spring Boot foundation
- [x] Excel migration ledger
- [x] Cloud Storage generation-safe ledger adapter
- [x] Separate Account A / Account B OAuth flow
- [x] Different-account validation by Google email
- [x] Refresh-token handling
- [x] AES-GCM encrypted production credential vault
- [x] Picker session creation, polling and pagination
- [x] Picker media stream download
- [x] Streaming destination upload (no whole-file Java byte array)
- [x] SHA-256 calculated while media streams to destination
- [x] `UPLOADING`, `VERIFIED`, retryable/final failure ledger states
- [x] Source-media-ID resume/skip protection
- [x] Browser migration dashboard
- [x] Downloadable Excel report
- [x] GitHub Actions Maven CI
- [ ] Google Cloud OAuth project configuration
- [ ] GitHub Pages deployment configuration
- [ ] Cloud Run deployment pipeline
- [ ] Live 1-photo / 10-photo migration test
