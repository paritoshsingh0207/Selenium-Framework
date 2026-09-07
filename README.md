# Google Photos Storage Migrator — Zero Billing

A Java utility for evacuating a nearly-full Google Photos account into one or more secondary Google Photos accounts without any paid backend or Google Cloud billing attachment.

## Target architecture

```text
Google Photos Account A
        |
        | Google Takeout ZIP archives
        v
Windows Photos Migrator utility
  - streams ZIP entries without full extraction
  - SHA-256 inventory and duplicate detection
  - reads common Google Photos JSON sidecar timestamps
  - Excel checkpoint / audit ledger
  - destination allocation by date and capacity
  - OAuth sign-in for Account B / C / D
  - Google Photos upload + retry + verification
        |
        +--> Account B
        +--> Account C
        +--> Account D
```

The computer is the transfer engine. No photo/video bytes are sent to GitHub or to our own hosted server.

## Hard zero-billing constraint

Allowed:

- local Windows utility
- Google Takeout
- Google Photos API / OAuth where available without a billing attachment
- GitHub repository and GitHub Actions build/test artifacts
- local Excel ledger

Not allowed:

- Google Cloud billing link
- Cloud Run
- Cloud Storage
- Secret Manager
- Firebase billing
- paid database or paid worker
- automatic destructive deletion of the primary library

The earlier Cloud Run path remains retired. `deployment/bootstrap-gcp.sh` is a no-side-effect guard.

## Phase 1 — implemented

The local utility now has an `inventory` command that:

- discovers Takeout `.zip` files recursively;
- reads media directly from ZIP streams, so archives do not need to be fully extracted;
- calculates SHA-256 for supported photo/video entries;
- detects byte-identical duplicates across archives using SHA-256 + size;
- reads `photoTakenTime.timestamp` / `creationTime.timestamp` from common Takeout JSON sidecars;
- writes a local `.xlsx` ledger with `Summary`, `Items`, `Duplicates`, `Failures`, `Destinations`, `Cleanup Plan`, and `Audit` sheets;
- never uploads or deletes anything during inventory.

After building the jar:

```bash
java -jar target/google-photos-migrator-0.1.0-SNAPSHOT.jar inventory \
  --takeout "C:\\Takeout" \
  --ledger "C:\\Takeout\\photos-migration-ledger.xlsx"
```

## Safety model

A source cleanup batch will only be considered `SAFE_TO_DELETE` after all unique media assigned to that batch are uploaded and destination verification succeeds. Source deletion remains an explicit user action.

The utility must never commit OAuth credentials, tokens, Takeout archives, photos/videos, or ledgers to GitHub.

## Development plan

- [x] retire billed Cloud Run deployment
- [x] streaming Takeout ZIP discovery
- [x] SHA-256 inventory
- [x] duplicate detection
- [x] common JSON sidecar timestamp parsing
- [x] local Excel inventory ledger
- [x] automated tests for duplicate detection and ledger output
- [ ] local multi-account OAuth manager
- [ ] destination account capacity rules
- [ ] resumable Google Photos upload from Takeout ZIP streams
- [ ] destination verification and retry/resume
- [ ] cleanup-batch generator
- [ ] Windows GUI
- [ ] self-contained Windows package with bundled Java runtime
- [ ] 5-photo + 1-video acceptance test
- [ ] 1 GB acceptance test

The static GitHub Pages dashboard from the previous approach is retained only as historical/prototype material and is not the target migration runtime.
