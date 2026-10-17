#!/usr/bin/env bash
set -euo pipefail

# Run locally; NEVER commit the private signing keystore or its password.
REPO="gutschnertim-droid/gutschnertim-droid-pokemon-card-finder"
if [[ $# -ne 2 ]]; then
  echo "Usage: bash scripts/configure_android_signing.sh /private/path/pokemon-card-finder-signing-key.p12 /private/path/pokemon-card-finder-ANDROID_KEYSTORE_PASSWORD.txt" >&2
  exit 2
fi

KEYSTORE="$1"
PASSWORD_FILE="$2"
[[ -s "$KEYSTORE" ]] || { echo "Missing keystore file." >&2; exit 1; }
[[ -s "$PASSWORD_FILE" ]] || { echo "Missing password file." >&2; exit 1; }
command -v gh >/dev/null || { echo "GitHub CLI (gh) is required: https://cli.github.com/" >&2; exit 1; }
command -v keytool >/dev/null || { echo "JDK keytool is required." >&2; exit 1; }
gh auth status >/dev/null

# Read credentials from files; never use plaintext command-line values or verbose logs.
export ANDROID_KEYSTORE_PASSWORD
ANDROID_KEYSTORE_PASSWORD="$(tr -d '\r\n' < "$PASSWORD_FILE")"
fingerprint="$(keytool -list -v -alias pcf-update -storetype PKCS12 -keystore "$KEYSTORE" -storepass:env ANDROID_KEYSTORE_PASSWORD | awk -F 'SHA256: ' '/SHA256:/ { print $2; exit }')"
expected="4F:AA:C0:EA:65:3F:83:E6:DE:EA:D3:65:B9:EB:BD:2B:BD:D0:87:41:B1:AA:49:24:95:52:D1:12:BC:39:86:47"
if [[ "$fingerprint" != "$expected" ]]; then
  echo "Keystore fingerprint does not match the pinned application signing identity." >&2
  exit 1
fi

base64 < "$KEYSTORE" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$ANDROID_KEYSTORE_PASSWORD" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$REPO"
unset ANDROID_KEYSTORE_PASSWORD
echo "Both GitHub Actions secrets have been uploaded without committing the private key."
echo "Next: Actions > Build local Android live test > Run workflow."
