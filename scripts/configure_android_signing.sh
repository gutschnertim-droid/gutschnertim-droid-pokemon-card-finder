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
expected="48:29:26:07:1D:A9:D7:A1:A0:73:64:E2:1E:52:F5:F6:30:51:51:67:98:80:E0:67:E4:63:6B:DA:28:E4:AC:C4"
if [[ "$fingerprint" != "$expected" ]]; then
  echo "Keystore fingerprint does not match the pinned application signing identity." >&2
  exit 1
fi

base64 < "$KEYSTORE" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$ANDROID_KEYSTORE_PASSWORD" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$REPO"
unset ANDROID_KEYSTORE_PASSWORD
echo "Both GitHub Actions secrets have been uploaded without committing the private key."
echo "Next: Actions > Build local Android live test > Run workflow."
