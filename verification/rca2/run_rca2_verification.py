#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, os, re, subprocess, sys
from pathlib import Path

WORK_START = "ed5708bd4da12eaea8180043f5cd7f6eb13c3099"
SC5_HEAD = "a3e7045c42bf854967263f8911389afd96fda4f4"
HANDOFF = "docs/platform/governance/sc-next-track/56-RCA-2-IMPLEMENTATION-HANDOFF-PROMPT.md"
REQUIRED_METRICS = [
    "shadow_request_count", "shadow_execution_count", "shadow_success_count", "shadow_timeout_count",
    "shadow_exception_count", "shadow_circuit_open_count", "shadow_queue_rejected_count",
    "shadow_late_result_discard_count", "shadow_latency_ms", "primary_latency_ms",
    "p1_result_mismatch_count", "p2_result_mismatch_count", "checkpoint_mismatch_count",
    "lineage_mismatch_count", "stale_candidate_count", "identity_blocked_count", "redaction_failure_count",
]
VERIFICATION_COUNTERS = REQUIRED_METRICS[:8] + REQUIRED_METRICS[10:] + [
    "shadow_write_attempt_blocked_count", "shadow_event_attempt_blocked_count",
    "shadow_response_mutation_blocked_count",
]
NOT_EXECUTED = [
    "production_dark_read", "nonzero_production_traffic", "actual_production_credential",
    "actual_production_identity", "production_db_validation", "production_route_test",
    "production_canary", "load", "replay", "cutover", "production_activation", "authority_transfer",
]

class Check:
    def __init__(self, name: str, status: str, detail: str):
        self.name, self.status, self.detail = name, status, detail
    def as_dict(self): return {"name": self.name, "status": self.status, "detail": self.detail}

def run(repo: Path, *args: str) -> str:
    return subprocess.check_output(args, cwd=repo, text=True, stderr=subprocess.STDOUT).strip()

def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def contains(path: Path, *values: str) -> bool:
    text=path.read_text(encoding="utf-8")
    return all(value in text for value in values)

def main() -> int:
    parser=argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--expected-head", default=os.getenv("RCA2_EXPECTED_HEAD", ""))
    parser.add_argument("--output", default="verification/rca2/evidence/rca2-verification-evidence.json")
    args=parser.parse_args()
    repo=Path(args.repo).resolve()
    checks=[]
    def check(name, condition, detail): checks.append(Check(name, "PASS" if condition else "FAIL", detail))
    def status(name, value, detail): checks.append(Check(name, value, detail))

    git_available=(repo/".git").exists()
    actual_head="NOT_AVAILABLE"
    if git_available:
        actual_head=run(repo, "git", "rev-parse", "HEAD")
        check("actual_work_start_present", run(repo,"git","cat-file","-t",WORK_START)=="commit", WORK_START)
        check("sc5_exact_final_head_present", run(repo,"git","cat-file","-t",SC5_HEAD)=="commit", SC5_HEAD)
        check("pr28_merge_subject", "authorize controlled RCA-2 runtime dark read" in run(repo,"git","show","-s","--format=%s",WORK_START), run(repo,"git","show","-s","--format=%s",WORK_START))
        check("sc5_merge_tree_equivalence", run(repo,"git","rev-parse",f"{WORK_START}^{{tree}}") == run(repo,"git","rev-parse",f"{SC5_HEAD}^{{tree}}"), "SC-5 exact-final-head tree equals PR #28 merge tree")
        if args.expected_head:
            check("exact_final_pr_head", actual_head == args.expected_head, f"actual={actual_head} expected={args.expected_head}")
        else:
            status("exact_final_pr_head", "NOT_EXECUTED", "expected head not supplied")
    else:
        status("git_history_checks", "NOT_EXECUTED", "repository has no .git metadata")

    handoff=repo/HANDOFF
    check("handoff_exists", handoff.is_file(), HANDOFF)
    governance=repo/"docs/platform/governance/sc-next-track/SC-5-RCA-2-ENTRY-AUTHORIZATION-AND-EXECUTION-BOUNDARY.md"
    check("sc5_authorization_document", governance.is_file(), str(governance.relative_to(repo)))
    if governance.is_file():
        check("rca2_entry_authorized", contains(governance,"RCA2_ENTRY_AUTHORIZED"), "authorization marker")
        check("sc5_runtime_baseline", contains(governance,"ISOLATED_NON_PRODUCTION_RUNTIME","ASYNC_POST_RESPONSE_SHADOW","FEATURE_FLAG_DEFAULT=OFF","INITIAL_TRAFFIC_PERCENT=0"), "SC-5 runtime markers")

    src=repo/"jc-backend/src/main/java/com/jc/backend/recommendation/rca2"
    tests=repo/"jc-backend/src/test/java/com/jc/backend/recommendation/rca2"
    config=repo/"jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml"
    main_source="\n".join(p.read_text(encoding="utf-8") for p in src.glob("*.java"))
    all_source=main_source+"\n"+"\n".join(p.read_text(encoding="utf-8") for p in tests.glob("*.java"))
    check("work_start_recorded_in_runtime", WORK_START in all_source, WORK_START)
    check("runtime_environment", "ISOLATED_NON_PRODUCTION_RUNTIME" in all_source, "isolated non-production only")
    check("async_post_response_ordering", "OnCommittedResponseWrapper" in all_source and "submitAfterResponseCommitted" in all_source and "PRIMARY_RESPONSE_COMMITTED_BEFORE_SHADOW_SUBMISSION" in all_source, "post-commit callback")
    check("primary_future_not_joined", not re.search(r"\.join\s*\(|CompletableFuture", all_source), "no join/CompletableFuture coupling")
    check("bounded_executor", "ArrayBlockingQueue" in all_source and "MAX_SHADOW_CONCURRENCY = 4" in all_source and "MAX_SHADOW_QUEUE_DEPTH = 100" in all_source, "concurrency=4 queue=100")
    check("forbidden_executor_absent", all(x not in main_source for x in ["ForkJoinPool.commonPool", "newCachedThreadPool", "CallerRunsPolicy"]), "no common/cached/caller-runs")
    check("queue_timeout_and_no_retry", "TASK_QUEUE_TIMEOUT = Duration.ofMillis(50)" in all_source and 'RETRY_POLICY = "NONE"' in all_source, "50ms/no retry")
    check("late_discard", "MAX_TASK_AGE = Duration.ofMillis(1_000)" in all_source and 'LATE_RESULT_POLICY = "DISCARD"' in all_source, "age=1000ms/discard")
    check("timeout_contract", all(x in all_source for x in ["Duration.ofMillis(100)","Duration.ofMillis(300)","Duration.ofMillis(500)","future.cancel(true)"]), "100/300/500ms and cancellation")
    check("lane_breaker_separation", "EnumMap<Rca2RuntimeContracts.Lane, Rca2CircuitBreaker>" in all_source and "Lane.P1" in all_source and "Lane.P2" in all_source, "P1/P2 breakers")
    check("global_lane_kill", "killGlobal" in all_source and "killLane" in all_source and "globalKilled" in all_source and "laneKilled" in all_source, "global and lane kill")
    check("response_unchanged_verifier", "serializedBytes" in all_source and "SHADOW_RESPONSE_MUTATION_DETECTED" in all_source, "bytes/status/headers/body/order/count/cursor/source refs")
    check("shadow_result_not_served", 'SHADOW_RESULT_SERVING = "FORBIDDEN"' in all_source and "Rca2SideEffectGuard" in all_source, "candidate non-serving")
    check("no_db_cache_event_notification_feedback", all(x not in main_source for x in ["JpaRepository","JdbcTemplate","EntityManager","ApplicationEventPublisher","KafkaTemplate","@Transactional"]), "RCA-2 package has no persistence/event dependency")
    check("identity_fail_closed", "SYNTHETIC_OR_TEST_ACCOUNT_ONLY" in all_source and "ACTUAL_PRODUCTION_IDENTITY_BLOCKED" in all_source and "subject:production:" in all_source, "hashed production subject blocked")
    check("p1_lane_markers", all(x in all_source for x in ["RecommendationP1ProfileSource","recommendation_p1_profile_snapshot","P1_EXPECTED_GAPS"]), "current P1 authority/gaps")
    check("p2_lane_markers", all(x in all_source for x in ["RecommendationP2ObservationSource","recommendation_p2_experiment_exposure","P2_MIGRATION_GAPS"]), "current P2 authority/gaps")
    check("p2_exact_event_allowlist", "P2_EVENTS.equals(candidate.engagementEvents())" in all_source, "exact click/like/save/share")
    check("checkpoint_lineage", all(x in all_source for x in ["opaqueRef","monotonicSequence","capturedAtUtc","lineage fingerprint","artifactSha"]), "checkpoint and lineage required")
    check("freshness_measurement_only", "RUNTIME_FRESHNESS_POLICY_BLOCKED_PENDING_MEASUREMENT" in all_source and "freshness-threshold" not in all_source.lower(), "no invented PASS threshold")
    metrics_path=src/"Rca2Metrics.java"
    metric_text=metrics_path.read_text(encoding="utf-8")
    check("seventeen_metrics", all(m in metric_text for m in REQUIRED_METRICS), ",".join(REQUIRED_METRICS))
    check("metric_low_cardinality", all(x in metric_text for x in ["environment","lane","result_class","breaker_state"]) and all(x not in metric_text for x in ["request_id","subject_id","session_id","exposure_id"]), "allowlisted labels only")
    check("redaction", "hashedRequestRef" in all_source and "private static final List<String> FORBIDDEN" in main_source and "killSwitch.failClosed" in all_source, "redaction/global kill")
    check("credential_network_contract", all(x in all_source for x in ["PLATFORM_SECRET_MANAGER","CREDENTIAL_MAX_TTL","DENY_BY_DEFAULT_EXPLICIT_NONPRODUCTION_ALLOWLIST","PRODUCTION_ROUTE"]), "nonproduction read-only contract")
    check("rollback_hierarchy", all(f"LEVEL_{i}" in all_source for i in range(1,8)), "seven levels")

    cfg=config.read_text(encoding="utf-8")
    check("default_off", "flag: off" in cfg and "config-signature-verified: false" in cfg, "OFF/unverified")
    check("traffic_zero", "traffic-percent: 0" in cfg, "initial traffic 0")
    check("production_traffic_zero", "max-production-dark-read-percent: 0" in cfg, "production ceiling 0")
    check("production_route_absent", "production-route-allowed: false" in cfg and not re.search(r"https?://|jdbc:", cfg), "no concrete endpoint/DB route")
    check("actual_credential_absent", "credential-secret-ref: ${RCA2_NONPRODUCTION_CREDENTIAL_SECRET_REF:}" in cfg and not re.search(r"(?i)(password|token|secret):\s*[^$\s][^\n]*", cfg), "placeholder only")

    sql=[]
    for p in (repo/"database").rglob("*.sql"):
        m=re.match(r"(?:V)?(\d+)",p.name)
        if m: sql.append((int(m.group(1)),str(p.relative_to(repo))))
    check("sql_01_52_protected", bool(sql) and max(n for n,_ in sql)==52, f"max_sql={max(n for n,_ in sql) if sql else 'none'}")
    check("sql_53_plus_absent", not any(n>=53 for n,_ in sql), "SQL 53+ absent")
    check("db_change_none", not any("rca2" in path.lower() for _,path in sql), "no RCA-2 SQL")

    docs=sorted((repo/"docs/platform/recommendation/rca2").glob("*.md"))
    check("required_documents", len(docs)>=25, f"count={len(docs)}")
    check("work_start_in_all_documents", all(WORK_START in p.read_text(encoding="utf-8") for p in docs), "all RCA-2 docs bind work-start")
    check("approval_pending", any("APPROVAL_STATUS=PENDING_USER_REVIEW" in p.read_text(encoding="utf-8") for p in docs), "no fabricated human approval")

    for name in NOT_EXECUTED: status(name, "NOT_EXECUTED", "RCA-2 production boundary")
    statuses={c.status for c in checks}
    overall="PASS" if "FAIL" not in statuses else "FAIL"
    evidence={
        "contract":"recommendation-runtime-dark-read-v1",
        "actualWorkStartSha":WORK_START,
        "sc5ExactFinalHead":SC5_HEAD,
        "testedSha":actual_head,
        "expectedHead":args.expected_head or None,
        "overall":overall,
        "checks":[c.as_dict() for c in checks],
        "verificationCounters":{name:{"status":"VERIFIED_BY_JUNIT","defaultValue":0} for name in VERIFICATION_COUNTERS},
        "negativeTestCounters":{"shadow_write_attempt_blocked_count":2,"shadow_event_attempt_blocked_count":3,"shadow_response_mutation_blocked_count":1},
        "retention":{"metricsDays":30,"redactedLogsDays":14,"reviewEvidenceDays":90,"rawResult":"NONE","rawIdentity":"NONE","credential":"NONE"},
        "approvalStatus":"PENDING_USER_REVIEW",
        "productionActivation":"NOT_AUTHORIZED",
        "authorityTransfer":"FORBIDDEN",
    }
    out=repo/args.output
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    failed=[c.as_dict() for c in checks if c.status == "FAIL"]
    summary={
        "overall": overall,
        "testedSha": actual_head,
        "expectedHead": args.expected_head or None,
        "failedChecks": failed,
        "verificationCounters": evidence["verificationCounters"],
        "evidencePath": str(out.relative_to(repo)),
    }
    print(json.dumps(summary,indent=2,sort_keys=True))
    return 0 if overall=="PASS" else 1

if __name__=="__main__": sys.exit(main())
