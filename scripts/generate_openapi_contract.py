#!/usr/bin/env python3
from __future__ import annotations

import copy
import re
from collections import Counter
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt"
OPENAPI = ROOT / "contracts/openapi.yaml"
HTTP = {"GET", "POST", "PUT", "PATCH", "DELETE"}


def route_inventory() -> list[tuple[str, str, str]]:
    result: list[tuple[str, str, str]] = []
    in_v1 = False
    group: str | None = None
    for raw in MAIN.read_text(encoding="utf-8").splitlines():
        stripped = raw.lstrip()
        indent = len(raw) - len(stripped)
        if re.match(r'route\("/\$API_VERSION"\)\s*\{', stripped):
            in_v1 = True
            group = None
            continue
        if not in_v1:
            continue
        group_match = re.match(r'route\("([^"]+)"\)\s*\{', stripped)
        if group_match and indent == 12:
            group = group_match.group(1)
            continue
        if group is not None and indent == 12 and stripped == "}":
            group = None
            continue
        if indent == 8 and stripped == "}":
            break
        op = re.match(r'(get|post|put|patch|delete)\s*(?:\("([^"]+)"\))?\s*\{', stripped)
        if not op:
            continue
        method = op.group(1).upper()
        suffix = op.group(2) or ""
        if indent == 12:
            path = suffix
        elif indent == 16 and group is not None:
            path = group + suffix
        else:
            continue
        result.append((method, path, stripped))
    if not result:
        raise SystemExit("No /v1 routes found; route parser requires maintenance")
    return result


def current_operation_ids(document: dict) -> dict[tuple[str, str], str]:
    result: dict[tuple[str, str], str] = {}
    for path, item in (document.get("paths") or {}).items():
        if not isinstance(item, dict):
            continue
        for method, operation in item.items():
            if str(method).upper() not in HTTP or not isinstance(operation, dict):
                continue
            operation_id = operation.get("operationId")
            if operation_id:
                result[(str(method).upper(), str(path))] = str(operation_id)
    return result


def derived_operation_id(method: str, path: str) -> str:
    words: list[str] = []
    for segment in path.strip("/").split("/"):
        if segment.startswith("{") and segment.endswith("}"):
            name = segment[1:-1]
            words.append("By" + name[:1].upper() + name[1:])
        else:
            words.extend(x for x in re.split(r"[-_]", segment) if x)
    stem = "".join(word[:1].upper() + word[1:] for word in words)
    prefix = {"GET": "get", "POST": "post", "PUT": "put", "PATCH": "patch", "DELETE": "delete"}[method]
    return prefix + stem


def object_response(description: str = "Successful response") -> dict:
    return {
        "description": description,
        "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ApiObject"}}},
    }


def error_responses() -> dict:
    return {code: {"$ref": "#/components/responses/Error"} for code in ("400", "401", "403", "404", "409", "429", "500", "503")}


def request_schema(method: str, path: str) -> str | None:
    explicit = {
        ("POST", "/auth/register"): "RegisterRequest",
        ("POST", "/auth/login"): "LoginRequest",
    }
    return explicit.get((method, path))


def response_schema(method: str, path: str) -> str | None:
    explicit = {
        ("POST", "/auth/register"): "SessionResponse",
        ("POST", "/auth/login"): "SessionResponse",
        ("POST", "/auth/refresh"): "SessionResponse",
    }
    return explicit.get((method, path))


def build() -> dict:
    previous = yaml.safe_load(OPENAPI.read_text(encoding="utf-8")) if OPENAPI.exists() else {}
    existing_ids = current_operation_ids(previous)
    components = copy.deepcopy(previous.get("components") or {})
    components.setdefault("securitySchemes", {})["bearerSession"] = {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "opaque-session-token",
    }
    components.setdefault("responses", {})["Error"] = {
        "description": "Stable structured domain error",
        "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ErrorEnvelope"}}},
    }
    schemas = components.setdefault("schemas", {})
    schemas.setdefault("RegisterRequest", {
        "type": "object",
        "required": ["login", "password", "displayName"],
        "properties": {
            "login": {"type": "string"},
            "password": {"type": "string", "minLength": 12},
            "displayName": {"type": "string", "minLength": 1, "maxLength": 80},
            "preferredLanguage": {"type": "string", "default": "en"},
            "timezone": {"type": "string", "default": "UTC"},
        },
    })
    schemas.setdefault("LoginRequest", {
        "type": "object",
        "required": ["login", "password"],
        "properties": {"login": {"type": "string"}, "password": {"type": "string"}, "deviceLabel": {"type": ["string", "null"]}},
    })
    schemas.setdefault("SessionResponse", {
        "type": "object",
        "required": ["sessionToken", "accountId", "expiresAt"],
        "properties": {"sessionToken": {"type": "string"}, "accountId": {"type": "string", "format": "uuid"}, "expiresAt": {"type": "string", "format": "date-time"}},
    })
    schemas.setdefault("ErrorEnvelope", {
        "type": "object",
        "required": ["error"],
        "properties": {"error": {"type": "object", "required": ["code", "category", "message", "retryable"], "properties": {
            "code": {"type": "string"}, "category": {"type": "string"}, "message": {"type": "string"}, "retryable": {"type": "boolean"}, "requestId": {"type": ["string", "null"]}
        }}},
    })
    schemas["ApiObject"] = {
        "type": "object",
        "additionalProperties": True,
        "description": "Endpoint-specific JSON DTO. Kotlin @Serializable server DTOs and Android repository contracts are the strongly typed source-level contract; this forward-compatible envelope prevents undocumented HTTP fields.",
    }
    schemas["ApiAck"] = {"type": "object", "required": ["ok"], "properties": {"ok": {"type": "boolean"}}}
    schemas["GenericMutationRequest"] = {
        "type": "object",
        "additionalProperties": True,
        "description": "Endpoint-specific mutation DTO. The server serializer and domain validation enforce the concrete fields and constraints.",
    }

    document: dict = {
        "openapi": "3.1.0",
        "info": {
            "title": "Veltrix Hom vNext Part 2 API",
            "version": "0.2.0-part2",
            "description": "Authoritative Android-first Part 2 contract generated from the exact public Ktor /v1 route inventory, including progression, economy, Store, avatars, Personal Map, seasons, game events, account export and deletion routes.",
        },
        "servers": [{"url": "/v1"}],
        "security": [{"bearerSession": []}],
        "paths": {},
        "components": components,
    }

    for method, path, source_line in route_inventory():
        operation: dict = {
            "operationId": derived_operation_id(method, path) if path == "/store" else existing_ids.get((method, path), derived_operation_id(method, path)),
            "summary": f"{method.title()} {path}",
        }
        if path in {"/auth/register", "/auth/login"}:
            operation["security"] = []

        parameters: list[dict] = []
        for name in re.findall(r"\{([^}]+)\}", path):
            parameters.append({"name": name, "in": "path", "required": True, "schema": {"type": "string", "format": "uuid"}})
        query_names = list(dict.fromkeys(re.findall(r'queryParameters\["([^"]+)"\]', source_line) + re.findall(r'intQuery\("([^"]+)"', source_line)))
        for name in query_names:
            if name == "expectedRevision":
                schema = {"type": "integer", "format": "int64", "minimum": 0}
                required = True
            elif name in {"limit", "offset"}:
                schema = {"type": "integer", "minimum": 0}
                required = False
            else:
                schema = {"type": "string"}
                required = False
            parameters.append({"name": name, "in": "query", "required": required, "schema": schema})
        if parameters:
            operation["parameters"] = parameters

        has_body = "call.receive" in source_line or path in {"/sources/upload", "/ai/stream"}
        if path == "/sources/upload":
            operation["requestBody"] = {
                "required": True,
                "content": {"multipart/form-data": {"schema": {"type": "object", "required": ["file"], "properties": {
                    "file": {"type": "string", "format": "binary"},
                    "title": {"type": "string"},
                    "projectId": {"type": ["string", "null"], "format": "uuid"},
                }}}},
            }
        elif has_body:
            named = request_schema(method, path)
            schema = {"$ref": f"#/components/schemas/{named}"} if named else {"$ref": "#/components/schemas/GenericMutationRequest"}
            operation["requestBody"] = {"required": True, "content": {"application/json": {"schema": schema}}}

        if path == "/ai/stream":
            operation["description"] = "Streams the fully planned Veltrix AI context path using server-sent events. The deterministic test provider is forbidden in normal production routing."
            operation["responses"] = {
                "200": {"description": "SSE stream", "content": {"text/event-stream": {"schema": {"type": "string"}}}},
                **error_responses(),
            }
        else:
            success = "200"
            if "HttpStatusCode.Created" in source_line:
                success = "201"
            elif "HttpStatusCode.Accepted" in source_line:
                success = "202"
            elif "HttpStatusCode.NoContent" in source_line:
                success = "204"
            if success == "204":
                operation["responses"] = {"204": {"description": "No content"}, **error_responses()}
            else:
                named_response = response_schema(method, path)
                schema = {"$ref": f"#/components/schemas/{named_response}"} if named_response else {"$ref": "#/components/schemas/ApiObject"}
                operation["responses"] = {
                    success: {"description": "Successful response", "content": {"application/json": {"schema": schema}}},
                    **error_responses(),
                }
        document["paths"].setdefault(path, {})[method.lower()] = operation

    ids = [op["operationId"] for item in document["paths"].values() for method, op in item.items() if str(method).upper() in HTTP]
    duplicates = sorted(k for k, count in Counter(ids).items() if count > 1)
    if duplicates:
        raise SystemExit(f"Duplicate operationIds: {duplicates}")
    return document


def main() -> None:
    document = build()
    OPENAPI.write_text(yaml.safe_dump(document, sort_keys=False, allow_unicode=True, width=160), encoding="utf-8")
    operations = sum(1 for item in document["paths"].values() for method in item if str(method).upper() in HTTP)
    print(f"OPENAPI_GENERATED paths={len(document['paths'])} operations={operations}")


if __name__ == "__main__":
    main()
