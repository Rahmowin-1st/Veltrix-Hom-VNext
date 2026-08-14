#!/usr/bin/env python3
"""Fail when public Ktor v1 routes and OpenAPI operations drift.

Main.kt intentionally keeps the public route registration structurally simple. This guard
tracks the /v1 route scope and one level of route("/group") nesting by indentation. Health
and readiness are operational endpoints outside the public v1 contract.
"""
from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "server/src/main/kotlin/com/veltrix/hom/vnext/server/Main.kt"
OPENAPI = ROOT / "contracts/openapi.yaml"
HTTP = {"GET", "POST", "PUT", "PATCH", "DELETE"}


def server_routes() -> set[tuple[str, str]]:
    routes: set[tuple[str, str]] = set()
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
        # The /v1 block itself closes at eight spaces after all public registrations.
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
        if not path.startswith("/"):
            raise SystemExit(f"invalid registered route path: {method} {path!r}")
        routes.add((method, path))
    if not routes:
        raise SystemExit("no /v1 Ktor routes discovered; drift parser needs maintenance")
    return routes


def openapi_routes() -> set[tuple[str, str]]:
    text = OPENAPI.read_text(encoding="utf-8")
    if not text.startswith("openapi: 3.1"):
        raise SystemExit("OpenAPI must remain 3.1.x")
    if not re.search(r"(?m)^- url: /v1\s*$", text):
        raise SystemExit("OpenAPI servers must declare /v1 base path")
    result: set[tuple[str, str]] = set()
    operation_ids: set[str] = set()
    current_path: str | None = None
    current_method: str | None = None
    for raw in text.splitlines():
        pm = re.match(r"^  (/[^:]*):\s*$", raw)
        if pm:
            current_path = pm.group(1)
            current_method = None
            continue
        mm = re.match(r"^    (get|post|put|patch|delete):\s*$", raw)
        if mm and current_path is not None:
            current_method = mm.group(1).upper()
            result.add((current_method, current_path))
            continue
        om = re.match(r"^      operationId:\s*(\S+)\s*$", raw)
        if om and current_path is not None and current_method is not None:
            operation_id = om.group(1)
            if operation_id in operation_ids:
                raise SystemExit(f"duplicate operationId: {operation_id}")
            operation_ids.add(operation_id)
    if len(operation_ids) != len(result):
        raise SystemExit(f"missing operationId: operations={len(result)} ids={len(operation_ids)}")
    return result


def main() -> None:
    registered = server_routes()
    documented = openapi_routes()
    missing = sorted(registered - documented)
    stale = sorted(documented - registered)
    print(f"KTOR_V1_OPERATIONS={len(registered)}")
    print(f"OPENAPI_OPERATIONS={len(documented)}")
    if missing:
        print("UNDOCUMENTED:")
        for method, path in missing:
            print(f"  {method} {path}")
    if stale:
        print("STALE_OPENAPI:")
        for method, path in stale:
            print(f"  {method} {path}")
    if missing or stale:
        raise SystemExit(1)
    print("OPENAPI_DRIFT=PASS")


if __name__ == "__main__":
    main()
