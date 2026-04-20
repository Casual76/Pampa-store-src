#!/usr/bin/env python3
"""Local stdio MCP server for Pampa Store tooling."""

from __future__ import annotations

import json
import sys
import traceback
from typing import Any, Callable

import publisher
import store_tools

SERVER_INFO = {
    "name": "pampa-store-publisher",
    "version": "0.2.0",
}


def build_tool(
    name: str,
    title: str,
    description: str,
    input_schema: dict[str, Any],
    max_result_chars: int | None = None,
) -> dict[str, Any]:
    tool = {
        "name": name,
        "title": title,
        "description": description,
        "inputSchema": input_schema,
    }
    if max_result_chars is not None:
        tool["_meta"] = {"anthropic/maxResultSizeChars": max_result_chars}
    return tool


COMMON_STORE_TARGET_PROPS: dict[str, Any] = {
    "storeOwner": {
        "type": "string",
        "description": "Optional override for the central Pampa Store owner.",
    },
    "storeRepo": {
        "type": "string",
        "description": "Optional override for the central Pampa Store repository.",
    },
    "storeManifestPath": {
        "type": "string",
        "description": "Optional override for the store manifest path. Defaults to manifest.json.",
    },
}


COMMON_APP_TARGET_PROPS: dict[str, Any] = {
    "appId": {
        "type": "string",
        "description": "App identifier inside the Pampa Store catalog.",
    },
    "repoOwner": {
        "type": "string",
        "description": "Optional GitHub owner override.",
    },
    "repoName": {
        "type": "string",
        "description": "Optional GitHub repository name override.",
    },
    "manifestPath": {
        "type": "string",
        "description": "Optional manifest path inside the target repository.",
    },
    "ref": {
        "type": "string",
        "description": "Optional branch, tag, or ref to read from.",
    },
}


TOOLS = [
    build_tool(
        "get_pampa_store_context",
        "Get Pampa Store Context",
        "Show the local plugin paths, store defaults, token presence, and a local Android release probe.",
        {
            "type": "object",
            "properties": {
                "repoRoot": {
                    "type": "string",
                    "description": "Optional repository root override for local probing.",
                },
                **COMMON_STORE_TARGET_PROPS,
            },
            "additionalProperties": False,
        },
    ),
    build_tool(
        "get_pampa_store_catalog",
        "Get Pampa Store Catalog",
        "Fetch and return the normalized central Pampa Store manifest.",
        {
            "type": "object",
            "properties": COMMON_STORE_TARGET_PROPS,
            "additionalProperties": False,
        },
        max_result_chars=300000,
    ),
    build_tool(
        "list_pampa_store_apps",
        "List Pampa Store Apps",
        "List apps from the Pampa Store catalog with optional query, category, developer, pagination, and hydrated version info.",
        {
            "type": "object",
            "properties": {
                **COMMON_STORE_TARGET_PROPS,
                "query": {
                    "type": "string",
                    "description": "Optional free-text search query across app id, name, description, category, developer, and repo fields.",
                },
                "category": {
                    "type": "string",
                    "description": "Optional category filter.",
                },
                "developer": {
                    "type": "string",
                    "description": "Optional developer filter.",
                },
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of apps to return. Defaults to 50.",
                },
                "offset": {
                    "type": "integer",
                    "description": "Number of matched apps to skip before returning results.",
                },
                "includeVersions": {
                    "type": "boolean",
                    "description": "Hydrate each app manifest to include stable and beta version summaries. Defaults to true.",
                },
                "includeManifest": {
                    "type": "boolean",
                    "description": "Include the full app manifest for each returned app.",
                },
            },
            "additionalProperties": False,
        },
        max_result_chars=250000,
    ),
    build_tool(
        "get_pampa_store_app",
        "Get Pampa Store App",
        "Resolve a single app from the store catalog and return its catalog entry, full manifest, and current version summary.",
        {
            "type": "object",
            "properties": {
                **COMMON_STORE_TARGET_PROPS,
                "appId": COMMON_APP_TARGET_PROPS["appId"],
            },
            "required": ["appId"],
            "additionalProperties": False,
        },
        max_result_chars=200000,
    ),
    build_tool(
        "get_pampa_store_current_version",
        "Get Current Pampa Store Version",
        "Return the current stable and beta versions for a single app, or a single channel if requested.",
        {
            "type": "object",
            "properties": {
                **COMMON_STORE_TARGET_PROPS,
                "appId": COMMON_APP_TARGET_PROPS["appId"],
                "channel": {
                    "type": "string",
                    "enum": ["stable", "beta"],
                    "description": "Optional channel filter.",
                },
            },
            "required": ["appId"],
            "additionalProperties": False,
        },
    ),
    build_tool(
        "list_pampa_store_categories",
        "List Pampa Store Categories",
        "List categories currently used by apps in the Pampa Store and count how many apps use each category.",
        {
            "type": "object",
            "properties": COMMON_STORE_TARGET_PROPS,
            "additionalProperties": False,
        },
    ),
    build_tool(
        "list_pampa_store_developers",
        "List Pampa Store Developers",
        "List developers currently present in the Pampa Store and count how many apps belong to each developer.",
        {
            "type": "object",
            "properties": COMMON_STORE_TARGET_PROPS,
            "additionalProperties": False,
        },
    ),
    build_tool(
        "list_pampa_store_releases",
        "List Pampa Store Releases",
        "List GitHub releases for an app repository resolved by appId or by explicit repo owner and name.",
        {
            "type": "object",
            "properties": {
                **COMMON_STORE_TARGET_PROPS,
                **COMMON_APP_TARGET_PROPS,
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of releases to return. Defaults to 20.",
                },
                "page": {
                    "type": "integer",
                    "description": "GitHub releases page number. Defaults to 1.",
                },
            },
            "additionalProperties": False,
        },
        max_result_chars=150000,
    ),
    build_tool(
        "get_pampa_store_release",
        "Get Pampa Store Release",
        "Return a specific GitHub release by tag, or resolve the release tag automatically from the app's stable or beta channel.",
        {
            "type": "object",
            "properties": {
                **COMMON_STORE_TARGET_PROPS,
                **COMMON_APP_TARGET_PROPS,
                "tag": {
                    "type": "string",
                    "description": "Release tag to resolve.",
                },
                "channel": {
                    "type": "string",
                    "enum": ["stable", "beta"],
                    "description": "Optional channel used to resolve the release tag from the app manifest.",
                },
            },
            "additionalProperties": False,
        },
        max_result_chars=180000,
    ),
    build_tool(
        "get_pampa_store_repo_file",
        "Get Pampa Store Repo File",
        "Read a text or JSON file from a GitHub repository related to the store or to a specific app.",
        {
            "type": "object",
            "properties": {
                **COMMON_STORE_TARGET_PROPS,
                **COMMON_APP_TARGET_PROPS,
                "path": {
                    "type": "string",
                    "description": "Path to the repository file. Defaults to the manifest path when available.",
                },
                "parseJson": {
                    "type": "boolean",
                    "description": "Force JSON parsing or disable it explicitly.",
                },
            },
            "additionalProperties": False,
        },
        max_result_chars=300000,
    ),
    build_tool(
        "put_pampa_store_repo_file",
        "Put Pampa Store Repo File",
        "Create or update a text or JSON file inside a target GitHub repository. Requires a GitHub token.",
        {
            "type": "object",
            "properties": {
                **COMMON_STORE_TARGET_PROPS,
                **COMMON_APP_TARGET_PROPS,
                "path": {
                    "type": "string",
                    "description": "Path to the repository file. Defaults to the manifest path when available.",
                },
                "textContent": {
                    "type": "string",
                    "description": "Plain text file content to write.",
                },
                "content": {
                    "type": "string",
                    "description": "Alias for textContent.",
                },
                "jsonContent": {
                    "description": "JSON object or array to serialize and write.",
                },
                "commitMessage": {
                    "type": "string",
                    "description": "Git commit message for the file update.",
                },
                "sha": {
                    "type": "string",
                    "description": "Optional existing blob sha. If omitted, the server looks it up automatically.",
                },
                "branch": {
                    "type": "string",
                    "description": "Optional target branch for the write operation.",
                },
            },
            "required": ["commitMessage"],
            "additionalProperties": False,
        },
    ),
    build_tool(
        "upsert_pampa_store_index_entry",
        "Upsert Pampa Store Index Entry",
        "Add or update an app entry inside the central Pampa Store manifest. Requires a GitHub token.",
        {
            "type": "object",
            "properties": {
                **COMMON_STORE_TARGET_PROPS,
                "appId": COMMON_APP_TARGET_PROPS["appId"],
                "repoOwner": COMMON_APP_TARGET_PROPS["repoOwner"],
                "repoName": COMMON_APP_TARGET_PROPS["repoName"],
                "manifestPath": COMMON_APP_TARGET_PROPS["manifestPath"],
                "commitMessage": {
                    "type": "string",
                    "description": "Optional custom commit message.",
                },
                "branch": {
                    "type": "string",
                    "description": "Optional target branch for the write operation.",
                },
            },
            "required": ["appId", "repoOwner", "repoName"],
            "additionalProperties": False,
        },
    ),
    build_tool(
        "remove_pampa_store_index_entry",
        "Remove Pampa Store Index Entry",
        "Remove an app entry from the central Pampa Store manifest. Requires a GitHub token.",
        {
            "type": "object",
            "properties": {
                **COMMON_STORE_TARGET_PROPS,
                "appId": COMMON_APP_TARGET_PROPS["appId"],
                "commitMessage": {
                    "type": "string",
                    "description": "Optional custom commit message.",
                },
                "branch": {
                    "type": "string",
                    "description": "Optional target branch for the write operation.",
                },
            },
            "required": ["appId"],
            "additionalProperties": False,
        },
    ),
    build_tool(
        "validate_pampa_store_manifest",
        "Validate Pampa Store Manifest",
        "Validate a store manifest or app manifest provided inline or resolved from a remote repository.",
        {
            "type": "object",
            "properties": {
                **COMMON_STORE_TARGET_PROPS,
                **COMMON_APP_TARGET_PROPS,
                "manifestType": {
                    "type": "string",
                    "enum": ["store", "app"],
                    "description": "Optional explicit manifest type. If omitted, it is inferred.",
                },
                "path": {
                    "type": "string",
                    "description": "Optional repository path to validate if reading remotely.",
                },
                "content": {
                    "type": "string",
                    "description": "Inline JSON string to validate.",
                },
                "jsonContent": {
                    "description": "Inline JSON object or array to validate.",
                },
            },
            "additionalProperties": False,
        },
        max_result_chars=150000,
    ),
    build_tool(
        "probe_pampa_store_android_release",
        "Probe Pampa Store Android Release",
        "Inspect the Android repository, detect module metadata, and list candidate release APKs.",
        {
            "type": "object",
            "properties": {
                "repoRoot": {
                    "type": "string",
                    "description": "Absolute or relative path to the Android app repository. Optional for this repo-local installation.",
                },
                "module": {
                    "type": "string",
                    "description": "Optional Android module name or path, for example androidApp.",
                },
                "repoOwner": {
                    "type": "string",
                    "description": "Optional GitHub owner override when origin cannot be inferred.",
                },
                "repoName": {
                    "type": "string",
                    "description": "Optional GitHub repository name override when origin cannot be inferred.",
                },
            },
            "additionalProperties": False,
        },
    ),
    build_tool(
        "publish_pampa_store_android_release",
        "Publish Pampa Store Android Release",
        "Build or reuse a signed release APK, publish the GitHub release, and sync the app/store manifests for Pampa Store.",
        {
            "type": "object",
            "properties": {
                "repoRoot": {
                    "type": "string",
                    "description": "Absolute or relative path to the Android app repository. Optional for this repo-local installation.",
                },
                "module": {
                    "type": "string",
                    "description": "Optional Android module name or path, for example androidApp.",
                },
                "channel": {
                    "type": "string",
                    "enum": ["stable", "beta"],
                    "description": "Release channel to publish.",
                },
                "repoOwner": {
                    "type": "string",
                    "description": "Optional GitHub owner override when origin cannot be inferred.",
                },
                "repoName": {
                    "type": "string",
                    "description": "Optional GitHub repository name override when origin cannot be inferred.",
                },
                "version": {
                    "type": "string",
                    "description": "Optional explicit app version.",
                },
                "appId": {
                    "type": "string",
                    "description": "Optional stable slug used in tags and asset names.",
                },
                "appName": {
                    "type": "string",
                    "description": "Optional human-facing app name for the release title.",
                },
                "packageName": {
                    "type": "string",
                    "description": "Optional Android package name override.",
                },
                "description": {
                    "type": "string",
                    "description": "Optional app description for manifest bootstrap.",
                },
                "developer": {
                    "type": "string",
                    "description": "Optional developer label for the app manifest.",
                },
                "category": {
                    "type": "string",
                    "description": "Optional category label for the app manifest.",
                },
                "changelog": {
                    "type": "string",
                    "description": "Optional changelog text.",
                },
                "changelogFile": {
                    "type": "string",
                    "description": "Optional path to a changelog text file.",
                },
                "manifestPath": {
                    "type": "string",
                    "description": "Optional manifest path in the app repository. Defaults to manifest.json.",
                },
                "storeOwner": {
                    "type": "string",
                    "description": "Optional override for the central Pampa Store owner.",
                },
                "storeRepo": {
                    "type": "string",
                    "description": "Optional override for the central Pampa Store repository.",
                },
                "storeManifestPath": {
                    "type": "string",
                    "description": "Optional manifest path in the central store repository. Defaults to manifest.json.",
                },
                "apkPath": {
                    "type": "string",
                    "description": "Optional path to an existing APK to upload instead of building one.",
                },
                "task": {
                    "type": "string",
                    "description": "Optional explicit Gradle task to run instead of the detected release task.",
                },
                "skipBuild": {
                    "type": "boolean",
                    "description": "Skip the Gradle build step and reuse an existing release APK.",
                },
                "dryRun": {
                    "type": "boolean",
                    "description": "Compute the release plan without creating the GitHub release or updating manifests.",
                },
            },
            "required": ["channel"],
            "additionalProperties": False,
        },
        max_result_chars=200000,
    ),
]


HANDLERS: dict[str, Callable[[dict[str, Any] | None], dict[str, Any]]] = {
    "get_pampa_store_context": store_tools.get_pampa_store_context,
    "get_pampa_store_catalog": store_tools.get_pampa_store_catalog,
    "list_pampa_store_apps": store_tools.list_pampa_store_apps,
    "get_pampa_store_app": store_tools.get_pampa_store_app,
    "get_pampa_store_current_version": store_tools.get_pampa_store_current_version,
    "list_pampa_store_categories": store_tools.list_pampa_store_categories,
    "list_pampa_store_developers": store_tools.list_pampa_store_developers,
    "list_pampa_store_releases": store_tools.list_pampa_store_releases,
    "get_pampa_store_release": store_tools.get_pampa_store_release,
    "get_pampa_store_repo_file": store_tools.get_pampa_store_repo_file,
    "put_pampa_store_repo_file": store_tools.put_pampa_store_repo_file,
    "upsert_pampa_store_index_entry": store_tools.upsert_pampa_store_index_entry,
    "remove_pampa_store_index_entry": store_tools.remove_pampa_store_index_entry,
    "validate_pampa_store_manifest": store_tools.validate_pampa_store_manifest,
    "probe_pampa_store_android_release": publisher.probe_from_params,
    "publish_pampa_store_release": publisher.publish_from_params,
}


def main() -> int:
    while True:
        message = read_message()
        if message is None:
            return 0

        if not isinstance(message, dict):
            continue

        try:
            response = handle_message(message)
        except Exception as error:  # noqa: BLE001
            log_error("Unhandled server error", error)
            response = None
            if "id" in message:
                response = error_response(message["id"], -32603, f"Internal server error: {error}")

        if response is not None:
            write_message(response)


def handle_message(message: dict[str, Any]) -> dict[str, Any] | None:
    method = message.get("method")
    message_id = message.get("id")
    params = message.get("params")

    if method == "initialize":
        protocol_version = "2025-06-18"
        if isinstance(params, dict):
            requested_version = params.get("protocolVersion")
            if isinstance(requested_version, str) and requested_version.strip():
                protocol_version = requested_version.strip()
        return success_response(
            message_id,
            {
                "protocolVersion": protocol_version,
                "capabilities": {
                    "tools": {
                        "listChanged": False,
                    }
                },
                "serverInfo": SERVER_INFO,
            },
        )

    if method == "notifications/initialized":
        return None

    if method == "ping":
        return success_response(message_id, {})

    if method == "tools/list":
        return success_response(message_id, {"tools": TOOLS})

    if method == "tools/call":
        if not isinstance(params, dict):
            return error_response(message_id, -32602, "Tool call params must be an object.")
        tool_name = params.get("name")
        arguments = params.get("arguments") or {}
        if not isinstance(tool_name, str) or not tool_name.strip():
            return error_response(message_id, -32602, "Tool call is missing a valid name.")
        if not isinstance(arguments, dict):
            return error_response(message_id, -32602, "Tool arguments must be an object.")
        return handle_tool_call(message_id, tool_name.strip(), arguments)

    if message_id is not None:
        return error_response(message_id, -32601, f"Method not found: {method}")
    return None


def handle_tool_call(message_id: Any, tool_name: str, arguments: dict[str, Any]) -> dict[str, Any]:
    handler = HANDLERS.get(tool_name)
    if handler is None:
        return error_response(message_id, -32602, f"Unknown tool: {tool_name}")

    try:
        result = handler(arguments)
    except publisher.SkillError as error:
        return success_response(
            message_id,
            {
                "content": [{"type": "text", "text": str(error)}],
                "isError": True,
            },
        )
    except Exception as error:  # noqa: BLE001
        log_error(f"Tool execution failed for {tool_name}", error)
        return success_response(
            message_id,
            {
                "content": [{"type": "text", "text": f"Internal server error while running {tool_name}: {error}"}],
                "isError": True,
            },
        )

    text = json.dumps(result, indent=2, ensure_ascii=False)
    return success_response(
        message_id,
        {
            "content": [{"type": "text", "text": text}],
            "structuredContent": result,
            "isError": False,
        },
    )


def read_message() -> dict[str, Any] | None:
    headers: dict[str, str] = {}
    while True:
        raw_line = sys.stdin.buffer.readline()
        if raw_line == b"":
            if headers:
                raise EOFError("Unexpected EOF while reading MCP headers.")
            return None

        line = raw_line.decode("utf-8")
        if line in ("\r\n", "\n", ""):
            break

        name, separator, value = line.partition(":")
        if separator != ":":
            raise ValueError(f"Invalid MCP header line: {line!r}")
        headers[name.strip().lower()] = value.strip()

    content_length = headers.get("content-length")
    if not content_length:
        raise ValueError("Missing Content-Length header.")
    length = int(content_length)
    body = sys.stdin.buffer.read(length)
    if len(body) != length:
        raise EOFError("Unexpected EOF while reading MCP body.")
    return json.loads(body.decode("utf-8"))


def write_message(payload: dict[str, Any]) -> None:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    header = f"Content-Length: {len(body)}\r\n\r\n".encode("ascii")
    sys.stdout.buffer.write(header)
    sys.stdout.buffer.write(body)
    sys.stdout.buffer.flush()


def success_response(message_id: Any, result: dict[str, Any]) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": message_id, "result": result}


def error_response(message_id: Any, code: int, message: str) -> dict[str, Any]:
    return {
        "jsonrpc": "2.0",
        "id": message_id,
        "error": {"code": code, "message": message},
    }


def log_error(context: str, error: Exception) -> None:
    print(f"[pampa-store-publisher] {context}: {error}", file=sys.stderr)
    traceback.print_exc(file=sys.stderr)


if __name__ == "__main__":
    raise SystemExit(main())
tion) -> None:
    print(f"[pampa-store-publisher] {context}: {error}", file=sys.stderr)
    traceback.print_exc(file=sys.stderr)


if __name__ == "__main__":
    raise SystemExit(main())
