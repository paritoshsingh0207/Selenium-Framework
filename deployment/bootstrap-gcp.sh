#!/usr/bin/env bash
set -Eeuo pipefail

cat >&2 <<'EOF'
This deployment path has been retired.

The Google Photos Migrator is now constrained to ZERO-BILLING operation:
- do not attach a Google Cloud billing account
- do not create Cloud Run, Cloud Storage, Secret Manager, Artifact Registry, or Cloud Build resources for this project
- GitHub Pages remains the supported hosted frontend

If you previously ran the old Google Cloud bootstrap, follow deployment/GOOGLE_CLOUD_SETUP.md to unlink billing and delete the dedicated migration project.
EOF

exit 2
