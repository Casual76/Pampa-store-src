#!/usr/bin/env python3
"""Store-centric MCP tools for browsing and administering the Pampa Store."""

from __future__ import annotations

import base64
import json
import os
import urllib.parse
from collections import Counter
from pathlib import Path
from typing import Any

import publisher


def get_pampa_store_context(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    repo_root = resolve_repo_root(source)
    plugin_root = Path(__file__).resolve().parent.parent
    store_target = resolve_store_target(source)
    token = publisher.resolve_token(required=False)

    try:
        local_probe = publisher.probe_from_params({"repoRoot": str(repo_root)})
    except publisher.SkillError as error:
        local_probe = {"error": str(error)}

    return {
        "repoRoot": str(repo_root),
        "pluginRoot": str(plugin_root),
        "launcherPath": str(plugin_root / "run-pampa-store-publisher.cmd"),
        "claudeProjectConfigPath": str(repo_root / ".mcp.json"),
        "claudeDesktopExamplePath": str(plugin_root / "claude-desktop-config.example.json"),
        "storeRepository": store_target,
        "tokenPresent": bool(token),
        "localProbe": local_probe,
    }


def get_pampa_store_catalog(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    catalog = fetch_catalog(source)
    return {
        "storeRepository": catalog["storeRepository"],
        "sha": catalog["sha"],
        "appCount": len(catalog["manifest"].get("apps") or []),
        "manifest": catalog["manifest"],
    }


def list_pampa_store_apps(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    query = publisher.coerce_string(source.get("query")).lower()
    category = publisher.coerce_string(source.get("category")).lower()
    developer = publisher.coerce_string(source.get("developer")).lower()
    include_manifest = publisher.coerce_bool(source.get("includeManifest"))
    include_versions = True
    if "includeVersions" in source:
        include_versions = publisher.coerce_bool(source.get("includeVersions"))
    limit = clamp_int(source.get("limit"), default=50, minimum=1, maximum=200)
    offset = clamp_int(source.get("offset"), default=0, minimum=0, maximum=10000)

    catalog = fetch_catalog(source)
    token = publisher.resolve_token(required=False)
    hydrated_items: list[dict[str, Any]] = []

    for entry in catalog["manifest"].get("apps") or []:
        item = build_catalog_entry_summary(entry)
        manifest_summary: dict[str, Any] | None = None
        if include_versions or include_manifest or query or category or developer:
            try:
                manifest_summary = load_app_manifest_summary(entry, token, include_manifest)
                item.update(manifest_summary)
            except publisher.SkillError as error:
                item["manifestError"] = str(error)
        if matches_app_filters(item, query=query, category=category, developer=developer):
            hydrated_items.append(item)

    paged_items = hydrated_items[offset : offset + limit]
    return {
        "storeRepository": catalog["storeRepository"],
        "totalApps": len(catalog["manifest"].get("apps") or []),
        "matchedApps": len(hydrated_items),
        "offset": offset,
        "limit": limit,
        "items": paged_items,
    }


def get_pampa_store_app(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    entry = resolve_app_entry(source)
    token = publisher.resolve_token(required=False)
    manifest, sha = load_app_manifest(entry, token)
    app = manifest.get("app") or {}
    return {
        "catalogEntry": entry,
        "manifestSha": sha,
        "app": app,
        "versions": summarize_versions(app),
    }


def get_pampa_store_current_version(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    app_details = get_pampa_store_app(source)
    channel = publisher.coerce_string(source.get("channel")).lower()
    versions = app_details["versions"]
    if channel in {"stable", "beta"}:
        return {
            "catalogEntry": app_details["catalogEntry"],
            "channel": channel,
            "version": versions.get(channel),
        }
    return {
        "catalogEntry": app_details["catalogEntry"],
        "versions": versions,
    }


def list_pampa_store_categories(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    items = hydrate_all_catalog_entries(source)
    counter = Counter(item.get("category", "").strip() for item in items if item.get("category"))
    return {"categories": build_counter_rows(counter)}


def list_pampa_store_developers(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    items = hydrate_all_catalog_entries(source)
    counter = Counter(item.get("developer", "").strip() for item in items if item.get("developer"))
    return {"developers": build_counter_rows(counter)}


def list_pampa_store_releases(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    repo_target = resolve_repository_target(source)
    limit = clamp_int(source.get("limit"), default=20, minimum=1, maximum=100)
    page = clamp_int(source.get("page"), default=1, minimum=1, maximum=100)
    token = publisher.resolve_token(required=False)
    url = (
        f"https://api.github.com/repos/{repo_target['repoOwner']}/{repo_target['repoName']}/releases"
        f"?per_page={limit}&page={page}"
    )
    releases = publisher.github_request("GET", url, token=token)
    return {
        "repository": repo_target,
        "page": page,
        "limit": limit,
        "releases": [summarize_release(release, include_body=False) for release in releases],
    }


def get_pampa_store_release(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    repo_target = resolve_repository_target(source)
    tag = publisher.coerce_string(source.get("tag"))
    channel = publisher.coerce_string(source.get("channel")).lower()
    if not tag and channel in {"stable", "beta"}:
        app_details = get_pampa_store_app(source)
        channel_info = app_details["app"].get(channel) or {}
        tag = str(channel_info.get("releaseTag", "")).strip()
    if not tag:
        raise publisher.SkillError("Missing tag or channel for get_pampa_store_release.")

    token = publisher.resolve_token(required=False)
    url = f"https://api.github.com/repos/{repo_target['repoOwner']}/{repo_target['repoName']}/releases/tags/{urllib.parse.quote(tag)}"
    release = publisher.github_request("GET", url, token=token)
    return {
        "repository": repo_target,
        "release": summarize_release(release, include_body=True),
    }


def get_pampa_store_repo_file(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    target = resolve_repository_target(source)
    path = publisher.coerce_string(source.get("path")) or target.get("manifestPath", "")
    if not path:
        raise publisher.SkillError("Missing path for get_pampa_store_repo_file.")
    ref = publisher.coerce_string(source.get("ref")) or target.get("ref", "")
    token = publisher.resolve_token(required=False)
    remote_file = publisher.get_remote_file(
        target["repoOwner"],
        target["repoName"],
        path,
        token,
        allow_missing=False,
        ref=ref,
    )
    if remote_file is None:
        raise publisher.SkillError(f"Remote file not found: {path}")

    parse_json = should_parse_json(path, source.get("parseJson"))
    result: dict[str, Any] = {
        "repository": target,
        "path": path,
        "ref": ref,
        "sha": remote_file["sha"],
    }
    if parse_json:
        try:
            result["jsonContent"] = json.loads(remote_file["content"])
            return result
        except json.JSONDecodeError:
            result["jsonParseError"] = "Content is not valid JSON."
    result["textContent"] = remote_file["content"]
    return result


def put_pampa_store_repo_file(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    token = publisher.resolve_token(required=True)
    target = resolve_repository_target(source)
    path = publisher.coerce_string(source.get("path")) or target.get("manifestPath", "")
    if not path:
        raise publisher.SkillError("Missing path for put_pampa_store_repo_file.")
    commit_message = publisher.coerce_string(source.get("commitMessage"))
    if not commit_message:
        raise publisher.SkillError("Missing commitMessage for put_pampa_store_repo_file.")

    content_text = build_put_file_content(source)
    branch = publisher.coerce_string(source.get("branch"))
    sha = publisher.coerce_string(source.get("sha"))
    if not sha:
        existing = publisher.get_remote_file(
            target["repoOwner"],
            target["repoName"],
            path,
            token,
            allow_missing=True,
            ref=branch,
        )
        if existing is not None:
            sha = existing["sha"]

    payload: dict[str, Any] = {
        "message": commit_message,
        "content": base64.b64encode(content_text.encode("utf-8")).decode("ascii"),
    }
    if sha:
        payload["sha"] = sha
    if branch:
        payload["branch"] = branch

    url = f"https://api.github.com/repos/{target['repoOwner']}/{target['repoName']}/contents/{urllib.parse.quote(path, safe='/')}"
    response = publisher.github_request("PUT", url, token=token, payload=payload)
    return {
        "repository": target,
        "path": path,
        "branch": branch,
        "contentSha": (response.get("content") or {}).get("sha", ""),
        "commit": summarize_commit(response.get("commit") or {}),
    }


def upsert_pampa_store_index_entry(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    token = publisher.resolve_token(required=True)
    store_target = resolve_store_target(source)
    catalog = fetch_catalog(source)
    app_id = publisher.coerce_string(source.get("appId"))
    if not app_id:
        raise publisher.SkillError("Missing appId for upsert_pampa_store_index_entry.")
    repo_owner = publisher.coerce_string(source.get("repoOwner"))
    repo_name = publisher.coerce_string(source.get("repoName"))
    manifest_path = publisher.coerce_string(source.get("manifestPath")) or publisher.DEFAULT_MANIFEST_PATH
    if not repo_owner or not repo_name:
        raise publisher.SkillError("Missing repoOwner or repoName for upsert_pampa_store_index_entry.")

    previous_entry = publisher.find_store_entry(catalog["manifest"], app_id)
    updated_manifest = publisher.upsert_store_manifest(
        existing_manifest=catalog["manifest"],
        store_owner=store_target["repoOwner"],
        store_repo=store_target["repoName"],
        app_id=app_id,
        repo_owner=repo_owner,
        repo_name=repo_name,
        manifest_path=manifest_path,
    )
    commit_message = (
        publisher.coerce_string(source.get("commitMessage"))
        or f"Aggiorna indice store per {app_id}"
    )
    write_result = put_pampa_store_repo_file(
        {
            "repoOwner": store_target["repoOwner"],
            "repoName": store_target["repoName"],
            "path": store_target["manifestPath"],
            "jsonContent": updated_manifest,
            "sha": catalog["sha"],
            "commitMessage": commit_message,
            "branch": publisher.coerce_string(source.get("branch")),
        }
    )
    return {
        "storeRepository": store_target,
        "previousEntry": previous_entry,
        "currentEntry": publisher.find_store_entry(updated_manifest, app_id),
        "writeResult": write_result,
    }


def remove_pampa_store_index_entry(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    store_target = resolve_store_target(source)
    catalog = fetch_catalog(source)
    app_id = publisher.coerce_string(source.get("appId"))
    if not app_id:
        raise publisher.SkillError("Missing appId for remove_pampa_store_index_entry.")
    previous_entry = publisher.find_store_entry(catalog["manifest"], app_id)
    if previous_entry is None:
        raise publisher.SkillError(f"App '{app_id}' is not present in the store index.")

    updated_manifest = publisher.deep_copy(catalog["manifest"])
    updated_manifest["apps"] = [
        entry for entry in updated_manifest.get("apps") or [] if str(entry.get("id", "")).strip() != app_id
    ]
    commit_message = (
        publisher.coerce_string(source.get("commitMessage"))
        or f"Rimuove {app_id} dall'indice store"
    )
    write_result = put_pampa_store_repo_file(
        {
            "repoOwner": store_target["repoOwner"],
            "repoName": store_target["repoName"],
            "path": store_target["manifestPath"],
            "jsonContent": updated_manifest,
            "sha": catalog["sha"],
            "commitMessage": commit_message,
            "branch": publisher.coerce_string(source.get("branch")),
        }
    )
    return {
        "storeRepository": store_target,
        "removedEntry": previous_entry,
        "remainingApps": len(updated_manifest.get("apps") or []),
        "writeResult": write_result,
    }


def validate_pampa_store_manifest(params: dict[str, Any] | None) -> dict[str, Any]:
    source = params or {}
    manifest = resolve_manifest_for_validation(source)
    manifest_type = publisher.coerce_string(source.get("manifestType")).lower()
    if not manifest_type:
        manifest_type = "store" if "apps" in manifest else "app"

    errors: list[str] = []
    warnings: list[str] = []
    summary: dict[str, Any]
    if manifest_type == "store":
        summary = validate_store_manifest(manifest, errors, warnings)
    else:
        summary = validate_app_manifest(manifest, errors, warnings)

    return {
        "manifestType": manifest_type,
        "valid": not errors,
        "errors": errors,
        "warnings": warnings,
        "summary": summary,
    }


def fetch_catalog(params: dict[str, Any]) -> dict[str, Any]:
    store_target = resolve_store_target(params)
    token = publisher.resolve_token(required=False)
    manifest, sha, _ = read_json_repo_file(
        owner=store_target["repoOwner"],
        repo=store_target["repoName"],
        path=store_target["manifestPath"],
        token=token,
        allow_missing=False,
        ref=store_target["ref"],
    )
    normalized = publisher.normalize_store_manifest(manifest, store_target["repoOwner"], store_target["repoName"])
    return {"storeRepository": store_target, "manifest": normalized, "sha": sha}


def hydrate_all_catalog_entries(params: dict[str, Any]) -> list[dict[str, Any]]:
    catalog = fetch_catalog(params)
    token = publisher.resolve_token(required=False)
    items: list[dict[str, Any]] = []
    for entry in catalog["manifest"].get("apps") or []:
        item = build_catalog_entry_summary(entry)
        try:
            item.update(load_app_manifest_summary(entry, token, include_manifest=False))
        except publisher.SkillError as error:
            item["manifestError"] = str(error)
        items.append(item)
    return items


def resolve_app_entry(params: dict[str, Any]) -> dict[str, Any]:
    app_id = publisher.coerce_string(params.get("appId"))
    if not app_id:
        raise publisher.SkillError("Missing appId.")
    catalog = fetch_catalog(params)
    entry = publisher.find_store_entry(catalog["manifest"], app_id)
    if entry is None:
        raise publisher.SkillError(f"App '{app_id}' was not found in the Pampa Store catalog.")
    return entry


def resolve_repository_target(params: dict[str, Any]) -> dict[str, str]:
    repo_owner = publisher.coerce_string(params.get("repoOwner"))
    repo_name = publisher.coerce_string(params.get("repoName"))
    manifest_path = publisher.coerce_string(params.get("manifestPath"))
    ref = publisher.coerce_string(params.get("ref"))
    if repo_owner and repo_name:
        return {
            "repoOwner": repo_owner,
            "repoName": repo_name,
            "manifestPath": manifest_path,
            "ref": ref,
        }
    if not publisher.coerce_string(params.get("appId")):
        store_target = resolve_store_target(params)
        return {
            "repoOwner": store_target["repoOwner"],
            "repoName": store_target["repoName"],
            "manifestPath": manifest_path or store_target["manifestPath"],
            "ref": ref or store_target["ref"],
        }
    entry = resolve_app_entry(params)
    return {
        "repoOwner": entry["repoOwner"],
        "repoName": entry["repoName"],
        "manifestPath": entry.get("manifestPath", publisher.DEFAULT_MANIFEST_PATH),
        "ref": entry.get("ref", ""),
    }


def resolve_store_target(params: dict[str, Any]) -> dict[str, str]:
    return {
        "repoOwner": (
            publisher.coerce_string(params.get("storeOwner"))
            or os.getenv("PAMPA_REPO_OWNER", "").strip()
            or publisher.DEFAULT_STORE_OWNER
        ),
        "repoName": (
            publisher.coerce_string(params.get("storeRepo"))
            or os.getenv("PAMPA_REPO_NAME", "").strip()
            or publisher.DEFAULT_STORE_REPO
        ),
        "manifestPath": publisher.coerce_string(params.get("storeManifestPath")) or publisher.DEFAULT_MANIFEST_PATH,
        "ref": publisher.coerce_string(params.get("storeRef")),
    }


def resolve_repo_root(params: dict[str, Any]) -> Path:
    return Path(publisher.coerce_string(params.get("repoRoot")) or str(publisher.default_repo_root())).resolve()


def read_json_repo_file(
    owner: str,
    repo: str,
    path: str,
    token: str | None,
    allow_missing: bool,
    ref: str = "",
) -> tuple[dict[str, Any], str | None, str]:
    remote_file = publisher.get_remote_file(owner, repo, path, token, allow_missing=allow_missing, ref=ref)
    if remote_file is None:
        raise publisher.SkillError(f"Remote JSON file not found: {owner}/{repo}:{path}")
    try:
        parsed = json.loads(remote_file["content"])
    except json.JSONDecodeError as error:
        raise publisher.SkillError(f"Invalid JSON in {owner}/{repo}:{path}: {error}") from error
    return parsed, remote_file["sha"], remote_file["content"]


def load_app_manifest(entry: dict[str, Any], token: str | None) -> tuple[dict[str, Any], str | None]:
    manifest, sha, _ = read_json_repo_file(
        owner=entry["repoOwner"],
        repo=entry["repoName"],
        path=entry.get("manifestPath", publisher.DEFAULT_MANIFEST_PATH),
        token=token,
        allow_missing=False,
        ref=entry.get("ref", ""),
    )
    if "app" not in manifest:
        manifest = {"app": manifest}
    return manifest, sha


def load_app_manifest_summary(entry: dict[str, Any], token: str | None, include_manifest: bool) -> dict[str, Any]:
    manifest, _ = load_app_manifest(entry, token)
    app = manifest.get("app") or {}
    result = {
        "name": str(app.get("name", "")),
        "description": str(app.get("description", "")),
        "packageName": str(app.get("packageName", "")),
        "category": str(app.get("category", "")),
        "developer": str(app.get("developer", "")),
        "iconUrl": str(app.get("iconUrl", "")),
        "screenshotsCount": len(app.get("screenshots") or []),
        "versions": summarize_versions(app),
    }
    if include_manifest:
        result["manifest"] = manifest
    return result


def build_catalog_entry_summary(entry: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": entry.get("id", ""),
        "repoOwner": entry.get("repoOwner", ""),
        "repoName": entry.get("repoName", ""),
        "manifestPath": entry.get("manifestPath", publisher.DEFAULT_MANIFEST_PATH),
        "ref": entry.get("ref", ""),
    }


def summarize_versions(app: dict[str, Any]) -> dict[str, Any]:
    stable = app.get("stable") or {}
    beta = app.get("beta") or {}
    history = app.get("history") or []
    return {
        "stable": summarize_channel(stable),
        "beta": summarize_channel(beta),
        "historyCount": len(history),
    }


def summarize_channel(channel_info: dict[str, Any]) -> dict[str, Any]:
    return {
        "version": str(channel_info.get("version", "")),
        "releaseDate": str(channel_info.get("releaseDate", "")),
        "changelog": str(channel_info.get("changelog", "")),
        "releaseTag": str(channel_info.get("releaseTag", "")),
        "apkAsset": str(channel_info.get("apkAsset", "")),
        "sizeBytes": int(channel_info.get("sizeBytes", 0) or 0),
    }


def summarize_release(release: dict[str, Any], include_body: bool) -> dict[str, Any]:
    body = str(release.get("body", "") or "")
    result = {
        "id": release.get("id"),
        "tagName": release.get("tag_name", ""),
        "name": release.get("name", ""),
        "draft": bool(release.get("draft")),
        "prerelease": bool(release.get("prerelease")),
        "createdAt": release.get("created_at", ""),
        "publishedAt": release.get("published_at", ""),
        "htmlUrl": release.get("html_url", ""),
        "assets": [summarize_asset(asset) for asset in release.get("assets") or []],
    }
    result["body"] = body if include_body else publisher.truncate_text(body, 240)
    return result


def summarize_asset(asset: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": asset.get("id"),
        "name": asset.get("name", ""),
        "sizeBytes": int(asset.get("size", 0) or 0),
        "contentType": asset.get("content_type", ""),
        "state": asset.get("state", ""),
        "downloadUrl": asset.get("browser_download_url", ""),
        "updatedAt": asset.get("updated_at", ""),
    }


def summarize_commit(commit: dict[str, Any]) -> dict[str, Any]:
    return {
        "sha": commit.get("sha", ""),
        "message": commit.get("message", ""),
        "htmlUrl": commit.get("html_url", ""),
    }


def build_put_file_content(source: dict[str, Any]) -> str:
    if "jsonContent" in source and source.get("jsonContent") is not None:
        return json.dumps(source.get("jsonContent"), indent=2, ensure_ascii=False)
    text_content = source.get("textContent")
    if text_content is None:
        text_content = source.get("content")
    if text_content is None:
        raise publisher.SkillError("Missing textContent/content or jsonContent for put_pampa_store_repo_file.")
    return str(text_content)


def matches_app_filters(item: dict[str, Any], query: str, category: str, developer: str) -> bool:
    if category and item.get("category", "").lower() != category:
        return False
    if developer and item.get("developer", "").lower() != developer:
        return False
    if not query:
        return True
    haystack_parts = [
        item.get("id", ""),
        item.get("name", ""),
        item.get("description", ""),
        item.get("packageName", ""),
        item.get("category", ""),
        item.get("developer", ""),
        item.get("repoOwner", ""),
        item.get("repoName", ""),
    ]
    haystack = " ".join(str(part).lower() for part in haystack_parts if part)
    return query in haystack


def should_parse_json(path: str, parse_json_value: Any) -> bool:
    if parse_json_value is None:
        return path.lower().endswith(".json")
    return publisher.coerce_bool(parse_json_value)


def resolve_manifest_for_validation(source: dict[str, Any]) -> dict[str, Any]:
    if "jsonContent" in source and source.get("jsonContent") is not None:
        value = source.get("jsonContent")
        if isinstance(value, str):
            return json.loads(value)
        if isinstance(value, dict):
            return value
        raise publisher.SkillError("jsonContent must be an object or a JSON string.")

    if "content" in source and source.get("content") is not None:
        return json.loads(str(source.get("content")))

    manifest_type = publisher.coerce_string(source.get("manifestType")).lower()
    if manifest_type == "store":
        return get_pampa_store_catalog(source)["manifest"]
    if manifest_type == "app" or publisher.coerce_string(source.get("appId")):
        return get_pampa_store_app(source)["app"]

    repo_file = get_pampa_store_repo_file(source)
    if "jsonContent" in repo_file:
        return repo_file["jsonContent"]
    raise publisher.SkillError("The selected file is not valid JSON and cannot be validated.")


def validate_store_manifest(manifest: dict[str, Any], errors: list[str], warnings: list[str]) -> dict[str, Any]:
    apps = manifest.get("apps")
    if not isinstance(apps, list):
        errors.append("Store manifest must contain an 'apps' array.")
        apps = []
    if "storeReleases" not in manifest:
        warnings.append("Store manifest does not contain 'storeReleases'.")

    valid_apps = 0
    for index, entry in enumerate(apps):
        if not isinstance(entry, dict):
            errors.append(f"apps[{index}] must be an object.")
            continue
        for field in ("id", "repoOwner", "repoName", "manifestPath"):
            if not str(entry.get(field, "")).strip():
                errors.append(f"apps[{index}] is missing '{field}'.")
        valid_apps += 1
    return {"appCount": len(apps), "validEntries": valid_apps}


def validate_app_manifest(manifest: dict[str, Any], errors: list[str], warnings: list[str]) -> dict[str, Any]:
    app = manifest.get("app", manifest)
    if not isinstance(app, dict):
        errors.append("App manifest must be an object with an 'app' payload.")
        return {}
    for field in ("id", "name", "description", "packageName"):
        if not str(app.get(field, "")).strip():
            errors.append(f"App manifest is missing '{field}'.")
    repository = app.get("repository")
    if not isinstance(repository, dict):
        errors.append("App manifest is missing the 'repository' object.")
    else:
        for field in ("repoOwner", "repoName", "manifestPath"):
            if not str(repository.get(field, "")).strip():
                errors.append(f"App repository is missing '{field}'.")
    for channel in ("stable", "beta"):
        channel_info = app.get(channel)
        if not channel_info:
            warnings.append(f"App manifest does not contain the '{channel}' section.")
            continue
        for field in ("version", "releaseDate", "releaseTag", "apkAsset"):
            if not str(channel_info.get(field, "")).strip():
                errors.append(f"Channel '{channel}' is missing '{field}'.")
    return summarize_versions(app)


def clamp_int(value: Any, default: int, minimum: int, maximum: int) -> int:
    if value is None or value == "":
        return default
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return default
    return max(minimum, min(maximum, parsed))


def build_counter_rows(counter: Counter[str]) -> list[dict[str, Any]]:
    rows = [{"name": name, "count": count} for name, count in counter.items()]
    rows.sort(key=lambda row: (-row["count"], row["name"].lower()))
    return rows
