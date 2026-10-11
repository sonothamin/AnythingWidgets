# Placeholder signing key

`dev.p12` is a **public, non-secret** key used only so CI release APKs are signed and can update over each
other while the project is in development. Anyone can sign an APK with it, so never ship to users with it.

For production, add these repository secrets and CI will use them instead (no code change needed):
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Switching keys changes the app's signature, so installs signed with the placeholder must be uninstalled first.
