# Pampa Store Publisher

Repo-local MCP plugin that lets an AI agent browse, manage, and publish Android APK releases with the Pampa Store workflow.

The publisher is Android-only. Historical EXE/MSI assets may remain in old GitHub releases for
archival purposes, but they are unsupported and are never added to new manifests or releases.

## Tools

- `get_pampa_store_context`
- `get_pampa_store_catalog`
- `list_pampa_store_apps`
- `get_pampa_store_app`
- `get_pampa_store_current_version`
- `list_pampa_store_categories`
- `list_pampa_store_developers`
- `list_pampa_store_releases`
- `get_pampa_store_release`
- `get_pampa_store_repo_file`
- `put_pampa_store_repo_file`
- `upsert_pampa_store_index_entry`
- `remove_pampa_store_index_entry`
- `validate_pampa_store_manifest`
- `probe_pampa_store_android_release`
- `publish_pampa_store_android_release`

## What it can do

1. List the full app catalog with filters and current stable/beta versions.
2. Inspect a single app manifest, current release tags, and GitHub releases.
3. Read and write repository files in the store or app repositories.
4. Add or remove entries from the central store index.
5. Validate app and store manifests before writing.
6. Detect the Android application module and version.
7. Build the release APK unless `skipBuild` is set or `apkPath` is provided.
8. Create the GitHub release and upload the APK asset.
9. Update the app repository `manifest.json`.
10. Update the central Pampa Store `manifest.json`.

## Claude

- Claude Code can use the project-level [`.mcp.json`](/C:/ViboCoded%20projects/Pampa%20store/.mcp.json) directly from this repository.
- Claude Desktop can use [claude-desktop-config.example.json](/C:/ViboCoded%20projects/Pampa%20store/plugins/pampa-store-publisher/claude-desktop-config.example.json) as a ready-made local config snippet.
- On a Windows development machine, `run-pampa-store-publisher.cmd` starts this Android publisher.

## Requirements

- Python 3.10+
- A GitHub token in one of:
  - `PAMPA_GH_TOKEN`
  - `GITHUB_TOKEN`
  - `GH_TOKEN`
- Release signing configured when a build is required

## Notes

- In this repo-local layout the server defaults `repoRoot` to the repository root automatically.
- For a home-local installation, pass `repoRoot` explicitly in tool calls.
- Read-only catalog and release calls can also work without a token against public GitHub repositories.
- Start with `dryRun: true` before pushing a live release.
