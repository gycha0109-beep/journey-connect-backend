#!/usr/bin/env python3
from pathlib import Path
import base64
import zipfile

root = Path(__file__).resolve().parents[3]
parts = sorted((root / "verification/dp7/.materialize").glob("dp7_payload.part.*"))
if not parts:
    raise SystemExit("DP-7 payload parts missing")
encoded = "".join(path.read_text(encoding="ascii") for path in parts)
archive = Path("/tmp/dp7_payload.zip")
archive.write_bytes(base64.b64decode(encoded))
with zipfile.ZipFile(archive) as source:
    for member in source.infolist():
        target = (root / member.filename).resolve()
        if root.resolve() not in target.parents:
            raise SystemExit(f"unsafe payload path: {member.filename}")
    source.extractall(root)
    count = len(source.infolist())
print(f"DP-7 payload materialized: {count} files")
