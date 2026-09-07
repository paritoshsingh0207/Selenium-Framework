# Google Photos Migrator — Zero Billing

A private Google Photos account-to-account migration orchestrator with a hard **₹0 infrastructure / no billing attachment** requirement.

## Production architecture

The production path no longer transfers media through our own backend. Google Photos performs the copy internally:

- **Whole library:** Google Photos Partner Sharing → Account B → Save to your account.
- **Selected media:** Google Photos Shared Album → Account B → Save all photos and videos.
- **Orchestration:** static GitHub Pages dashboard in `docs/`.
- **Ledger:** browser-local state with dependency-free `.xlsx` and JSON export.

No production OAuth token, photo, video, ledger or secret is sent to this repository or to an application backend.

## Hard zero-billing constraint

Allowed:

- GitHub repository
- GitHub Actions CI
- GitHub Pages
- browser-local storage for migration progress
- Google Photos' own Partner Sharing and Shared Album features

Not allowed:

- Google Cloud billing link
- Cloud Run
- Cloud Storage
- Secret Manager
- Artifact Registry / Cloud Build deployment
- Firebase billing
- paid workers or paid hosted backends

The earlier Cloud Run path has been retired. `deployment/bootstrap-gcp.sh` is a no-side-effect guard and the Cloud Run workflow is removed.

## Dashboard responsibilities

The GitHub Pages application:

- records Account A and Account B identifiers locally;
- prevents same-account verification;
- guides whole-library Partner Sharing;
- creates selected-media migration batch IDs;
- records source/destination counts and lifecycle state;
- blocks VERIFIED for a selected batch unless non-zero source/destination counts match;
- maintains an audit history;
- exports an Excel workbook with `Summary`, `Items`, `Failures`, `Audit`, and `Config` sheets;
- keeps source deletion manual and outside the application.

The application never proxies or stores media bytes.

## Safety rules

1. Source deletion is not implemented.
2. Account A and Account B must be different.
3. Verify the destination before deleting anything from Account A.
4. Photos/videos must never be committed to GitHub.
5. No billing account may be attached for this project.
6. Direct account sharing is preferred over public album links for selected migrations.
7. Partner Sharing primarily migrates media; arbitrary album organization is not automatically recreated.

## Legacy Java code

The Java 21 / Spring Boot API implementation remains in `src/` as an archived engineering prototype with regression tests. It includes Picker API, upload, video-processing and Excel-ledger logic, but it is **not part of the zero-billing production runtime**.

## Current status

- [x] Java migration prototype and regression tests
- [x] Billed Cloud Run deployment path retired
- [x] GitHub Pages enabled
- [x] Zero-billing native Google Photos architecture defined
- [x] Browser-only Partner Sharing workflow
- [x] Browser-only Shared Album batch workflow
- [x] Browser-local audit ledger
- [x] Dependency-free `.xlsx` export
- [ ] Live whole-library workflow acceptance test
- [ ] Live selected small-batch acceptance test

See `docs/zero-billing-design.md` for the architecture and `deployment/GOOGLE_CLOUD_SETUP.md` for cleanup information from the retired cloud design.
