#!/usr/bin/env python3
"""Render a review-only SR-6F-H binding proposal from a verified SR-6F-I manifest."""

from __future__ import annotations

import argparse
import hashlib
import sys
from pathlib import Path

from verify_stage_readiness import VerificationError, parse_manifest, repository_root, verify

PROPOSAL_NAME = "sr6fh-binding-proposal.env"


def render(manifest_path: Path, output_dir: Path) -> Path:
    root = repository_root(manifest_path.parent)
    result = verify(manifest_path, "ready", root)
    manifest = parse_manifest(manifest_path)

    current_contract = root / "operations" / "search-ctr" / "sr6fh" / "stage-execution-contract.env"
    contract_text = current_contract.read_text(encoding="utf-8")
    if "SR6FH_EXECUTION_STATUS=BLOCKED_EXTERNAL_STAGE_ACCESS" not in contract_text:
        raise VerificationError("current SR-6F-H contract is not in the expected blocked state")
    if "SR6FH_AUTHORIZED_STAGE_ENDPOINT_SHA256=UNASSIGNED" not in contract_text:
        raise VerificationError("current SR-6F-H endpoint fingerprint is not unassigned")
    if "SR6FH_FINALITY_WRITE=DISABLED" not in contract_text:
        raise VerificationError("current SR-6F-H finality boundary is not disabled")

    manifest_digest = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
    lines = [
        "# Review-only proposal. This file does not mutate the authoritative SR-6F-H contract.",
        "SR6FH_EXECUTION_STATUS=READY_FOR_ONE_SHOT",
        f"SR6FH_AUTHORIZED_STAGE_ENDPOINT_SHA256={manifest['SR6FI_STAGE_ENDPOINT_SHA256']}",
        f"SR6FH_BOUND_DEPLOYMENT_SOURCE_SHA={manifest['SR6FI_DEPLOYMENT_SOURCE_SHA']}",
        f"SR6FH_BOUND_DEPLOYMENT_ARTIFACT_DIGEST={manifest['SR6FI_DEPLOYMENT_ARTIFACT_DIGEST']}",
        f"SR6FH_BOUND_DB_PACKAGE_SHA256={manifest['SR6FI_DB_PACKAGE_SHA256']}",
        f"SR6FH_BOUND_DB_DEPLOYMENT_EVIDENCE_SHA256={manifest['SR6FI_DB_DEPLOYMENT_EVIDENCE_SHA256']}",
        f"SR6FH_BOUND_READINESS_MANIFEST_SHA256={manifest_digest}",
        f"SR6FH_BOUND_FINAL_DEPLOYMENT_PLATFORM={result.final_platform}",
        "SR6FH_FINALITY_WRITE=DISABLED",
        "SR6FH_BINDING_REVIEW_STATUS=PROPOSED_NOT_AUTHORIZED",
    ]
    output_dir.mkdir(parents=True, exist_ok=True)
    output = output_dir / PROPOSAL_NAME
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    try:
        output = render(args.manifest.resolve(), args.output_dir.resolve())
        print(output)
        return 0
    except (VerificationError, OSError) as exc:
        sys.stderr.write(f"SR-6F-I binding proposal failed: {exc}\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
