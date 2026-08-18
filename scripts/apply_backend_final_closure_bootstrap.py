#!/usr/bin/env python3
from __future__ import annotations
import base64, io, shutil, tarfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
chunk_dir = ROOT / "ci/backend_final_payload"
chunks = sorted(chunk_dir.glob("chunk-*.b64"))
if not chunks:
    raise SystemExit("backend final payload chunks missing")
raw = base64.b64decode("".join(p.read_text(encoding="ascii") for p in chunks))
with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as tf:
    root = ROOT.resolve()
    for member in tf.getmembers():
        target = (ROOT / member.name).resolve()
        if target != root and root not in target.parents:
            raise SystemExit(f"unsafe payload path: {member.name}")
    tf.extractall(ROOT)
shutil.rmtree(chunk_dir)
(ROOT / "scripts/apply_backend_final_closure_bootstrap.py").unlink(missing_ok=True)
(ROOT / ".github/workflows/backend-final-closure-bootstrap.yml").unlink(missing_ok=True)
print("BACKEND_FINAL_CLOSURE_PAYLOAD_APPLIED=PASS")
