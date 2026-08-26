#!/usr/bin/env python3
"""Local stdio MCP server for Pampa Store tooling."""

from __future__ import annotations

import json
import sys
import os
import traceback
from typing import Any, Callable

# --- PROTEZIONE FLUSSI E BINARY MODE ---
_REAL_STDOUT_FD = sys.stdout.fileno()
_REAL_STDIN_FD = sys.stdin.fileno()

if sys.platform == "win32":
    import msvcrt
    msvcrt.setmode(_REAL_STDOUT_FD, os.O_BINARY)
    msvcrt.setmode(_REAL_STDIN_FD, os.O_BINARY)

# Reindirizziamo sys.stdout a sys.stderr per evitare print() accidentali
class StdoutRedirector:
    def write(self, data):
        if data:
            sys.stderr.write(data)
            sys.stderr.flush()
    def flush(self):
        sys.stderr.flush()

sys.stdout = StdoutRedirector() # type: ignore

import publisher
import store_tools

SERVER_INFO = {"name": "pampa-store-publisher", "version": "0.4.0"}
_buffer = b""

def log_diag(msg: str):
    print(f"[pampa-store-publisher] {msg}", file=sys.stderr, flush=True)

def build_tool(name: str, title: str, description: str, input_schema: dict[str, Any]) -> dict[str, Any]:
    return {"name": name, "title": title, "description": description, "inputSchema": input_schema}

COMMON_STORE_PROPS = {
    "storeOwner": {"type": "string", "description": "Store owner."},
    "storeRepo": {"type": "string", "description": "Store repository."},
    "storeManifestPath": {"type": "string", "description": "Path to manifest.json."},
}

TOOLS = [
    build_tool("get_pampa_store_context", "Context", "Local info.", {"type": "object", "properties": {"repoRoot": {"type": "string"}}, "additionalProperties": False}),
    build_tool("get_pampa_store_catalog", "Catalog", "Full manifest.", {"type": "object", "properties": COMMON_STORE_PROPS, "additionalProperties": False}),
    build_tool("list_pampa_store_apps", "List Apps", "Search apps.", {"type": "object", "properties": {**COMMON_STORE_PROPS, "query": {"type": "string"}}, "additionalProperties": False}),
    build_tool("get_pampa_store_app", "Get App", "App details.", {"type": "object", "properties": {"appId": {"type": "string"}}, "required": ["appId"], "additionalProperties": False}),
    build_tool(
        "publish_pampa_store_android_release",
        "Publish",
        "Build and sync.",
        publisher.PUBLISH_TOOL_INPUT_SCHEMA,
    ),
]

HANDLERS = {
    "get_pampa_store_context": store_tools.get_pampa_store_context,
    "get_pampa_store_catalog": store_tools.get_pampa_store_catalog,
    "list_pampa_store_apps": store_tools.list_pampa_store_apps,
    "get_pampa_store_app": store_tools.get_pampa_store_app,
    "publish_pampa_store_android_release": publisher.publish_from_params,
}

def read_message() -> dict[str, Any] | None:
    global _buffer
    while True:
        if b"\r\n\r\n" in _buffer:
            header_part, body_part = _buffer.split(b"\r\n\r\n", 1)
            headers = {}
            for line in header_part.decode("ascii", errors="ignore").split("\r\n"):
                if ":" in line:
                    k, v = line.split(":", 1)
                    headers[k.strip().lower()] = v.strip()
            
            content_length = int(headers.get("content-length", 0))
            if len(body_part) >= content_length:
                message_body = body_part[:content_length]
                _buffer = body_part[content_length:]
                return json.loads(message_body.decode("utf-8"))
        
        chunk = os.read(_REAL_STDIN_FD, 8192)
        if not chunk: return None
        _buffer += chunk

def write_message(payload: dict[str, Any]) -> None:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    header = f"Content-Length: {len(body)}\r\n\r\n".encode("ascii")
    os.write(_REAL_STDOUT_FD, header + body)

def handle_message(message: dict[str, Any]) -> dict[str, Any] | None:
    method = message.get("method")
    msg_id = message.get("id")
    params = message.get("params", {})

    if method == "initialize":
        # Rispondiamo con la versione richiesta dal client per massima compatibilità
        client_version = params.get("protocolVersion", "2024-11-05")
        return {"jsonrpc": "2.0", "id": msg_id, "result": {
            "protocolVersion": client_version,
            "capabilities": {"tools": {"listChanged": False}},
            "serverInfo": SERVER_INFO,
        }}

    if method == "notifications/initialized": return None
    if method == "ping": return {"jsonrpc": "2.0", "id": msg_id, "result": {}}
    if method == "tools/list": return {"jsonrpc": "2.0", "id": msg_id, "result": {"tools": TOOLS}}

    if method == "tools/call":
        tool_name = params.get("name")
        args = params.get("arguments", {})
        handler = HANDLERS.get(tool_name)
        if not handler:
            return {"jsonrpc": "2.0", "id": msg_id, "error": {"code": -32602, "message": f"Unknown tool: {tool_name}"}}
        try:
            result = handler(args)
            return {"jsonrpc": "2.0", "id": msg_id, "result": {"content": [{"type": "text", "text": json.dumps(result, indent=2)}], "isError": False}}
        except Exception as e:
            return {"jsonrpc": "2.0", "id": msg_id, "result": {"content": [{"type": "text", "text": str(e)}], "isError": True}}

    if msg_id is not None:
        return {"jsonrpc": "2.0", "id": msg_id, "error": {"code": -32601, "message": f"Method not found: {method}"}}
    return None

def main():
    log_diag("Server v0.4.0 active and listening...")
    try:
        while True:
            msg = read_message()
            if msg is None: break
            resp = handle_message(msg)
            if resp is not None: write_message(resp)
    except Exception as e:
        log_diag(f"FATAL ERROR: {e}")
        traceback.print_exc(file=sys.stderr)
    return 0

if __name__ == "__main__":
    sys.exit(main())
