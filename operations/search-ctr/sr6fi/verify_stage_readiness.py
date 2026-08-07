#!/usr/bin/env python3
"""Fail-closed verifier for the SR-6F-I platform-neutral stage readiness manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

CONTRACT_VERSION = "search-ctr-stage-readiness-handoff-v1"
MATRIX_REFERENCE = "verification/sc-next-track/op3-entry/sc-op3-required-input-decision-matrix.json"
DB_PACKAGE_ID = "journey-connect-db-v2.8"
DB_PACKAGE_LAST_SCRIPT = "11_search_ctr_reliability_role_noinherit_smoke_test.sql"
BLOCKED_STATUS = "BLOCKED_PLATFORM_UNDECIDED"
READY_STATUS = "READY_FOR_SR6FH_BINDING"

REQUIRED_KEYS = (
    "SR6FI_CONTRACT_VERSION",
    "SR6FI_READINESS_STATUS",
    "SR6FI_FINAL_DEPLOYMENT_PLATFORM",
    "SR6FI_PLATFORM_DECISION_REFERENCE",
    "SR6FI_PLATFORM_DECISION_STATUS",
    "SR6FI_RESOURCE_CREATION_AUTHORIZED",
    "SR6FI_BILLING_SPEND_AUTHORIZED",
    "SR6FI_IAM_MUTATION_AUTHORIZED",
    "SR6FI_DEPLOYMENT_SOURCE_SHA",
    "SR6FI_DEPLOYMENT_ARTIFACT_DIGEST",
    "SR6FI_DEPLOYMENT_RESOURCE_ID",
    "SR6FI_STAGE_DATABASE_PLATFORM",
    "SR6FI_STAGE_DATABASE_RESOURCE_ID",
    "SR6FI_STAGE_ENDPOINT_SHA256",
    "SR6FI_DB_PACKAGE_ID",
    "SR6FI_DB_PACKAGE_LAST_SCRIPT",
    "SR6FI_DB_PACKAGE_SHA256",
    "SR6FI_DB_DEPLOYMENT_EVIDENCE_SHA256",
    "SR6FI_GITHUB_ENVIRONMENT",
    "SR6FI_GITHUB_ENVIRONMENT_PROTECTION",
    "SR6FI_REQUIRED_SECRET_NAMES_SHA256",
    "SR6FI_SECRET_INVENTORY_STATUS",
    "SR6FI_EXECUTION_OPERATOR",
    "SR6FI_REVOKE_OPERATOR",
    "SR6FI_INDEPENDENT_APPROVER",
    "SR6FI_INCIDENT_OWNER",
    "SR6FI_COST_OWNER",
    "SR6FI_TEARDOWN_OPERATOR",
    "SR6FI_TEARDOWN_DEADLINE_UTC",
    "SR6FI_EVIDENCE_STORE_TYPE",
    "SR6FI_EVIDENCE_STORE_RESOURCE_ID",
    "SR6FI_EVIDENCE_RETENTION_DAYS",
    "SR6FI_STAGE_TRAFFIC_PERCENT",
    "SR6FI_PRODUCTION_TRAFFIC_PERCENT",
    "SR6FI_CANDIDATE_SERVING",
    "SR6FI_FINALITY_WRITE",
    "SR6FI_SR6FH_CONTRACT_STATUS",
)

EXTERNAL_ASSIGNMENT_KEYS = (
    "SR6FI_DEPLOYMENT_SOURCE_SHA",
    "SR6FI_DEPLOYMENT_ARTIFACT_DIGEST",
    "SR6FI_DEPLOYMENT_RESOURCE_ID",
    "SR6FI_STAGE_DATABASE_PLATFORM",
    "SR6FI_STAGE_DATABASE_RESOURCE_ID",
    "SR6FI_STAGE_ENDPOINT_SHA256",
    "SR6FI_DB_PACKAGE_SHA256",
    "SR6FI_DB_DEPLOYMENT_EVIDENCE_SHA256",
    "SR6FI_EXECUTION_OPERATOR",
    "SR6FI_REVOKE_OPERATOR",
    "SR6FI_INDEPENDENT_APPROVER",
    "SR6FI_INCIDENT_OWNER",
    "SR6FI_COST_OWNER",
    "SR6FI_TEARDOWN_OPERATOR",
    "SR6FI_TEARDOWN_DEADLINE_UTC",
    "SR6FI_EVIDENCE_STORE_TYPE",
    "SR6FI_EVIDENCE_STORE_RESOURCE_ID",
    "SR6FI_EVIDENCE_RETENTION_DAYS",
)

PLACEHOLDERS = {
    "UNASSIGNED",
    "UNVERIFIED",
    "UNDECIDED",
    "DEFERRED",
    "DEFERRED_PLATFORM_UNDECIDED",
    "PENDING_CURRICULUM_CONFIRMATION",
    "REQUIRED_INPUT",
    "NOT_APPLICABLE_REFERENCE_ONLY",
    "DEFERRED_UNTIL_EXECUTION",
}

SECRET_PATTERNS = (
    re.compile(r"(?i)password\s*="),
    re.compile(r"(?i)token\s*="),
    re.compile(r"(?i)secret\s*="),
    re.compile(r"(?i)authorization:\s*bearer"),
    re.compile(r"-----BEGIN [A-Z ]+PRIVATE KEY-----"),
    re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"),
    re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b"),
    re.compile(r"\bAIza[0-9A-Za-z_-]{20,}\b"),
)

SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
SOURCE_SHA_RE = re.compile(r"^[0-9a-f]{40}$")
IMAGE_DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
UTC_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")
SAFE_IDENTIFIER_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:/@+-]{0,255}$")


class VerificationError(ValueError):
    pass


@dataclass(frozen=True)
class VerificationResult:
    mode: str
    readiness_status: str
    final_platform: str
    matrix_decision_status: str
    blockers: tuple[str, ...]
    source_db_package_sha256: str
    required_secret_names_sha256: str
    safe_binding: dict[str, str]

    def as_json(self) -> dict[str, object]:
        return {
            "contractVersion": CONTRACT_VERSION,
            "mode": self.mode,
            "readinessStatus": self.readiness_status,
            "finalDeploymentPlatform": self.final_platform,
            "matrixDecisionStatus": self.matrix_decision_status,
            "blockers": list(self.blockers),
            "sourceDbPackageSha256": self.source_db_package_sha256,
            "requiredSecretNamesSha256": self.required_secret_names_sha256,
            "safeBinding": self.safe_binding,
        }


def repository_root(start: Path | None = None) -> Path:
    current = (start or Path.cwd()).resolve()
    for candidate in (current, *current.parents):
        if (candidate / "jc-backend" / "build.gradle.kts").is_file() and (
            candidate / "operations" / "search-ctr"
        ).is_dir():
            return candidate
    raise VerificationError(f"repository root not found from {current}")


def parse_manifest(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise VerificationError(f"manifest does not exist: {path}")
    values: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise VerificationError(f"manifest line {line_number} is not KEY=VALUE")
        key, value = line.split("=", 1)
        if not re.fullmatch(r"SR6FI_[A-Z0-9_]+", key):
            raise VerificationError(f"manifest line {line_number} has invalid key: {key}")
        if key in values:
            raise VerificationError(f"manifest contains duplicate key: {key}")
        if not value or value != value.strip():
            raise VerificationError(f"manifest key {key} has an empty or padded value")
        values[key] = value

    missing = sorted(set(REQUIRED_KEYS) - values.keys())
    extra = sorted(values.keys() - set(REQUIRED_KEYS))
    if missing:
        raise VerificationError(f"manifest missing required keys: {', '.join(missing)}")
    if extra:
        raise VerificationError(f"manifest has unknown keys: {', '.join(extra)}")
    return values


def read_matrix(root: Path, manifest: dict[str, str]) -> dict[str, object]:
    reference = manifest["SR6FI_PLATFORM_DECISION_REFERENCE"]
    if reference != MATRIX_REFERENCE:
        raise VerificationError("manifest platform decision reference is not authoritative")
    matrix_path = root / reference
    if not matrix_path.is_file():
        raise VerificationError(f"authoritative platform matrix missing: {matrix_path}")
    try:
        matrix = json.loads(matrix_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise VerificationError(f"authoritative platform matrix is invalid JSON: {exc}") from exc
    if not isinstance(matrix, dict):
        raise VerificationError("authoritative platform matrix must be a JSON object")
    return matrix


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def source_db_package_sha256(root: Path) -> str:
    package = root / "database" / DB_PACKAGE_ID
    if not package.is_dir():
        raise VerificationError(f"source DB package missing: {package}")
    ordered = sorted(path for path in package.iterdir() if path.is_file())
    required_last = package / DB_PACKAGE_LAST_SCRIPT
    if required_last not in ordered:
        raise VerificationError(f"source DB package missing terminal script: {DB_PACKAGE_LAST_SCRIPT}")
    digest = hashlib.sha256()
    for path in ordered:
        relative = path.relative_to(root).as_posix().encode("utf-8")
        content = path.read_bytes()
        digest.update(len(relative).to_bytes(4, "big"))
        digest.update(relative)
        digest.update(len(content).to_bytes(8, "big"))
        digest.update(content)
    return digest.hexdigest()


def reject_secret_material(manifest: dict[str, str]) -> None:
    for key, value in manifest.items():
        lower = value.lower()
        if "://" in lower or lower.startswith("jdbc:"):
            raise VerificationError(f"manifest must not contain endpoint or URI material: {key}")
        for pattern in SECRET_PATTERNS:
            if pattern.search(value):
                raise VerificationError(f"manifest appears to contain secret material: {key}")


def require_fixed_contract(manifest: dict[str, str]) -> None:
    expected = {
        "SR6FI_CONTRACT_VERSION": CONTRACT_VERSION,
        "SR6FI_DB_PACKAGE_ID": DB_PACKAGE_ID,
        "SR6FI_DB_PACKAGE_LAST_SCRIPT": DB_PACKAGE_LAST_SCRIPT,
        "SR6FI_GITHUB_ENVIRONMENT": "stage",
        "SR6FI_STAGE_TRAFFIC_PERCENT": "0",
        "SR6FI_PRODUCTION_TRAFFIC_PERCENT": "0",
        "SR6FI_CANDIDATE_SERVING": "FORBIDDEN",
        "SR6FI_FINALITY_WRITE": "DISABLED",
        "SR6FI_SR6FH_CONTRACT_STATUS": "BLOCKED_EXTERNAL_STAGE_ACCESS",
    }
    for key, value in expected.items():
        if manifest[key] != value:
            raise VerificationError(f"fixed contract mismatch for {key}: {manifest[key]}")


def matrix_bool(matrix: dict[str, object], key: str) -> bool:
    value = matrix.get(key)
    if not isinstance(value, bool):
        raise VerificationError(f"authoritative matrix key {key} is not boolean")
    return value


def validate_secret_inventory(root: Path, manifest: dict[str, str]) -> str:
    inventory = root / "operations" / "search-ctr" / "sr6fi" / "required-secret-names.txt"
    if not inventory.is_file():
        raise VerificationError("required secret-name inventory is missing")
    lines = [line.strip() for line in inventory.read_text(encoding="utf-8").splitlines() if line.strip()]
    if len(lines) != len(set(lines)):
        raise VerificationError("required secret-name inventory contains duplicates")
    if any("=" in line or not re.fullmatch(r"SR6FH_[A-Z0-9_]+", line) for line in lines):
        raise VerificationError("required secret-name inventory contains invalid entries")
    digest = sha256_file(inventory)
    if manifest["SR6FI_REQUIRED_SECRET_NAMES_SHA256"] != digest:
        raise VerificationError("required secret-name inventory digest mismatch")
    return digest


def blockers_from_matrix(matrix: dict[str, object]) -> list[str]:
    blockers: list[str] = []
    if matrix.get("final_deployment_platform") == "UNDECIDED":
        blockers.append("FINAL_DEPLOYMENT_PLATFORM_UNDECIDED")
    if matrix.get("deployment_implementation") == "DEFERRED":
        blockers.append("DEPLOYMENT_IMPLEMENTATION_DEFERRED")
    if not matrix_bool(matrix, "cloud_resource_creation_authorized"):
        blockers.append("RESOURCE_CREATION_NOT_AUTHORIZED")
    if not matrix_bool(matrix, "billing_spend_authorized"):
        blockers.append("BILLING_SPEND_NOT_AUTHORIZED")
    if not matrix_bool(matrix, "iam_mutation_authorized"):
        blockers.append("IAM_MUTATION_NOT_AUTHORIZED")
    return blockers


def assert_placeholder(value: str, key: str) -> None:
    if value not in PLACEHOLDERS:
        raise VerificationError(f"template key {key} must remain unresolved, got {value}")


def validate_template_mode(
    manifest: dict[str, str], matrix: dict[str, object], blockers: list[str]
) -> None:
    if manifest["SR6FI_READINESS_STATUS"] != BLOCKED_STATUS:
        raise VerificationError("template readiness status must remain blocked")
    if manifest["SR6FI_FINAL_DEPLOYMENT_PLATFORM"] != "UNDECIDED":
        raise VerificationError("template must not select a deployment platform")
    if manifest["SR6FI_PLATFORM_DECISION_STATUS"] != str(matrix.get("decision_status")):
        raise VerificationError("template platform decision status is stale")
    if matrix.get("final_deployment_platform") != "UNDECIDED":
        raise VerificationError("template mode is invalid after authoritative platform selection")
    for key in EXTERNAL_ASSIGNMENT_KEYS:
        assert_placeholder(manifest[key], key)
    for key in (
        "SR6FI_RESOURCE_CREATION_AUTHORIZED",
        "SR6FI_BILLING_SPEND_AUTHORIZED",
        "SR6FI_IAM_MUTATION_AUTHORIZED",
    ):
        if manifest[key] != "NO":
            raise VerificationError(f"template authorization must remain NO: {key}")
    if manifest["SR6FI_GITHUB_ENVIRONMENT_PROTECTION"] != "UNVERIFIED":
        raise VerificationError("template GitHub environment protection must remain UNVERIFIED")
    if manifest["SR6FI_SECRET_INVENTORY_STATUS"] != "UNVERIFIED":
        raise VerificationError("template secret inventory status must remain UNVERIFIED")
    if not blockers:
        raise VerificationError("template mode unexpectedly has no authoritative blockers")


def require_assigned_identifier(value: str, key: str) -> None:
    if value in PLACEHOLDERS:
        raise VerificationError(f"ready manifest key remains unresolved: {key}")
    if not SAFE_IDENTIFIER_RE.fullmatch(value):
        raise VerificationError(f"ready manifest key has unsafe identifier syntax: {key}")


def validate_ready_mode(
    manifest: dict[str, str], matrix: dict[str, object], source_package_digest: str, blockers: list[str]
) -> None:
    if blockers:
        raise VerificationError("authoritative platform matrix still blocks readiness: " + ", ".join(blockers))
    if manifest["SR6FI_READINESS_STATUS"] != READY_STATUS:
        raise VerificationError("ready mode requires READY_FOR_SR6FH_BINDING")
    final_platform = matrix.get("final_deployment_platform")
    if not isinstance(final_platform, str) or final_platform == "UNDECIDED":
        raise VerificationError("authoritative final deployment platform is not selected")
    if manifest["SR6FI_FINAL_DEPLOYMENT_PLATFORM"] != final_platform:
        raise VerificationError("manifest platform does not match authoritative matrix")
    if manifest["SR6FI_PLATFORM_DECISION_STATUS"] != str(matrix.get("decision_status")):
        raise VerificationError("ready manifest platform decision status is stale")
    for key in (
        "SR6FI_RESOURCE_CREATION_AUTHORIZED",
        "SR6FI_BILLING_SPEND_AUTHORIZED",
        "SR6FI_IAM_MUTATION_AUTHORIZED",
    ):
        if manifest[key] != "YES":
            raise VerificationError(f"ready manifest authorization must be YES: {key}")
    if manifest["SR6FI_GITHUB_ENVIRONMENT_PROTECTION"] != "VERIFIED":
        raise VerificationError("GitHub stage environment protection is not verified")
    if manifest["SR6FI_SECRET_INVENTORY_STATUS"] != "VERIFIED_NAMES_ONLY":
        raise VerificationError("required secret-name inventory is not verified")

    if not SOURCE_SHA_RE.fullmatch(manifest["SR6FI_DEPLOYMENT_SOURCE_SHA"]):
        raise VerificationError("deployment source SHA must be 40 lowercase hex characters")
    if not IMAGE_DIGEST_RE.fullmatch(manifest["SR6FI_DEPLOYMENT_ARTIFACT_DIGEST"]):
        raise VerificationError("deployment artifact digest must be sha256:<64 lowercase hex>")
    for key in (
        "SR6FI_STAGE_ENDPOINT_SHA256",
        "SR6FI_DB_PACKAGE_SHA256",
        "SR6FI_DB_DEPLOYMENT_EVIDENCE_SHA256",
    ):
        if not SHA256_RE.fullmatch(manifest[key]):
            raise VerificationError(f"ready manifest key must be 64 lowercase hex: {key}")
    if manifest["SR6FI_DB_PACKAGE_SHA256"] != source_package_digest:
        raise VerificationError("deployed DB package digest does not match reviewed source package")
    if not UTC_RE.fullmatch(manifest["SR6FI_TEARDOWN_DEADLINE_UTC"]):
        raise VerificationError("teardown deadline must use exact UTC second format")
    try:
        retention_days = int(manifest["SR6FI_EVIDENCE_RETENTION_DAYS"])
    except ValueError as exc:
        raise VerificationError("evidence retention days must be an integer") from exc
    if retention_days <= 0:
        raise VerificationError("evidence retention days must be positive")
    for key in EXTERNAL_ASSIGNMENT_KEYS:
        if key in {
            "SR6FI_DEPLOYMENT_SOURCE_SHA",
            "SR6FI_DEPLOYMENT_ARTIFACT_DIGEST",
            "SR6FI_STAGE_ENDPOINT_SHA256",
            "SR6FI_DB_PACKAGE_SHA256",
            "SR6FI_DB_DEPLOYMENT_EVIDENCE_SHA256",
            "SR6FI_TEARDOWN_DEADLINE_UTC",
            "SR6FI_EVIDENCE_RETENTION_DAYS",
        }:
            continue
        require_assigned_identifier(manifest[key], key)


def verify(manifest_path: Path, mode: str, root: Path | None = None) -> VerificationResult:
    repository = root or repository_root(manifest_path.parent)
    manifest = parse_manifest(manifest_path)
    reject_secret_material(manifest)
    require_fixed_contract(manifest)
    matrix = read_matrix(repository, manifest)
    source_package_digest = source_db_package_sha256(repository)
    inventory_digest = validate_secret_inventory(repository, manifest)
    blockers = blockers_from_matrix(matrix)

    if mode == "template":
        validate_template_mode(manifest, matrix, blockers)
    elif mode == "ready":
        validate_ready_mode(manifest, matrix, source_package_digest, blockers)
    else:
        raise VerificationError(f"unsupported mode: {mode}")

    safe_binding = {
        "deploymentSourceSha": manifest["SR6FI_DEPLOYMENT_SOURCE_SHA"],
        "deploymentArtifactDigest": manifest["SR6FI_DEPLOYMENT_ARTIFACT_DIGEST"],
        "stageEndpointSha256": manifest["SR6FI_STAGE_ENDPOINT_SHA256"],
        "dbPackageSha256": manifest["SR6FI_DB_PACKAGE_SHA256"],
        "dbDeploymentEvidenceSha256": manifest["SR6FI_DB_DEPLOYMENT_EVIDENCE_SHA256"],
    }
    return VerificationResult(
        mode=mode,
        readiness_status=manifest["SR6FI_READINESS_STATUS"],
        final_platform=manifest["SR6FI_FINAL_DEPLOYMENT_PLATFORM"],
        matrix_decision_status=str(matrix.get("decision_status")),
        blockers=tuple(blockers),
        source_db_package_sha256=source_package_digest,
        required_secret_names_sha256=inventory_digest,
        safe_binding=safe_binding,
    )


def write_json(path: Path | None, payload: dict[str, object]) -> None:
    serialized = json.dumps(payload, indent=2, sort_keys=True) + "\n"
    if path is None:
        sys.stdout.write(serialized)
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(serialized, encoding="utf-8")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--mode", required=True, choices=("template", "ready"))
    parser.add_argument("--output", type=Path)
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        result = verify(args.manifest.resolve(), args.mode)
        write_json(args.output.resolve() if args.output else None, result.as_json())
        return 0
    except VerificationError as exc:
        sys.stderr.write(f"SR-6F-I readiness verification failed: {exc}\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
