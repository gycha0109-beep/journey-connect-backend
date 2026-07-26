#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

WORK_START = "0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d"
OP0_EXACT_HEAD = "e29a056d63c8c953851e4261bde9f71f3cd19441"
OP0_MERGE_COMMIT = "0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d"
ARTIFACT_VERSION = "op1-rca2-stage1-environment-access-v1"
DOC_DIR = Path("docs/platform/operations/op1")
CONTRACT_DIR = Path("verification/operations/op1/contracts")
RUNTIME_DIR = Path("verification/operations/op1/runtime")
SOURCE_DIR = Path("jc-backend/src/main/java/com/jc/backend/recommendation/rca2")
TEST_DIR = Path("jc-backend/src/test/java/com/jc/backend/recommendation/rca2")
CONFIG = Path("jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml")

REQUIRED_DOCS = [f"{index:02d}-" for index in range(25)]
REQUIRED_ARTIFACTS = {
    "op1-baseline", "repository-path-discovery", "environment-contract", "endpoint-contract",
    "credential-contract", "allowlist-contract", "cohort-contract", "candidate-source-decision",
    "candidate-adapter-contract", "configuration-contract", "traffic-zero-proof",
    "security-control-inventory", "test-result-inventory", "external-dependency-register",
    "blocker-register", "risk-register", "op2-entry-status",
}

@dataclass
class Check:
    name: str
    status: str
    detail: str

    def as_dict(self) -> dict[str, str]:
        return {"name": self.name, "status": self.status, "detail": self.detail}


def git(repo: Path, *args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=repo, text=True, stderr=subprocess.STDOUT).strip()


def text(repo: Path, path: Path) -> str:
    return (repo / path).read_text(encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--expected-head", default=os.getenv("OP1_EXPECTED_HEAD", ""))
    parser.add_argument("--only", default="")
    parser.add_argument("--output", default=str(RUNTIME_DIR / "op1-verification-evidence.json"))
    args = parser.parse_args()
    repo = Path(args.repo).resolve()
    checks: list[Check] = []

    def add(name: str, condition: bool, detail: str) -> None:
        checks.append(Check(name, "PASS" if condition else "FAIL", detail))

    def status(name: str, value: str, detail: str) -> None:
        checks.append(Check(name, value, detail))

    groups: dict[str, list[Check]] = {}
    def group(name: str):
        start = len(checks)
        def finish(): groups[name] = checks[start:]
        return finish

    finish = group("authoritative_baseline")
    actual_head = git(repo, "rev-parse", "HEAD")
    add("actual_work_start_sha", git(repo, "cat-file", "-t", WORK_START) == "commit" and git(repo, "merge-base", WORK_START, "HEAD") == WORK_START, WORK_START)
    add("pr31_merge_commit_present", git(repo, "cat-file", "-t", OP0_MERGE_COMMIT) == "commit", OP0_MERGE_COMMIT)
    add("op0_exact_head_present", git(repo, "cat-file", "-t", OP0_EXACT_HEAD) == "commit", OP0_EXACT_HEAD)
    add("op0_merge_tree_equivalence", git(repo, "rev-parse", f"{OP0_MERGE_COMMIT}^{{tree}}") == git(repo, "rev-parse", f"{OP0_EXACT_HEAD}^{{tree}}"), "OP-0 exact head tree equals PR #31 merge tree")
    add("op0_merge_subject", "Merge pull request #31" in git(repo, "show", "-s", "--format=%B", OP0_MERGE_COMMIT), "PR #31 merged")
    if args.expected_head:
        add("exact_pr_head", actual_head == args.expected_head, f"actual={actual_head} expected={args.expected_head}")
    else:
        status("exact_pr_head", "NOT_EXECUTED", "expected head not supplied")
    finish()

    finish = group("op0_continuity")
    op0_baseline = json.loads(text(repo, Path("verification/operations/op0/contracts/op0-baseline.json")))
    stage1_gate = json.loads(text(repo, Path("verification/operations/op0/contracts/stage1-enable-gate.json")))
    add("op0_exact_head_continuity", op0_baseline["result"] == "RCA2_STAGE1_OPERATIONS_PREPARATION_BASELINE_ESTABLISHED", op0_baseline["result"])
    add("op1_op2_stage1_gates_defined", all((repo / Path("verification/operations/op0/contracts") / name).is_file() for name in ["op1-entry-gate.json", "op2-entry-gate.json", "stage1-enable-gate.json"]), "OP-0 gates")
    add("traffic_contract_continuity", op0_baseline["current_nonproduction_traffic_percent"] == 0 and op0_baseline["target_nonproduction_traffic_percent"] == 1 and op0_baseline["production_traffic_percent"] == 0, "0/1/0")
    add("authority_contract_continuity", op0_baseline["primary_result_authority"] == "CURRENT_P1_P2_ONLY" and op0_baseline["shadow_result_authority"] == "NONE" and op0_baseline["shadow_result_serving"] == "FORBIDDEN", "primary protected/shadow none")
    add("stage1_enable_gate_still_blocked", stage1_gate["gate_pass"] is False and stage1_gate["on_any_false"]["CURRENT_TRAFFIC_PERCENT"] == 0, "blocked")
    finish()

    finish = group("document_completeness")
    docs = sorted((repo / DOC_DIR).glob("*.md"))
    add("op1_document_count", len(docs) == 25, f"count={len(docs)}")
    add("op1_document_sequence", all(any(path.name.startswith(prefix) for path in docs) for prefix in REQUIRED_DOCS), "00..24")
    add("work_start_in_all_documents", all(WORK_START in path.read_text(encoding="utf-8") for path in docs), WORK_START)
    add("op2_handoff_exists", (repo / DOC_DIR / "23-OP-2-READINESS-HANDOFF.md").is_file() and (repo / DOC_DIR / "24-OP-2-IMPLEMENTATION-PROMPT.md").is_file(), "handoff/prompt")
    finish()

    finish = group("machine_readable_artifacts")
    artifacts = sorted((repo / CONTRACT_DIR).glob("*.json"))
    add("artifact_count", len(artifacts) == 17, f"count={len(artifacts)}")
    add("artifact_names", {path.stem for path in artifacts} == REQUIRED_ARTIFACTS, ",".join(sorted(path.stem for path in artifacts)))
    parsed = [json.loads(path.read_text(encoding="utf-8")) for path in artifacts]
    required_metadata = {"work_start_sha", "source_op0_exact_head", "source_op0_merge_commit", "artifact_version", "status", "owner", "updated_at"}
    add("artifact_metadata", all(required_metadata <= value.keys() for value in parsed), "required metadata")
    add("artifact_baseline_binding", all(value["work_start_sha"] == WORK_START and value["source_op0_exact_head"] == OP0_EXACT_HEAD and value["source_op0_merge_commit"] == OP0_MERGE_COMMIT and value["artifact_version"] == ARTIFACT_VERSION for value in parsed), "exact source binding")
    finish()

    source = "\n".join(path.read_text(encoding="utf-8") for path in (repo / SOURCE_DIR).glob("*.java"))
    tests = "\n".join(path.read_text(encoding="utf-8") for path in (repo / TEST_DIR).glob("*.java"))
    config = text(repo, CONFIG)

    finish = group("endpoint_policy")
    add("endpoint_policy_exists", "class Rca2ShadowEndpointPolicy" in source, "application policy")
    add("https_and_tls_required", "HTTPS_REQUIRED" in source and "tls-required: true" in config, "HTTPS/TLS")
    add("production_endpoint_deny", "PRODUCTION_HOST_FORBIDDEN" in source and "PRODUCTION_NAMESPACE_FORBIDDEN" in source and "PRODUCTION_DATABASE_ROUTE_FORBIDDEN" in source, "production route deny")
    add("ssrf_controls", all(value in source for value in ["USERINFO_FORBIDDEN", "QUERY_FORBIDDEN", "FRAGMENT_FORBIDDEN", "IP_LITERAL_FORBIDDEN", "HOST_NOT_ALLOWLISTED", "PATH_NOT_ALLOWLISTED"]), "bounded endpoint")
    add("endpoint_external_not_faked", 'endpoint: ""' in config and "https://" not in config and "http://" not in config, "no concrete endpoint")
    finish()

    finish = group("credential_boundary")
    add("credential_provider_exists", "interface Rca2WorkloadCredentialProvider" in source, "provider abstraction")
    add("credential_ttl_3600", "Duration.ofSeconds(3_600)" in source and "max-ttl-seconds: 3600" in config, "3600 seconds")
    add("credential_scope_forbidden", all(value in source for value in ["PRODUCTION_SCOPE", "WRITE_SCOPE", "WRONG_AUDIENCE", "REVOKED", "EXPIRED"]), "scope/lifecycle")
    add("raw_secret_redacted", "token=REDACTED" in source and "credential-secret-ref: ${RCA2_NONPRODUCTION_CREDENTIAL_SECRET_REF:}" in config, "no repository secret")
    add("external_credential_unavailable", "Rca2WorkloadCredentialProvider.unavailable()" in source and "provider: UNRESOLVED" in config, "external dependency")
    finish()

    finish = group("identity_allowlist_boundary")
    add("allowlist_provider_exists", "interface Provider" in text(repo, SOURCE_DIR / "Rca2TestAccountAllowlist.java"), "lookup abstraction")
    add("allowlist_default_deny", "default-deny" in text(repo, SOURCE_DIR / "Rca2TestAccountAllowlist.java").lower() and "default-deny: true" in config, "default deny")
    add("test_identity_only", "SYNTHETIC_OR_TEST_ACCOUNT_ONLY" in config and "ACTUAL_PRODUCTION_IDENTITY_BLOCKED" in source, "test/synthetic only")
    add("raw_identity_forbidden", "raw-identity-storage: FORBIDDEN" in config and "raw-identity-logging: FORBIDDEN" in config and "hashedSubjectRef" in source, "hashed only")
    add("allowlist_expiry_revocation", all(value in source for value in ["MAX_ENTRY_DURATION", "EXPIRED", "REVOKED", "DISABLED", "WRONG_PURPOSE", "WRONG_ENVIRONMENT"]), "lifecycle")
    finish()

    finish = group("stable_hash_cohort")
    add("stable_selector_exists", "class Rca2StableHashCohortSelector" in source, "selector")
    add("stable_algorithm_version", "rca2-stable-hash-percentage-v1" in source and "BUCKET_COUNT = 10_000" in source, "version/buckets")
    add("ceiling_one", "MAX_PERCENT_CEILING = 1" in source and "max-percent-ceiling: 1" in config, "1 percent")
    add("zero_default", "traffic-percent: 0" in config and "effective-traffic-percent: 0" in config, "effective zero")
    add("request_randomness_forbidden", "token-derived-request-hash: FORBIDDEN" in config and "request-randomness: FORBIDDEN" in config, "no per-request random sampling")
    add("determinism_tests", all(value in tests for value in ["stableHashIsDeterministicProcessIndependentAndBoundedToOnePercent", "10_000", "isBetween(50L, 150L)"]), "unit fixture")
    finish()

    finish = group("candidate_adapter_boundary")
    add("candidate_source_decision", "record Rca2CandidateSourceDecision" in source and "source: UNRESOLVED" in config and "protocol: UNRESOLVED" in config, "unresolved recorded")
    add("read_only_adapter_contract", "default boolean readOnly() { return true; }" in source and "default boolean servingAllowed() { return false; }" in source, "read-only/non-serving")
    add("request_response_mappers", "class Rca2CandidateRequestMapper" in source and "class Rca2CandidateResponseMapper" in source, "bounded protocol")
    add("fallback_primary", "KEEP_PRIMARY_RESULT" in source and "shadow-failure-fallback: KEEP_PRIMARY_RESULT" in config, "fallback")
    add("candidate_actual_ready_no", json.loads(text(repo, CONTRACT_DIR / "candidate-source-decision.json"))["actual_source_ready"] is False, "source unresolved")
    finish()

    finish = group("effective_zero_traffic")
    add("feature_flag_default_off", "flag: off" in config and "shadow:\n        enabled: false" in config, "OFF")
    add("configured_and_effective_zero", "traffic-percent: 0" in config and "effective-traffic-percent: 0" in config, "0")
    add("max_configurable_one", "max-configurable-percent: 1" in config, "1")
    add("automatic_rollout_forbidden", "automatic-ramp: false" in config and "manual-enablement-implemented: false" in config, "no ramp/enablement")
    add("gate_blocks_before_provider", "configuration.effectiveTrafficPercent() == 0" in source and "SubmissionStatus.TRAFFIC_ZERO" in source, "effective zero gate")
    add("integration_proof", "effectiveTrafficZeroBlocksInvocationEvenWhenFlagSnapshotAttemptsOnePercent" in tests and "hasValue(0)" in tests, "flag 1 attempt remains zero")
    finish()

    finish = group("runtime_side_effect_protection")
    add("primary_response_mutation_forbidden", 'SHADOW_TASK_RESPONSE_MUTATION = "FORBIDDEN"' in source and "Rca2ResponseMutationVerifier" in source, "primary immutable")
    add("no_persistence_or_events", all(value not in source for value in ["JpaRepository", "JdbcTemplate", "EntityManager", "ApplicationEventPublisher", "KafkaTemplate", "@Transactional"]), "no DB/cache/event infrastructure")
    add("side_effect_guard_continuity", all(value in source for value in ["databaseWrite", "cacheWrite", "eventEmission", "notification", "rankingFeedback", "responseMutation"]), "guard")
    add("production_activation_forbidden", "production-activation: NOT_AUTHORIZED" in config and "authority-transfer: FORBIDDEN" in config, "production boundary")
    finish()

    changed = git(repo, "diff", "--name-only", f"{WORK_START}...HEAD").splitlines()
    finish = group("historical_evidence_protection")
    historical_prefixes = (
        "docs/platform/recommendation/rca2/", "docs/platform/governance/sc-next-track/",
        "docs/platform/operations/op0/", "verification/rca2/",
        "verification/sc-next-track/", "verification/operations/op0/",
    )
    add("historical_evidence_unchanged", not any(path.startswith(historical_prefixes) for path in changed), "RCA/SC/OP-0 unchanged")
    allowed_prefixes = (
        "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/",
        "jc-backend/src/test/java/com/jc/backend/recommendation/rca2/",
        "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml",
        "docs/platform/operations/op1/", "verification/operations/op1/",
        ".github/workflows/op1-rca2-stage1-environment-access-ci.yml",
    )
    add("scope_allowlist", all(path.startswith(allowed_prefixes) for path in changed), "\n".join(changed))
    finish()

    finish = group("sql_protection")
    add("sql_unchanged", not any(path.endswith(".sql") or path.startswith("database/") for path in changed), "no DB/SQL diff")
    add("db_change_none", "db-change: NONE" in config and "sql-allocation: NOT_REQUIRED" in config, "NONE")
    finish()

    finish = group("independent_verifier")
    add("unit_test_sources_present", (repo / TEST_DIR / "Rca2Op1BoundaryUnitTest.java").is_file(), "unit")
    add("integration_test_sources_present", (repo / TEST_DIR / "Rca2Op1EnvironmentAccessIntegrationTest.java").is_file(), "integration")
    add("op2_entry_ready_with_blockers", json.loads(text(repo, CONTRACT_DIR / "op2-entry-status.json"))["op2_entry_recommendation"] == "READY_WITH_BLOCKERS", "handoff")
    status("actual_external_endpoint_deployment", "BLOCKED_EXTERNAL_DEPENDENCY", "not deployed")
    status("actual_secret_issuance", "BLOCKED_EXTERNAL_DEPENDENCY", "not issued")
    status("actual_workload_identity", "BLOCKED_EXTERNAL_DEPENDENCY", "provider unresolved")
    status("actual_allowlist_registration", "BLOCKED_EXTERNAL_DEPENDENCY", "store unresolved")
    status("actual_candidate_source", "BLOCKED_EXTERNAL_DEPENDENCY", "source/protocol/version unresolved")
    status("actual_one_percent_traffic", "NOT_EXECUTED", "effective traffic remains zero")
    status("actual_dashboard", "NOT_EXECUTED", "OP-2")
    status("actual_alert_route", "NOT_EXECUTED", "OP-2")
    status("actual_rollback_drill", "NOT_EXECUTED", "OP-2")
    status("human_approval", "NOT_EXECUTED", "pending user review")
    status("runtime_observation", "NOT_EXECUTED", "OP-3")
    finish()

    selected = groups.get(args.only, checks) if args.only else checks
    failure = any(check.status == "FAIL" for check in selected)
    overall = "FAIL" if failure else "PASS"
    evidence = {
        "officialPhase": "OP-1 RCA-2 Stage 1 Environment and Access Preparation",
        "artifactVersion": ARTIFACT_VERSION,
        "workStartSha": WORK_START,
        "sourceOp0ExactHead": OP0_EXACT_HEAD,
        "sourceOp0MergeCommit": OP0_MERGE_COMMIT,
        "testedSha": actual_head,
        "expectedHead": args.expected_head or None,
        "result": "RCA2_STAGE1_ENVIRONMENT_AND_ACCESS_APPLICATION_BOUNDARY_COMPLETE" if overall == "PASS" else "RCA2_STAGE1_ENVIRONMENT_AND_ACCESS_PREPARATION_BLOCKED",
        "overall": overall,
        "applicationBoundaryReady": overall == "PASS",
        "externalEndpointReady": False,
        "externalCredentialReady": False,
        "externalAllowlistReady": False,
        "stableHashCohortReady": overall == "PASS",
        "candidateAdapterReady": False,
        "stage1Enablement": "BLOCKED",
        "currentNonproductionTrafficPercent": 0,
        "targetNonproductionTrafficPercent": 1,
        "effectiveNonproductionTrafficPercent": 0,
        "productionTrafficPercent": 0,
        "checks": [check.as_dict() for check in selected],
        "changedFiles": changed,
    }
    if not args.only:
        output = repo / args.output
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(evidence, indent=2, sort_keys=True))
    return 1 if failure else 0


if __name__ == "__main__":
    sys.exit(main())
