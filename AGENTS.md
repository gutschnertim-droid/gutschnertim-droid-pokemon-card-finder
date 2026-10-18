# Project instructions

## Permanent Android signing identity

- Every future Android release must use the existing signing key stored in the GitHub Actions secrets `ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD`.
- Preserve the certificate fingerprint pinned in `.github/workflows/build-android-test.yml`. Do not generate, replace, rotate, or remove the signing key, change the pinned fingerprint, or rename/delete the secrets unless the user explicitly requests a signing identity reset.
- Never commit or print the PKCS12 keystore, its Base64 contents, or its password. Do not expose secret values in chat, workflow logs, artifacts, or repository files.
- Verify both the keystore certificate and the built APK against the pinned fingerprint before publishing. Stop publication if either check fails.
- Preserve the app's `applicationId` and keep published `versionCode` values strictly increasing so users can install each release as an update and retain local app data.
- If a proposed change would alter the signing identity or prevent an in-place update, explain the data-loss and reinstall impact before proceeding.
