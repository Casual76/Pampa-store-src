# Secrets and Credentials

Do not hardcode credentials in source code.

## Preferred setup

Use environment variables for publishing and release signing:

- `PAMPA_GH_TOKEN`
- `PAMPA_REPO_OWNER`
- `PAMPA_REPO_NAME`
- `PAMPA_RELEASE_STORE_FILE`
- `PAMPA_RELEASE_STORE_PASSWORD`
- `PAMPA_RELEASE_KEY_ALIAS`
- `PAMPA_RELEASE_KEY_PASSWORD`

`local.properties` can be used only for local development convenience and must never be committed.

## Admin tools visibility

Admin tools are available only when both conditions are true:

1. Build-level admin flag is enabled (debug/development build).
2. Publishing service is configured with valid GitHub credentials.
