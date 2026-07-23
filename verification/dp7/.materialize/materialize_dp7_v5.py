#!/usr/bin/env python3
from pathlib import Path
import base64
import io
import lzma
import tarfile

root = Path(__file__).resolve().parents[3]
staging = root / "verification/dp7/.materialize"
first = staging / "dp7_payload.tarsegment.000"
tail = sorted(staging.glob("dp7_payload.tailsegment.*"))
if not first.is_file() or not tail:
    raise SystemExit("DP-7 compressed payload segments missing")
encoded = first.read_text(encoding="ascii") + "".join(path.read_text(encoding="ascii") for path in tail)
raw = lzma.decompress(base64.b64decode(encoded))
with tarfile.open(fileobj=io.BytesIO(raw), mode="r:") as source:
    members = source.getmembers()
    for member in members:
        target = (root / member.name).resolve()
        if root.resolve() not in target.parents:
            raise SystemExit(f"unsafe payload path: {member.name}")
    source.extractall(root, filter="data")
print(f"DP-7 payload materialized: {len(members)} files")
