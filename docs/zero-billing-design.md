# Zero-Billing Google Photos Migration Design

## Hard constraint

This project must not require a billing account, paid Google Cloud services, Cloud Run, Cloud Storage, Secret Manager, Firebase billing, paid workers, or any paid backend.

## Production architecture

The migration is orchestrated from GitHub Pages, but Google Photos itself performs the account-to-account copy.

### Mode A — Whole library

Use Google Photos Partner Sharing:

1. Account A enables Partner Sharing.
2. Choose **All time** and **All photos**.
3. Invite Account B.
4. Account B accepts the invitation.
5. Account B chooses **Save to your account → All photos**.
6. Verify media are visible in Account B before any source cleanup.

This is the preferred mode for a complete library because Google performs the copy internally; media bytes never pass through this project.

### Mode B — Selected batch

Use a Google Photos Shared Album:

1. In Account A select the required photos/videos.
2. Create a shared album named with the generated migration batch ID.
3. Share the album directly with Account B (prefer direct account sharing over a public link).
4. Account B opens the album and chooses **Save all photos and videos**.
5. Verify the saved media in Account B.
6. Mark the batch VERIFIED in the GitHub Pages ledger.

Use multiple batches when a selected migration is too large to verify comfortably in one pass.

## GitHub Pages responsibilities

The static site will:

- generate migration IDs and batch IDs;
- store migration progress only in the browser;
- guide the source and destination steps;
- record source count, destination count, verification state, notes and timestamps;
- export a migration ledger as `.xlsx` without sending the ledger to a backend;
- keep source deletion as an explicit manual action outside the application.

## What the site will not do

- No Google OAuth tokens.
- No Google Cloud project is required for the migration runtime.
- No Google Photos API calls are required for the migration runtime.
- No photo/video bytes are downloaded, proxied, buffered, uploaded, or stored by this project.
- No automatic source deletion.
- No promise that album organization from a complete library is recreated automatically. Partner Sharing primarily moves media, not arbitrary album structure.

## Verification model

A migration is complete only when the user has confirmed the destination copy.

Suggested states:

- PLANNED
- SOURCE_SHARED
- DESTINATION_ACCEPTED
- SAVE_REQUESTED
- VERIFYING
- VERIFIED
- NEEDS_REVIEW

For selected-batch mode, source and destination item counts should match before marking VERIFIED.

For whole-library Partner Sharing, verify representative oldest/newest photos, videos, several dates, and any especially important media before considering source cleanup.

## Cost model

- GitHub Pages: no project billing account.
- Media transfer: handled by Google Photos sharing features.
- Project backend: none.
- Infrastructure bill: none by design.

Destination Google Account storage rules still apply. Google states that photos/videos saved from a normal shared album count toward the receiving account's quota; Partner Sharing has special storage behavior while the partner continues sharing.
