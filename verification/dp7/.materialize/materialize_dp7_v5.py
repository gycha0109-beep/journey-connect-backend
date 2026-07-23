#!/usr/bin/env python3
from pathlib import Path
import base64
import hashlib
import io
import lzma
import tarfile

root = Path(__file__).resolve().parents[3]
staging = root / "verification/dp7/.materialize"
relative_parts = [
    "dp7_final.small.000",
    "dp7_final.repair.001.0",
    "dp7_final.repair.001.1",
    "dp7_final.repair.001.2",
    "dp7_final.small.002",
    "dp7_final.small.003",
    "dp7_final.repair.004.0",
    "dp7_final.repair.004.1",
    "dp7_final.repair.004.2",
    "dp7_final.small.005",
    "dp7_final.small.006",
]
parts = [staging / name for name in relative_parts]
missing = [str(path.relative_to(root)) for path in parts if not path.is_file()]
if missing:
    raise SystemExit(f"DP-7 final payload segments missing: {missing}")
encoded = "".join(path.read_text(encoding="ascii") for path in parts)
if len(encoded) != 59340 or hashlib.sha256(encoded.encode("ascii")).hexdigest() != "e8b2fac2c5569cd14e3613b90eb540a95724f5f4acbd362ab27d2ddef1a74756":
    raise SystemExit("DP-7 encoded payload fingerprint mismatch")
raw = lzma.decompress(base64.b64decode(encoded, validate=True))
if len(raw) != 389120 or hashlib.sha256(raw).hexdigest() != "9517981afec0be9642d3b4459ed62b9961d79b5da5d11f8a9aec227ab285f4c1":
    raise SystemExit("DP-7 archive fingerprint mismatch")
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
