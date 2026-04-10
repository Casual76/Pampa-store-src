#!/usr/bin/env python3
"""Build and publish Android APK releases to GitHub and Pampa Store."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

DEFAULT_STORE_OWNER = "Casual76"
DEFAULT_STORE_REPO = "Pampa-store"
DEFAULT_MANIFEST_PATH = "manifest.json"
USER_AGENT = "PampaStorePublisherMCP/1.0"


class SkillError(RuntimeError):
    """User-facing error for the publisher workflow."""


class GitHubHttpError(SkillError):
    """GitHub REST API error with status code."""

    def __init__(self, status: int, url: str, preview: str) -> None:
        self.status = status
        self.url = url
        self.preview = preview
        message = f"GitHub API error {status} on {url}"
        if preview:
            message = f"{message}: {preview}"
        super().__init__(message)


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    try:
        if args.command == "probe":
            result = probe(args)
        elif args.command == "publish":
            result = publish(args)
        else:
            raise SkillError("Unknown command.")
    except SkillError as error:
        print(str(error), file=sys.stderr)
        return 1

    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Build Android APKs, publish GitHub releases, and sync Pampa Store manifests."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    probe_parser = subparsers.add_parser("probe", help="Inspect the repository and print detected metadata.")
    add_common_arguments(probe_parser)
    probe_parser.add_argument("--repo-owner", default="", help="Override the GitHub repository owner.")
    probe_parser.add_argument("--repo-name", default="", help="Override the GitHub repository name.")

    publish_parser = subparsers.add_parser("publish", help="Publish a release and update manifests.")
    add_common_arguments(publish_parser)
    publish_parser.add_argument("--channel", choices=("stable", "beta"), required=True)
    publish_parser.add_argument("--repo-owner", default="", help="Override the GitHub repository owner.")
    publish_parser.add_argument("--repo-name", default="", help="Override the GitHub repository name.")
    publish_parser.add_argument("--version", default="", help="Explicit app version.")
    publish_parser.add_argument("--app-id", default="", help="Stable slug used in tags and asset names.")
    publish_parser.add_argument("--app-name", default="", help="Human-facing app name for release titles.")
    publish_parser.add_argument("--package-name", default="", help="Android package/application id.")
    publish_parser.add_argument("--description", default="", help="App description for manifest bootstrap.")
    publish_parser.add_argument("--developer", default="", help="Developer label for the app manifest.")
    publish_parser.add_argument("--category", default="", help="Category label for the app manifest.")
    publish_parser.add_argument("--changelog", default="", help="Inline changelog for the release.")
    publish_parser.add_argument("--changelog-file", default="", help="Path to a text file with the changelog.")
    publish_parser.add_argument("--manifest-path", default=DEFAULT_MANIFEST_PATH, help="Manifest path inside the app repository.")
    publish_parser.add_argument("--store-owner", default="", help="Override the central Pampa Store owner.")
    publish_parser.add_argument("--store-repo", default="", help="Override the central Pampa Store repo.")
    publish_parser.add_argument("--store-manifest-path", default=DEFAULT_MANIFEST_PATH, help="Manifest path in the store repository.")
    publish_parser.add_argument("--apk-path", default="", help="Use an existing APK instead of discovering one.")
    publish_parser.add_argument("--task", default="", help="Explicit Gradle release task.")
    publish_parser.add_argument("--skip-build", action="store_true", help="Skip the Gradle build step.")
    publish_parser.add_argument("--dry-run", action="store_true", help="Compute the publish plan without pushing changes.")
    return parser


def add_common_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--repo-root",
        default=".",
        help="Repository root to inspect. Default: current working directory.",
    )
    parser.add_argument(
        "--module",
        default="",
        help="Hint for the Android application module name or path.",
    )


def default_repo_root() -> Path:
    env_repo_root = os.getenv("PAMPA_STORE_REPO_ROOT", "").strip()
    if env_repo_root:
        return Path(env_repo_root).expanduser().resolve()
    resolved = Path(__file__).resolve()
    try:
        return resolved.parents[3]
    except IndexError:
        return Path.cwd()


def probe_from_params(params: dict[str, Any] | None) -> dict[str, Any]:
    return probe(build_probe_namespace(params))


def publish_from_params(params: dict[str, Any] | None) -> dict[str, Any]:
    return publish(build_publish_namespace(params))


def build_probe_namespace(params: dict[str, Any] | None) -> argparse.Namespace:
    source = params or {}
    repo_root = coerce_string(source.get("repoRoot")) or str(default_repo_root())
    return argparse.Namespace(
        command="probe",
        repo_root=repo_root,
        module=coerce_string(source.get("module")),
        repo_owner=coerce_string(source.get("repoOwner")),
        repo_name=coerce_string(source.get("repoName")),
    )


def build_publish_namespace(params: dict[str, Any] | None) -> argparse.Namespace:
    source = params or {}
    channel = coerce_string(source.get("channel"))
    if not channel:
        raise SkillError("Missing required field 'channel'.")
    if channel not in {"stable", "beta"}:
        raise SkillError("Field 'channel' must be either 'stable' or 'beta'.")

    repo_root = coerce_string(source.get("repoRoot")) or str(default_repo_root())
    return argparse.Namespace(
        command="publish",
        repo_root=repo_root,
        module=coerce_string(source.get("module")),
        channel=channel,
        repo_owner=coerce_string(source.get("repoOwner")),
        repo_name=coerce_string(source.get("repoName")),
        version=coerce_string(source.get("version")),
        app_id=coerce_string(source.get("appId")),
        app_name=coerce_string(source.get("appName")),
        package_name=coerce_string(source.get("packageName")),
        description=coerce_string(source.get("description")),
        developer=coerce_string(source.get("developer")),
        category=coerce_string(source.get("category")),
        changelog=coerce_string(source.get("changelog")),
        changelog_file=coerce_string(source.get("changelogFile")),
        manifest_path=coerce_string(source.get("manifestPath")) or DEFAULT_MANIFEST_PATH,
        store_owner=coerce_string(source.get("storeOwner")),
        store_repo=coerce_string(source.get("storeRepo")),
        store_manifest_path=coerce_string(source.get("storeManifestPath")) or DEFAULT_MANIFEST_PATH,
        apk_path=coerce_string(source.get("apkPath")),
        task=coerce_string(source.get("task")),
        skip_build=coerce_bool(source.get("skipBuild")),
        dry_run=coerce_bool(source.get("dryRun")),
    )


def coerce_string(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()
    return str(value).strip()


def coerce_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return bool(value)
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"1", "true", "yes", "on"}:
            return True
        if normalized in {"0", "false", "no", "off", ""}:
            return False
    return False


def probe(args: argparse.Namespace) -> dict[str, Any]:
    repo_root = Path(args.repo_root).resolve()
    ensure_repo_root(repo_root)
    remote = resolve_repository(args.repo_owner, args.repo_name, repo_root)
    project_name = discover_project_name(repo_root)
    modules = discover_android_modules(repo_root)
    selected_module = choose_module(modules, args.module)
    apk_candidates = find_release_apks(repo_root, selected_module["module_dir"] if selected_module else None)
    token = resolve_token(required=False)

    return {
        "repoRoot": str(repo_root),
        "projectName": project_name,
        "repository": remote,
        "tokenPresent": bool(token),
        "gradleWrapper": str(find_gradle_wrapper(repo_root)) if has_gradle_wrapper(repo_root) else "",
        "selectedModule": serialize_module(selected_module),
        "modules": [serialize_module(module) for module in modules],
        "apkCandidates": [serialize_apk(path) for path in apk_candidates[:5]],
        "notes": build_probe_notes(remote, selected_module, token, apk_candidates),
    }


def publish(args: argparse.Namespace) -> dict[str, Any]:
    repo_root = Path(args.repo_root).resolve()
    ensure_repo_root(repo_root)
    token = resolve_token(required=not args.dry_run)
    repository = resolve_repository(args.repo_owner, args.repo_name, repo_root)
    if not repository["repoOwner"] or not repository["repoName"]:
        raise SkillError(
            "Unable to determine the GitHub repository. Configure origin or pass --repo-owner and --repo-name."
        )

    modules = discover_android_modules(repo_root)
    selected_module = choose_module(modules, args.module)
    if not selected_module and not args.apk_path:
        raise SkillError("No Android application module found. Pass --apk-path for an already-built APK.")

    app_manifest_path = args.manifest_path.strip() or DEFAULT_MANIFEST_PATH
    app_manifest, app_manifest_sha = load_app_manifest(
        repository["repoOwner"],
        repository["repoName"],
        app_manifest_path,
        token,
        allow_missing=True,
    )

    app_id = resolve_app_id(args, repository, app_manifest)
    app_name = resolve_app_name(args, repository, repo_root, app_manifest, selected_module)
    package_name = resolve_package_name(args, app_manifest, selected_module)
    version = resolve_version(args, app_manifest, selected_module)
    channel = args.channel
    ensure_version_is_newer(app_manifest, channel, version)
    changelog = resolve_changelog(args, version)
    description = resolve_description(args, app_manifest, app_name)
    developer = resolve_field(args.developer, app_manifest_get(app_manifest, "developer"), "Pampa")
    category = resolve_field(args.category, app_manifest_get(app_manifest, "category"), "General")

    apk_path = resolve_apk_path(args, repo_root, selected_module)
    apk_stats = inspect_apk(apk_path)
    release_tag = f"{channel}-{app_id}-v{version}"
    release_name = f"{app_name} {version}" if channel == "stable" else f"{app_name} Beta {version}"
    asset_name = f"{app_id}-{version}.apk"

    store_owner = args.store_owner.strip() or os.getenv("PAMPA_REPO_OWNER", "").strip() or DEFAULT_STORE_OWNER
    store_repo = args.store_repo.strip() or os.getenv("PAMPA_REPO_NAME", "").strip() or DEFAULT_STORE_REPO
    store_manifest_path = args.store_manifest_path.strip() or DEFAULT_MANIFEST_PATH

    store_manifest, store_manifest_sha = load_store_manifest(
        store_owner,
        store_repo,
        store_manifest_path,
        token,
        allow_missing=True,
    )

    version_info = build_version_info(
        version=version,
        changelog=changelog,
        release_tag=release_tag,
        asset_name=asset_name,
        size_bytes=apk_stats["sizeBytes"],
    )
    updated_app_manifest = upsert_app_manifest(
        existing_manifest=app_manifest,
        repo_owner=repository["repoOwner"],
        repo_name=repository["repoName"],
        manifest_path=app_manifest_path,
        app_id=app_id,
        app_name=app_name,
        description=description,
        package_name=package_name,
        developer=developer,
        category=category,
        channel=channel,
        version_info=version_info,
    )
    updated_store_manifest = upsert_store_manifest(
        existing_manifest=store_manifest,
        store_owner=store_owner,
        store_repo=store_repo,
        app_id=app_id,
        repo_owner=repository["repoOwner"],
        repo_name=repository["repoName"],
        manifest_path=app_manifest_path,
    )

    summary: dict[str, Any] = {
        "mode": "dry-run" if args.dry_run else "publish",
        "repoRoot": str(repo_root),
        "repository": repository,
        "channel": channel,
        "appId": app_id,
        "appName": app_name,
        "packageName": package_name,
        "version": version,
        "releaseTag": release_tag,
        "releaseName": release_name,
        "appManifestPath": app_manifest_path,
        "storeRepository": {
            "repoOwner": store_owner,
            "repoName": store_repo,
            "manifestPath": store_manifest_path,
        },
        "apk": {
            "path": str(apk_path),
            "assetName": asset_name,
            **apk_stats,
        },
        "selectedModule": serialize_module(selected_module),
        "appManifestPreview": updated_app_manifest,
        "storeEntryPreview": find_store_entry(updated_store_manifest, app_id),
    }

    if args.dry_run:
        dry_run_notes = [
            "Dry-run completed. No GitHub release or manifest update was pushed.",
            "Review the previews, then rerun without dryRun.",
        ]
        if not token:
            dry_run_notes.append("No GitHub token was available, so remote manifests were not fetched during the dry-run.")
        summary["notes"] = dry_run_notes
        return summary

    if version_exists(owner=repository["repoOwner"], repo=repository["repoName"], tag=release_tag, token=token):
        raise SkillError(
            f"Release tag '{release_tag}' already exists in {repository['repoOwner']}/{repository['repoName']}."
        )

    release_payload = create_release(
        owner=repository["repoOwner"],
        repo=repository["repoName"],
        token=token,
        tag=release_tag,
        name=release_name,
        body=changelog,
        prerelease=(channel == "beta"),
    )
    upload_asset(
        upload_url=release_payload["upload_url"],
        token=token,
        asset_name=asset_name,
        content_type="application/vnd.android.package-archive",
        payload=apk_path.read_bytes(),
    )
    upsert_remote_file(
        owner=repository["repoOwner"],
        repo=repository["repoName"],
        manifest_path=app_manifest_path,
        token=token,
        content=updated_app_manifest,
        existing_sha=app_manifest_sha,
        create_message=f"Inizializza manifest app {app_id}",
        update_message=f"Aggiorna manifest app {app_id}",
    )
    upsert_remote_file(
        owner=store_owner,
        repo=store_repo,
        manifest_path=store_manifest_path,
        token=token,
        content=updated_store_manifest,
        existing_sha=store_manifest_sha,
        create_message="Inizializza indice store",
        update_message="Aggiorna indice store",
    )

    summary["release"] = {
        "id": release_payload.get("id"),
        "htmlUrl": release_payload.get("html_url", ""),
        "uploadUrl": release_payload.get("upload_url", ""),
        "prerelease": bool(release_payload.get("prerelease")),
    }
    summary["notes"] = [
        "Release pushed to GitHub.",
        "App manifest and store index updated.",
    ]
    return summary


def ensure_repo_root(repo_root: Path) -> None:
    if not repo_root.exists():
        raise SkillError(f"Repository root does not exist: {repo_root}")


def build_probe_notes(
    remote: dict[str, str],
    selected_module: dict[str, Any] | None,
    token: str | None,
    apk_candidates: list[Path],
) -> list[str]:
    notes: list[str] = []
    if not remote["repoOwner"] or not remote["repoName"]:
        notes.append("GitHub repository not detected from origin. Pass repoOwner and repoName if needed.")
    if not selected_module:
        notes.append("No Android application module detected.")
    if not token:
        notes.append("No GitHub token found in PAMPA_GH_TOKEN, GITHUB_TOKEN, or GH_TOKEN.")
    if not apk_candidates:
        notes.append("No release APK found yet. Build a release or run publish without skipBuild.")
    return notes


def resolve_token(required: bool) -> str | None:
    for env_name in ("PAMPA_GH_TOKEN", "GITHUB_TOKEN", "GH_TOKEN"):
        value = os.getenv(env_name, "").strip()
        if value:
            return value
    if required:
        raise SkillError("Missing GitHub token. Set PAMPA_GH_TOKEN, GITHUB_TOKEN, or GH_TOKEN.")
    return None


def resolve_repository(repo_owner: str, repo_name: str, repo_root: Path) -> dict[str, str]:
    if repo_owner.strip() and repo_name.strip():
        return {
            "repoOwner": repo_owner.strip(),
            "repoName": repo_name.strip(),
        }

    remote_url = discover_git_remote(repo_root)
    if not remote_url:
        return {"repoOwner": repo_owner.strip(), "repoName": repo_name.strip()}

    parsed = parse_github_remote(remote_url)
    return {
        "repoOwner": repo_owner.strip() or parsed.get("repoOwner", ""),
        "repoName": repo_name.strip() or parsed.get("repoName", ""),
    }


def discover_git_remote(repo_root: Path) -> str:
    try:
        process = run_command(
            ["git", "remote", "get-url", "origin"],
            cwd=repo_root,
            check=False,
        )
    except SkillError:
        return ""
    return process.stdout.strip()


def parse_github_remote(remote_url: str) -> dict[str, str]:
    patterns = (
        r"github\.com[:/](?P<owner>[^/]+)/(?P<repo>[^/]+?)(?:\.git)?$",
        r"ssh://git@github\.com/(?P<owner>[^/]+)/(?P<repo>[^/]+?)(?:\.git)?$",
    )
    for pattern in patterns:
        match = re.search(pattern, remote_url)
        if match:
            return {
                "repoOwner": match.group("owner").strip(),
                "repoName": match.group("repo").strip(),
            }
    return {"repoOwner": "", "repoName": ""}


def discover_project_name(repo_root: Path) -> str:
    settings_files = (
        repo_root / "settings.gradle.kts",
        repo_root / "settings.gradle",
    )
    for path in settings_files:
        if not path.exists():
            continue
        content = path.read_text(encoding="utf-8", errors="ignore")
        match = re.search(r'rootProject\.name\s*=\s*"([^"]+)"', content)
        if match:
            return match.group(1).strip()
    return repo_root.name


def discover_android_modules(repo_root: Path) -> list[dict[str, Any]]:
    modules: list[dict[str, Any]] = []
    for gradle_file in iter_gradle_files(repo_root):
        content = gradle_file.read_text(encoding="utf-8", errors="ignore")
        if not is_android_application_gradle(content):
            continue
        module_dir = gradle_file.parent
        relative_path = module_dir.relative_to(repo_root)
        module_name = "." if str(relative_path) == "." else relative_path.as_posix()
        task = "assembleRelease" if module_name == "." else ":" + ":".join(relative_path.parts) + ":assembleRelease"
        modules.append(
            {
                "module_name": module_name,
                "module_dir": module_dir,
                "gradle_file": gradle_file,
                "release_task": task,
                "version": extract_gradle_value(content, "versionName"),
                "application_id": extract_gradle_value(content, "applicationId"),
                "namespace": extract_gradle_value(content, "namespace"),
            }
        )
    return sorted(modules, key=module_sort_key)


def iter_gradle_files(repo_root: Path) -> list[Path]:
    results: list[Path] = []
    for pattern in ("build.gradle", "build.gradle.kts"):
        for path in repo_root.rglob(pattern):
            if should_skip_path(path):
                continue
            results.append(path)
    return results


def should_skip_path(path: Path) -> bool:
    skip_names = {".git", ".gradle", "build", ".idea", ".kotlin", "node_modules"}
    return any(part in skip_names for part in path.parts)


def is_android_application_gradle(content: str) -> bool:
    patterns = (
        r'id\s*\(\s*["\']com\.android\.application["\']\s*\)(?!\s*apply\s+false)',
        r'id\s+["\']com\.android\.application["\'](?!\s*apply\s+false)',
        r'alias\s*\(\s*libs\.plugins\.androidApplication\s*\)(?!\s*apply\s+false)',
        r'alias\s*\(\s*libs\.plugins\.android\.application\s*\)(?!\s*apply\s+false)',
    )
    return any(re.search(pattern, content) for pattern in patterns)


def extract_gradle_value(content: str, key: str) -> str:
    patterns = (
        rf"{key}\s*=\s*\"([^\"]+)\"",
        rf"{key}\s*=\s*'([^']+)'",
        rf"{key}\s+\"([^\"]+)\"",
        rf"{key}\s+'([^']+)'",
    )
    for pattern in patterns:
        match = re.search(pattern, content)
        if match:
            return match.group(1).strip()
    return ""


def module_sort_key(module: dict[str, Any]) -> tuple[int, int, str]:
    name = module["module_name"]
    preferred = {
        "app": 0,
        "androidApp": 1,
        "mobile": 2,
        ".": 3,
    }
    score = preferred.get(name.split("/")[-1], 10)
    return (score, len(Path(name).parts), name)


def choose_module(modules: list[dict[str, Any]], module_hint: str) -> dict[str, Any] | None:
    if not modules:
        return None
    hint = module_hint.strip()
    if not hint:
        return modules[0]

    hint_normalized = hint.replace("\\", "/").strip("/")
    for module in modules:
        module_name = module["module_name"].strip("./")
        if module_name == hint_normalized or module_name.split("/")[-1] == hint_normalized:
            return module
    raise SkillError(f"Android module '{module_hint}' not found.")


def serialize_module(module: dict[str, Any] | None) -> dict[str, Any] | None:
    if module is None:
        return None
    return {
        "moduleName": module["module_name"],
        "moduleDir": str(module["module_dir"]),
        "gradleFile": str(module["gradle_file"]),
        "releaseTask": module["release_task"],
        "version": module["version"],
        "applicationId": module["application_id"],
        "namespace": module["namespace"],
    }


def serialize_apk(path: Path) -> dict[str, Any]:
    stat = path.stat()
    return {
        "path": str(path),
        "sizeBytes": stat.st_size,
        "modifiedAt": int(stat.st_mtime),
    }


def has_gradle_wrapper(repo_root: Path) -> bool:
    return (repo_root / "gradlew").exists() or (repo_root / "gradlew.bat").exists()


def find_gradle_wrapper(repo_root: Path) -> Path:
    if os.name == "nt" and (repo_root / "gradlew.bat").exists():
        return repo_root / "gradlew.bat"
    if (repo_root / "gradlew").exists():
        return repo_root / "gradlew"
    if (repo_root / "gradlew.bat").exists():
        return repo_root / "gradlew.bat"
    raise SkillError(f"Gradle wrapper not found in {repo_root}")


def resolve_app_id(
    args: argparse.Namespace,
    repository: dict[str, str],
    app_manifest: dict[str, Any] | None,
) -> str:
    if args.app_id.strip():
        return slugify(args.app_id)
    existing_id = app_manifest_get(app_manifest, "id")
    if existing_id:
        return slugify(existing_id)
    base = repository["repoName"] or "android-app"
    return slugify(base)


def resolve_app_name(
    args: argparse.Namespace,
    repository: dict[str, str],
    repo_root: Path,
    app_manifest: dict[str, Any] | None,
    module: dict[str, Any] | None,
) -> str:
    if args.app_name.strip():
        return args.app_name.strip()
    existing = app_manifest_get(app_manifest, "name")
    if existing:
        return existing
    project_name = discover_project_name(repo_root)
    if project_name and project_name != repo_root.name:
        return project_name
    if repository["repoName"]:
        return title_from_slug(repository["repoName"])
    if module:
        return title_from_slug(module["module_name"].split("/")[-1])
    return title_from_slug(repo_root.name)


def resolve_package_name(
    args: argparse.Namespace,
    app_manifest: dict[str, Any] | None,
    module: dict[str, Any] | None,
) -> str:
    if args.package_name.strip():
        return args.package_name.strip()
    existing = app_manifest_get(app_manifest, "packageName")
    if existing:
        return existing
    if module:
        return module["application_id"] or module["namespace"] or ""
    return ""


def resolve_version(
    args: argparse.Namespace,
    app_manifest: dict[str, Any] | None,
    module: dict[str, Any] | None,
) -> str:
    if args.version.strip():
        return args.version.strip()
    if module and module["version"]:
        return module["version"]
    fallback = ""
    if app_manifest:
        fallback = (
            app_manifest_get(app_manifest, "beta", "version")
            or app_manifest_get(app_manifest, "stable", "version")
        )
    if fallback:
        return fallback
    raise SkillError("Unable to detect version. Pass version explicitly.")


def ensure_version_is_newer(
    app_manifest: dict[str, Any] | None,
    channel: str,
    version: str,
) -> None:
    current_version = app_manifest_get(app_manifest, channel, "version")
    if not current_version:
        return
    comparison = compare_release_versions(version, current_version)
    if comparison <= 0:
        raise SkillError(
            f"Version '{version}' is not newer than the current {channel} version '{current_version}'."
        )


def compare_release_versions(left: str, right: str) -> int:
    left_core, left_weight, left_label, left_prerelease_number = parse_version_components(left)
    right_core, right_weight, right_label, right_prerelease_number = parse_version_components(right)

    core_comparison = compare_core_parts(left_core, right_core)
    if core_comparison != 0:
        return core_comparison
    if left_weight != right_weight:
        return 1 if left_weight > right_weight else -1
    if left_label != right_label:
        return 1 if left_label > right_label else -1
    if left_prerelease_number != right_prerelease_number:
        return 1 if left_prerelease_number > right_prerelease_number else -1

    if left != right:
        return 1 if left > right else -1
    return 0


def parse_version_components(version: str) -> tuple[list[int], int, str, int]:
    normalized = version.strip().lower()
    match = re.match(
        r"^(?P<core>\d+(?:\.\d+)*)(?:(?:[-._]?)(?P<label>[a-z]+)(?:[-._]?(?P<number>\d+))?)?$",
        normalized,
    )
    if not match:
        return ([0], 50, normalized, 0)

    core_parts = [int(part) for part in match.group("core").split(".")]
    label = match.group("label") or ""
    number = int(match.group("number") or 0)
    prerelease_weight = {
        "snapshot": 0,
        "dev": 0,
        "alpha": 10,
        "a": 10,
        "beta": 20,
        "b": 20,
        "rc": 30,
        "pre": 30,
        "preview": 30,
        "": 100,
    }.get(label, 50)
    return (core_parts, prerelease_weight, label, number)


def compare_core_parts(left: list[int], right: list[int]) -> int:
    max_length = max(len(left), len(right))
    for index in range(max_length):
        left_value = left[index] if index < len(left) else 0
        right_value = right[index] if index < len(right) else 0
        if left_value != right_value:
            return 1 if left_value > right_value else -1
    return 0


def resolve_description(
    args: argparse.Namespace,
    app_manifest: dict[str, Any] | None,
    app_name: str,
) -> str:
    if args.description.strip():
        return args.description.strip()
    existing = app_manifest_get(app_manifest, "description")
    if existing:
        return existing
    return f"{app_name} Android app"


def resolve_field(explicit: str, existing: str, fallback: str) -> str:
    return explicit.strip() or existing or fallback


def resolve_changelog(args: argparse.Namespace, version: str) -> str:
    if args.changelog_file.strip():
        changelog_path = Path(args.changelog_file).resolve()
        if not changelog_path.exists():
            raise SkillError(f"Changelog file not found: {changelog_path}")
        text = changelog_path.read_text(encoding="utf-8", errors="ignore").strip()
        if text:
            return text
    if args.changelog.strip():
        return args.changelog.strip()
    return f"Release {version}"


def resolve_apk_path(
    args: argparse.Namespace,
    repo_root: Path,
    module: dict[str, Any] | None,
) -> Path:
    if args.apk_path.strip():
        path = Path(args.apk_path).expanduser().resolve()
        if not path.exists():
            raise SkillError(f"APK not found: {path}")
        return path

    if not args.skip_build:
        build_release(repo_root, module, args.task)

    candidates = find_release_apks(repo_root, module["module_dir"] if module else None)
    if not candidates:
        raise SkillError("No release APK found after build. Pass apkPath if the output lives elsewhere.")
    return candidates[0]


def build_release(repo_root: Path, module: dict[str, Any] | None, explicit_task: str) -> None:
    wrapper = find_gradle_wrapper(repo_root)
    task = explicit_task.strip() or (module["release_task"] if module else "assembleRelease")
    command = [str(wrapper), task, "--no-daemon"]
    if wrapper.suffix == ".bat":
        command = ["cmd.exe", "/c", str(wrapper), task, "--no-daemon"]
    process = run_command(command, cwd=repo_root, check=False)
    if process.returncode != 0:
        output = (process.stdout + "\n" + process.stderr).strip()
        raise SkillError(
            f"Gradle release build failed for task '{task}'.\n{truncate_text(output, limit=1200)}"
        )


def find_release_apks(repo_root: Path, module_dir: Path | None) -> list[Path]:
    candidates: list[Path] = []
    search_roots: list[Path] = []
    if module_dir is not None:
        search_roots.append(module_dir / "build" / "outputs" / "apk" / "release")
    search_roots.append(repo_root)

    seen: set[Path] = set()
    for root in search_roots:
        if not root.exists():
            continue
        for path in root.rglob("*.apk"):
            if path in seen:
                continue
            seen.add(path)
            if should_skip_path(path):
                continue
            if is_unwanted_apk(path):
                continue
            candidates.append(path)
    candidates.sort(key=lambda path: path.stat().st_mtime, reverse=True)
    return candidates


def is_unwanted_apk(path: Path) -> bool:
    lowered = path.as_posix().lower()
    bad_markers = ("unsigned", "unaligned", "androidtest", "test/")
    if any(marker in lowered for marker in bad_markers):
        return True
    return False


def inspect_apk(apk_path: Path) -> dict[str, Any]:
    # Validate APK file integrity and signature
    validate_apk_is_valid_zip(apk_path)
    validate_apk_is_signed(apk_path)

    return {
        "sizeBytes": apk_path.stat().st_size,
        "sha256": sha256_file(apk_path),
    }


def validate_apk_is_valid_zip(apk_path: Path) -> None:
    """Validate that the APK file is a valid ZIP archive."""
    import zipfile
    try:
        with zipfile.ZipFile(apk_path, 'r') as zip_ref:
            bad_file = zip_ref.testzip()
            if bad_file is not None:
                raise SkillError(f"APK is corrupted: bad file in archive: {bad_file}")
    except zipfile.BadZipFile as e:
        raise SkillError(f"APK file is not a valid ZIP archive: {e}")
    except Exception as e:
        raise SkillError(f"Failed to validate APK file: {e}")


def validate_apk_is_signed(apk_path: Path) -> None:
    """Validate that the APK is properly signed using apksigner or by checking for META-INF/MANIFEST.MF."""
    import zipfile

    try:
        with zipfile.ZipFile(apk_path, 'r') as zip_ref:
            # Check for signature files in META-INF directory
            has_manifest = 'META-INF/MANIFEST.MF' in zip_ref.namelist()
            has_cert = False

            # Look for certificate files (RSA, EC, DSA)
            for name in zip_ref.namelist():
                if name.startswith('META-INF/') and name.upper().endswith(('.RSA', '.EC', '.DSA')):
                    has_cert = True
                    break

            if not has_manifest:
                raise SkillError(
                    "APK is not signed: missing META-INF/MANIFEST.MF. "
                    "Build a signed release APK using 'assembleRelease' or 'bundleRelease'."
                )

            if not has_cert:
                raise SkillError(
                    "APK is not signed: missing signature certificate in META-INF. "
                    "The APK must be signed with a release keystore. "
                    "Ensure your keystore is configured in build.gradle."
                )
    except SkillError:
        raise
    except zipfile.BadZipFile:
        raise SkillError("APK file is not a valid ZIP archive (cannot check signature)")
    except Exception as e:
        raise SkillError(f"Failed to validate APK signature: {e}")


def build_version_info(
    version: str,
    changelog: str,
    release_tag: str,
    asset_name: str,
    size_bytes: int,
) -> dict[str, Any]:
    return {
        "version": version,
        "releaseDate": today_string(),
        "changelog": changelog,
        "releaseTag": release_tag,
        "apkAsset": asset_name,
        "exeAsset": "",
        "sizeBytes": size_bytes,
    }


def upsert_app_manifest(
    existing_manifest: dict[str, Any] | None,
    repo_owner: str,
    repo_name: str,
    manifest_path: str,
    app_id: str,
    app_name: str,
    description: str,
    package_name: str,
    developer: str,
    category: str,
    channel: str,
    version_info: dict[str, Any],
) -> dict[str, Any]:
    manifest = deep_copy(existing_manifest) if existing_manifest else {"app": {}}
    app = manifest.setdefault("app", {})

    app["id"] = app_id
    app["name"] = app_name
    app["description"] = description
    app["packageName"] = package_name
    app["iconUrl"] = app.get("iconUrl", "")
    app["category"] = category
    app["developer"] = developer
    app["screenshots"] = list(app.get("screenshots") or [])
    app["repository"] = {
        "repoOwner": repo_owner,
        "repoName": repo_name,
        "manifestPath": manifest_path,
        "ref": "",
    }

    previous_version = deep_copy(app.get("stable" if channel == "stable" else "beta"))
    history = list(app.get("history") or [])
    if previous_version and not history_contains(history, previous_version):
        history = [previous_version] + history

    if channel == "stable":
        app["stable"] = version_info
        app["beta"] = deep_copy(version_info)
    else:
        app["beta"] = version_info
        if "stable" in app and app["stable"] is None:
            app.pop("stable")

    app["history"] = history
    return manifest


def upsert_store_manifest(
    existing_manifest: dict[str, Any] | None,
    store_owner: str,
    store_repo: str,
    app_id: str,
    repo_owner: str,
    repo_name: str,
    manifest_path: str,
) -> dict[str, Any]:
    store_manifest = normalize_store_manifest(existing_manifest, store_owner, store_repo)
    apps = list(store_manifest.get("apps") or [])
    new_entry = {
        "id": app_id,
        "repoOwner": repo_owner,
        "repoName": repo_name,
        "manifestPath": manifest_path,
        "ref": "",
    }
    existing_index = next((index for index, item in enumerate(apps) if item.get("id", "").strip() == app_id), -1)
    if existing_index >= 0:
        apps[existing_index] = new_entry
    elif app_id != "pampa-store":
        apps.append(new_entry)
    store_manifest["apps"] = sorted(apps, key=lambda item: item.get("id", "").lower())
    store_manifest["storeReleases"] = deep_copy(store_manifest.get("storeReleases") or {})
    return store_manifest


def normalize_store_manifest(
    manifest: dict[str, Any] | None,
    store_owner: str,
    store_repo: str,
) -> dict[str, Any]:
    if not manifest:
        return {"apps": [], "storeReleases": {}}

    manifest_copy = deep_copy(manifest)
    if "storeReleases" in manifest_copy:
        normalized_apps: list[dict[str, Any]] = []
        for raw_entry in manifest_copy.get("apps") or []:
            if "repoOwner" in raw_entry and "repoName" in raw_entry:
                normalized_apps.append(
                    {
                        "id": str(raw_entry.get("id", "")).strip(),
                        "repoOwner": str(raw_entry.get("repoOwner", "")).strip(),
                        "repoName": str(raw_entry.get("repoName", "")).strip(),
                        "manifestPath": str(raw_entry.get("manifestPath", DEFAULT_MANIFEST_PATH)).strip() or DEFAULT_MANIFEST_PATH,
                        "ref": str(raw_entry.get("ref", "")).strip(),
                    }
                )
                continue
            repository = raw_entry.get("repository") or {}
            normalized_apps.append(
                {
                    "id": str(raw_entry.get("id", "")).strip(),
                    "repoOwner": str(repository.get("repoOwner", store_owner)).strip() or store_owner,
                    "repoName": str(repository.get("repoName", store_repo)).strip() or store_repo,
                    "manifestPath": str(repository.get("manifestPath", DEFAULT_MANIFEST_PATH)).strip() or DEFAULT_MANIFEST_PATH,
                    "ref": str(repository.get("ref", "")).strip(),
                }
            )
        manifest_copy["apps"] = [entry for entry in normalized_apps if entry["id"] and entry["id"] != "pampa-store"]
        manifest_copy["storeReleases"] = deep_copy(manifest_copy.get("storeReleases") or {})
        return manifest_copy

    legacy_apps = []
    for raw_entry in manifest_copy.get("apps") or []:
        app_id = str(raw_entry.get("id", "")).strip()
        if not app_id or app_id == "pampa-store":
            continue
        repository = raw_entry.get("repository") or {}
        legacy_apps.append(
            {
                "id": app_id,
                "repoOwner": str(repository.get("repoOwner", store_owner)).strip() or store_owner,
                "repoName": str(repository.get("repoName", store_repo)).strip() or store_repo,
                "manifestPath": str(repository.get("manifestPath", DEFAULT_MANIFEST_PATH)).strip() or DEFAULT_MANIFEST_PATH,
                "ref": str(repository.get("ref", "")).strip(),
            }
        )
    return {
        "apps": legacy_apps,
        "storeReleases": {},
    }


def find_store_entry(store_manifest: dict[str, Any], app_id: str) -> dict[str, Any] | None:
    return next((entry for entry in store_manifest.get("apps") or [] if entry.get("id") == app_id), None)


def history_contains(history: list[dict[str, Any]], version_info: dict[str, Any]) -> bool:
    version = version_info.get("version", "")
    release_tag = version_info.get("releaseTag", "")
    for item in history:
        if item.get("releaseTag", "") == release_tag and release_tag:
            return True
        if item.get("version", "") == version and item.get("releaseDate", "") == version_info.get("releaseDate", ""):
            return True
    return False


def load_app_manifest(
    owner: str,
    repo: str,
    manifest_path: str,
    token: str | None,
    allow_missing: bool,
) -> tuple[dict[str, Any] | None, str | None]:
    remote_file = get_remote_file(owner, repo, manifest_path, token, allow_missing=allow_missing)
    if remote_file is None:
        return None, None
    try:
        parsed = json.loads(remote_file["content"])
    except json.JSONDecodeError as error:
        raise SkillError(f"Invalid app manifest JSON in {owner}/{repo}:{manifest_path}: {error}") from error
    if "app" not in parsed:
        parsed = {"app": parsed}
    return parsed, remote_file["sha"]


def load_store_manifest(
    owner: str,
    repo: str,
    manifest_path: str,
    token: str | None,
    allow_missing: bool,
) -> tuple[dict[str, Any] | None, str | None]:
    remote_file = get_remote_file(owner, repo, manifest_path, token, allow_missing=allow_missing)
    if remote_file is None:
        return None, None
    try:
        parsed = json.loads(remote_file["content"])
    except json.JSONDecodeError as error:
        raise SkillError(f"Invalid store manifest JSON in {owner}/{repo}:{manifest_path}: {error}") from error
    return parsed, remote_file["sha"]


def get_remote_file(
    owner: str,
    repo: str,
    manifest_path: str,
    token: str | None,
    allow_missing: bool,
    ref: str = "",
) -> dict[str, str] | None:
    url = f"https://api.github.com/repos/{owner}/{repo}/contents/{urllib.parse.quote(manifest_path, safe='/')}"
    if ref.strip():
        url = f"{url}?ref={urllib.parse.quote(ref.strip())}"
    try:
        response = github_request("GET", url, token=token)
    except GitHubHttpError as error:
        if error.status == 404 and allow_missing:
            return None
        raise
    encoded_content = response.get("content", "")
    if not encoded_content:
        raise SkillError(f"GitHub returned empty content for {owner}/{repo}:{manifest_path}.")
    try:
        decoded = base64.b64decode(encoded_content, validate=False).decode("utf-8")
    except Exception as error:  # noqa: BLE001
        raise SkillError(f"Unable to decode GitHub file {owner}/{repo}:{manifest_path}: {error}") from error
    return {
        "sha": str(response.get("sha", "")),
        "content": decoded,
    }


def version_exists(owner: str, repo: str, tag: str, token: str | None) -> bool:
    if not token:
        return False
    url = f"https://api.github.com/repos/{owner}/{repo}/releases/tags/{urllib.parse.quote(tag)}"
    try:
        github_request("GET", url, token=token)
        return True
    except GitHubHttpError as error:
        if error.status == 404:
            return False
        raise


def create_release(
    owner: str,
    repo: str,
    token: str,
    tag: str,
    name: str,
    body: str,
    prerelease: bool,
) -> dict[str, Any]:
    url = f"https://api.github.com/repos/{owner}/{repo}/releases"
    payload = {
        "tag_name": tag,
        "name": name,
        "body": body,
        "prerelease": prerelease,
    }
    return github_request("POST", url, token=token, payload=payload)


def upload_asset(
    upload_url: str,
    token: str,
    asset_name: str,
    content_type: str,
    payload: bytes,
) -> dict[str, Any]:
    clean_url = upload_url.replace("{?name,label}", "")
    separator = "&" if "?" in clean_url else "?"
    target_url = f"{clean_url}{separator}name={urllib.parse.quote(asset_name)}"
    headers = {"Content-Type": content_type}
    return github_request("POST", target_url, token=token, payload=payload, headers=headers, expect_json=True)


def upsert_remote_file(
    owner: str,
    repo: str,
    manifest_path: str,
    token: str,
    content: dict[str, Any],
    existing_sha: str | None,
    create_message: str,
    update_message: str,
) -> None:
    url = f"https://api.github.com/repos/{owner}/{repo}/contents/{urllib.parse.quote(manifest_path, safe='/')}"
    payload: dict[str, Any] = {
        "message": update_message if existing_sha else create_message,
        "content": base64.b64encode(
            json.dumps(content, indent=2, ensure_ascii=False).encode("utf-8")
        ).decode("ascii"),
    }
    if existing_sha:
        payload["sha"] = existing_sha
    github_request("PUT", url, token=token, payload=payload)


def github_request(
    method: str,
    url: str,
    token: str | None,
    payload: Any = None,
    headers: dict[str, str] | None = None,
    expect_json: bool = True,
) -> Any:
    request_headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": USER_AGENT,
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    if headers:
        request_headers.update(headers)

    request_payload: bytes | None
    if payload is None:
        request_payload = None
    elif isinstance(payload, (dict, list)):
        request_payload = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request_headers.setdefault("Content-Type", "application/json")
    elif isinstance(payload, str):
        request_payload = payload.encode("utf-8")
    else:
        request_payload = payload

    request = urllib.request.Request(url, data=request_payload, method=method, headers=request_headers)
    try:
        with urllib.request.urlopen(request) as response:
            body = response.read()
    except urllib.error.HTTPError as error:
        preview = truncate_text(error.read().decode("utf-8", errors="replace"), limit=400)
        raise GitHubHttpError(error.code, url, preview) from None
    except urllib.error.URLError as error:
        raise SkillError(f"Network error while calling GitHub: {error.reason}") from error

    if not body:
        return {} if expect_json else b""
    if expect_json:
        try:
            return json.loads(body.decode("utf-8"))
        except json.JSONDecodeError as error:
            raise SkillError(f"GitHub returned invalid JSON for {url}: {error}") from error
    return body


def app_manifest_get(manifest: dict[str, Any] | None, *path: str) -> str:
    if not manifest:
        return ""
    value: Any = manifest.get("app", {})
    for key in path:
        if not isinstance(value, dict):
            return ""
        value = value.get(key)
    if isinstance(value, str):
        return value.strip()
    return ""


def run_command(
    command: list[str],
    cwd: Path,
    check: bool,
) -> subprocess.CompletedProcess[str]:
    try:
        process = subprocess.run(
            command,
            cwd=str(cwd),
            text=True,
            capture_output=True,
            check=False,
        )
    except OSError as error:
        raise SkillError(f"Unable to run command {' '.join(command)}: {error}") from error

    if check and process.returncode != 0:
        output = (process.stdout + "\n" + process.stderr).strip()
        raise SkillError(
            f"Command failed with exit code {process.returncode}: {' '.join(command)}\n{truncate_text(output, 1200)}"
        )
    return process


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def slugify(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", value.strip().lower())
    normalized = re.sub(r"-{2,}", "-", normalized).strip("-")
    return normalized or "android-app"


def title_from_slug(value: str) -> str:
    normalized = value.replace("_", " ").replace("-", " ").strip()
    normalized = re.sub(r"\s+", " ", normalized)
    return normalized.title() or "Android App"


def today_string() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%d")


def truncate_text(value: str, limit: int) -> str:
    text = value.strip()
    if len(text) <= limit:
        return text
    return text[: limit - 3].rstrip() + "..."


def deep_copy(value: Any) -> Any:
    return json.loads(json.dumps(value))


if __name__ == "__main__":
    raise SystemExit(main())
