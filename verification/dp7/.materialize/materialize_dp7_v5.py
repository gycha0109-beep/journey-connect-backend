#!/usr/bin/env python3
from pathlib import Path
import base64
import io
import lzma
import tarfile

root = Path(__file__).resolve().parents[3]
staging = root / "verification/dp7/.materialize"
parts = sorted(staging.glob("dp7_final.small.*"))
if len(parts) != 7:
    raise SystemExit(f"DP-7 final payload segment count mismatch: {len(parts)}")
encoded = "".join(path.read_text(encoding="ascii") for path in parts)
raw = lzma.decompress(base64.b64decode(encoded))
with tarfile.open(fileobj=io.BytesIO(raw), mode="r:") as source:
    members = source.getmembers()
    for member in members:
        target = (root / member.name).resolve()
        if root.resolve() not in target.parents:
            raise SystemExit(f"unsafe payload path: {member.name}")
    source.extractall(root, filter="data")
if len(members) != 89:
    raise SystemExit(f"DP-7 payload file count mismatch: {len(members)}")
print(f"DP-7 payload materialized: {len(members)} files")
# final-materialization-trigger-v2
