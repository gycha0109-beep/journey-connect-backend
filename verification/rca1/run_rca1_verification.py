#!/usr/bin/env python3
from __future__ import annotations
import argparse,csv,json,re,shutil,subprocess,sys,tempfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
START="5a0ca52c8226a0f4a6e21f9af96c7da0732c8d5b"
SC3="e7f47f1e031a19cdf383f409235cad11c9209e83"
PKG=ROOT/"jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption/reconciliation"
RUNNER=ROOT/"verification/rca1/java/com/jc/backend/recommendation/dataadoption/reconciliation/Rca1ReconciliationTestMain.java"
P1=ROOT/"jc-backend/src/test/resources/recommendation-data-adoption/reconciliation/p1-reconciliation-fixtures-v1.tsv"
P2=ROOT/"jc-backend/src/test/resources/recommendation-data-adoption/reconciliation/p2-reconciliation-fixtures-v1.tsv"
RCA0P1=ROOT/"jc-backend/src/test/resources/recommendation-data-adoption/p1-fixtures-v1.tsv"
RCA0P2=ROOT/"jc-backend/src/test/resources/recommendation-data-adoption/p2-fixtures-v1.tsv"
OUT=ROOT/"verification/rca1/runtime"
DOC_NAMES=[
"RCA-1-IMPLEMENTATION-REPORT.md","RCA-1-EXECUTION-MODEL-IMPLEMENTATION-REPORT.md",
"RCA-1-P1-SHADOW-RECONCILIATION-RESULT.md","RCA-1-P1-MISMATCH-INVENTORY.md",
"RCA-1-P2-SHADOW-RECONCILIATION-RESULT.md","RCA-1-P2-MISMATCH-INVENTORY.md",
"RCA-1-IDENTITY-PRIVACY-ENFORCEMENT.md","RCA-1-EVIDENCE-SCHEMA-AND-REDACTION.md",
"RCA-1-PROTECTED-AUTHORITY-REPORT.md","RCA-1-VERIFICATION-SUMMARY.md",
"RCA-1-WORKLOG.md","RCA-1-EXIT-AND-RCA1B-RCA2-HANDOFF.md"]
SECTIONS={"Scope","Current Baseline","Implementation","Authority","Dependencies","Allowed Changes","Forbidden Changes","Identity/Privacy","Comparison Dimensions","Evidence","Verification","Compatibility","Risks","Exit Criteria","Handoff"}
IDS={"recommendation-shadow-reconciliation-v1","recommendation-shadow-reconciliation-evidence-v1","recommendation-shadow-reconciliation-fixture-v1"}
P1_DIMS={"EXACT_FIELD_PARITY","DERIVED_VALUE_PARITY","AGGREGATE_WINDOW_PARITY","ORDERING_NOT_COMPARABLE","EVENT_GRAIN_MISSING","EXPLICIT_PREFERENCE_MISSING","TRANSFORM_POLICY_MISSING","FINGERPRINT_SEMANTICS_PROTECTED","IDENTITY_BLOCKED"}
P2_DIMS={"EXPOSURE_REFERENCE_PARITY","ASSIGNMENT_PARITY","SUBJECT_SESSION_RUN_PARITY","OUTCOME_WINDOW_PARITY","ENGAGEMENT_EVENT_PARITY","FALLBACK_BINDING_PARITY","STALE_UNEXPOSED_ASSIGNMENT_GAP","OBSERVATION_DEDUPE_GAP","CANONICAL_DATASET_HASH_PROTECTED","RELEASE_EVIDENCE_PROTECTED","IDENTITY_BLOCKED"}
CLASSIFICATIONS={"MATCH_EXACT","MATCH_DERIVED","EXPECTED_SEMANTIC_GAP","MIGRATION_REQUIRED","IDENTITY_MAPPING_REQUIRED","IDENTITY_SCHEME_MISMATCH","SOURCE_CHECKPOINT_MISMATCH","SOURCE_STALE","LINEAGE_MISMATCH","EXPOSURE_AUTHORITY_MISMATCH","OUTCOME_WINDOW_MISMATCH","FALLBACK_BINDING_MISMATCH","PROTECTED_AUTHORITY_DIFFERENCE","RECONCILIATION_INCONCLUSIVE"}
FORBIDDEN={"RUNTIME_READY","PRODUCTION_READY","CUTOVER_READY","AUTHORITATIVE","AUTHORITY_TRANSFERRED"}
RCA0_P1={
"p1_valid_7":"CONDITIONALLY_COMPATIBLE","p1_valid_30":"CONDITIONALLY_COMPATIBLE","p1_valid_90":"CONDITIONALLY_COMPATIBLE",
"p1_unsupported_schema_version":"UNSUPPORTED_CONTRACT_VERSION","p1_invalid_activity_window":"INCOMPATIBLE_REQUIRED_ENUM",
"p1_missing_subject":"INCOMPATIBLE_REQUIRED_FIELD","p1_missing_checkpoint":"INCOMPATIBLE_REQUIRED_FIELD","p1_missing_lineage":"INCOMPATIBLE_REQUIRED_FIELD",
"p1_aggregate_to_event_stream_rejected":"PROTECTED_AUTHORITY_CHANGE_REQUIRED","p1_explicit_preference_missing":"MIGRATION_REQUIRED",
"p1_identity_mapping_missing":"IDENTITY_MAPPING_REQUIRED","p1_identity_scheme_mismatch":"IDENTITY_SCHEME_MISMATCH"}
RCA0_P2={
"p2_valid_exact_exposure_outcome":"COMPATIBLE_FOR_FIXTURE_VALIDATION","p2_click_only":"COMPATIBLE_FOR_FIXTURE_VALIDATION",
"p2_like_only":"COMPATIBLE_FOR_FIXTURE_VALIDATION","p2_save_only":"COMPATIBLE_FOR_FIXTURE_VALIDATION","p2_share_only":"COMPATIBLE_FOR_FIXTURE_VALIDATION",
"p2_combined_engagement":"COMPATIBLE_FOR_FIXTURE_VALIDATION","p2_non_p2_exposure_rejected":"EXPOSURE_AUTHORITY_MISMATCH",
"p2_behavior_impression_rejected":"EXPOSURE_AUTHORITY_MISMATCH","p2_view_rejected":"INCOMPATIBLE_REQUIRED_ENUM","p2_hide_rejected":"INCOMPATIBLE_REQUIRED_ENUM",
"p2_report_rejected":"INCOMPATIBLE_REQUIRED_ENUM","p2_outcome_window_mismatch":"OUTCOME_WINDOW_MISMATCH",
"p2_unbound_fallback_rejected":"PROTECTED_AUTHORITY_CHANGE_REQUIRED","p2_subject_mismatch_rejected":"IDENTITY_SCHEME_MISMATCH",
"p2_session_mismatch_rejected":"INCOMPATIBLE_REQUIRED_FIELD","p2_run_mismatch_rejected":"INCOMPATIBLE_REQUIRED_FIELD",
"p2_exposure_mismatch_rejected":"EXPOSURE_AUTHORITY_MISMATCH","p2_stale_assignment_migration":"MIGRATION_REQUIRED",
"p2_dataset_hash_migration":"MIGRATION_REQUIRED","p2_identity_mapping_missing":"IDENTITY_MAPPING_REQUIRED","p2_identity_scheme_mismatch":"IDENTITY_SCHEME_MISMATCH"}


def sh(cmd:list[str],check:bool=True)->str:
    return subprocess.run(cmd,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=check).stdout

def need(ok:bool,message:str)->None:
    if not ok: raise AssertionError(message)

def rows(path:Path)->list[dict[str,str]]:
    with path.open(encoding="utf-8",newline="") as handle:
        reader=csv.DictReader(handle,delimiter="\t"); data=list(reader)
    need(bool(reader.fieldnames) and bool(data),f"invalid TSV {path}")
    signatures=[tuple(row.get(field,"") for field in reader.fieldnames) for row in data]
    need(len(signatures)==len(set(signatures)),f"duplicate TSV row {path}")
    return data

def enum_values(text:str,name:str)->set[str]:
    match=re.search(rf"enum\s+{re.escape(name)}\s*\{{(.*?)\}}",text,re.S)
    need(match is not None,f"enum {name} missing")
    return {part.strip() for part in match.group(1).split(",") if part.strip()}

def java_run(head:str)->tuple[str,str]:
    build=Path(tempfile.mkdtemp(prefix="rca1-"))
    try:
        sources=sorted(str(path) for path in PKG.glob("*.java"))+[str(RUNNER)]
        compile_cmd=["javac","--release","21","-Xlint:all","-Werror","-d",str(build),*sources]
        compile_out=sh(compile_cmd)
        run_cmd=["java","-cp",str(build),"com.jc.backend.recommendation.dataadoption.reconciliation.Rca1ReconciliationTestMain",str(P1),str(P2),str(OUT),head]
        run_out=sh(run_cmd)
        return " ".join(compile_cmd)+" && "+" ".join(run_cmd),compile_out+run_out
    finally: shutil.rmtree(build,ignore_errors=True)

def main()->int:
    parser=argparse.ArgumentParser(); parser.add_argument("--execute-regressions",action="store_true"); args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True); checks=[]; failures=[]; logs={}; head="UNKNOWN"
    def rec(name,fn,command="NOT_APPLICABLE"):
        try: checks.append({"check":name,"status":"PASS","command":command,"detail":fn() or "verified"})
        except Exception as exc: failures.append(f"{name}: {exc}"); checks.append({"check":name,"status":"FAIL","command":command,"detail":str(exc)})
    try:
        head=sh(["git","rev-parse","HEAD"]).strip()
        sh(["git","fetch","origin","main","--unshallow"],False); sh(["git","fetch","origin","main","--depth=200"],False)
        def baseline():
            need(sh(["git","cat-file","-t",START]).strip()=="commit","RCA1 work-start commit absent")
            need("Merge pull request #24" in sh(["git","show","-s","--format=%B",START]),"SC3 merge absent")
            sh(["git","merge-base","--is-ancestor",SC3,START])
            sh(["git","diff","--quiet",SC3,START])
            sh(["git","merge-base","--is-ancestor",START,head])
            sh(["git","merge-base","--is-ancestor",START,"origin/main"])
            baseline_rows={row["key"]:row["value"] for row in rows(ROOT/"verification/rca1/RCA1_BASELINE.tsv")}
            need(baseline_rows.get("work_start_sha")==START,"baseline SHA")
            return "historical RCA1 work-start, SC3 merge/tree and current ancestry verified"
        rec("actual_authoritative_work_start_and_sc3_merge",baseline,"git main/merge/tree checks")

        def rca0():
            p1={row["scenario"]:row["expected"] for row in rows(RCA0P1)}; p2={row["scenario"]:row["expected"] for row in rows(RCA0P2)}
            need(p1==RCA0_P1,"RCA0 P1 expected classifications changed"); need(p2==RCA0_P2,"RCA0 P2 expected classifications changed")
            source="\n".join(path.read_text(encoding="utf-8") for path in (ROOT/"jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption").glob("*.java"))
            for item in ("recommendation-data-consumer-alignment-v1","recommendation-profile-input-consumer-v1","experiment-outcome-input-consumer-v1","recommendation-data-consumer-fixture-v1"): need(item in source,f"RCA0 contract missing {item}")
            changed=sh(["git","diff","--name-only",f"{START}..{head}"]).splitlines()
            approved_verifier="verification/rca0/run_rca0_verification.py"
            protected=[p for p in changed if (p.startswith("verification/rca0/") and p != approved_verifier) or p.startswith("docs/platform/recommendation/RCA-0-") or p in {str(RCA0P1.relative_to(ROOT)),str(RCA0P2.relative_to(ROOT))}]
            need(not protected,f"RCA0 historical artifacts changed {protected}")
            if approved_verifier in changed:
                verifier=(ROOT/approved_verifier).read_text(encoding="utf-8")
                need("RCA2_NONPRODUCTION_PROFILE" in verifier and "result=sh(cmd,check=False)" in verifier and "production profiles/controls unchanged" in verifier,"RCA0 verifier delta outside approved fail-closed classification/logging fix")
            return "four RCA0 contracts, 12/21 fixtures and classifications unchanged; approved verifier fix structurally verified"
        rec("rca0_contract_fixture_and_evidence_regression",rca0,"read/diff RCA0 baseline")

        def contracts():
            source="\n".join(path.read_text(encoding="utf-8") for path in PKG.glob("*.java"))
            found=set(re.findall(r'"(recommendation-shadow-reconciliation(?:-evidence|-fixture)?-v1)"',source))
            need(found==IDS,f"RCA1 contract IDs {found}")
            need(enum_values(source,"Classification")==CLASSIFICATIONS,"classification taxonomy")
            need(not any(word in source for word in FORBIDDEN),"forbidden result taxonomy in source")
            dimensions=enum_values(source,"Dimension")
            need(P1_DIMS<=dimensions and P2_DIMS<=dimensions,"required dimensions missing")
            return "three IDs and exact allowed taxonomy implemented"
        rec("rca1_contract_dimension_and_taxonomy",contracts,"static Java contract scan")

        def fixtures():
            p1=rows(P1); p2=rows(P2); need(len(p1)==23,"P1 count"); need(len(p2)==39,"P2 count")
            for lane in (p1,p2):
                names={row["scenario"] for row in lane}
                for suffix in ("identity_absent","identity_invalid","identity_expired","identity_deleted","identity_mismatched","unauthorized_purpose","unauthorized_caller"):
                    need(any(name.endswith(suffix) for name in names),f"identity case {suffix}")
            need(not any(word in "\n".join(row["expectedPrimaryClassification"] for row in p1+p2) for word in FORBIDDEN),"forbidden fixture taxonomy")
            return "23 P1, 39 P2, duplicate-free and all identity failures"
        rec("rca1_fixture_inventory",fixtures,"TSV fixture validation")

        def documents():
            for name in DOC_NAMES:
                path=ROOT/"docs/platform/recommendation"/name; need(path.is_file(),f"missing {name}"); text=path.read_text(encoding="utf-8")
                headings=set(re.findall(r"^##\s+(.+)$",text,re.M)); need(SECTIONS<=headings,f"sections missing {name}: {SECTIONS-headings}"); need(START in text,f"work-start missing {name}")
            return "12 RCA1 documents complete"
        rec("required_documents",documents,"document section/SHA checks")

        def protected():
            changed=sh(["git","diff","--name-only",f"{START}..{head}"]).splitlines()
            allowed=(
                ".github/actions/rca2-job/",
                ".github/workflows/rca1-offline-reconciliation-ci.yml",
                ".github/workflows/rca1b-nonproduction-readonly-reconciliation-ci.yml",
                ".github/workflows/rca2-controlled-runtime-dark-read-ci.yml",
                ".github/workflows/sc-baseline-reconciliation.yml",
                "docs/platform/governance/",
                "docs/platform/recommendation/RCA-1-",
                "docs/platform/recommendation/RCA-1B-",
                "docs/platform/recommendation/rca2/",
                "jc-backend/build.gradle.kts",
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java",
                "jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption/reconciliation/",
                "jc-backend/src/main/java/com/jc/backend/recommendation/rca2/",
                "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml",
                "jc-backend/src/test/java/com/jc/backend/recommendation/dataadoption/reconciliation/database/",
                "jc-backend/src/test/java/com/jc/backend/recommendation/rca2/",
                "jc-backend/src/test/java/com/jc/backend/verification/IP9ControlledBackendHookStaticTest.java",
                "jc-backend/src/test/resources/recommendation-data-adoption/reconciliation/",
                "jc-backend/src/test/resources/recommendation-data-adoption/rca1b/",
                "jc-search-readiness/src/test/java/com/jc/intelligence/readiness/search/SearchShadowReadinessContractTest.java",
                "verification/rca0/run_rca0_verification.py",
                "verification/rca1/",
                "verification/rca1b/",
                "verification/rca2/",
                "verification/sc-dp1-baseline-reconciliation/",
                "verification/sc-next-track/",
            )
            unexpected=[item for item in changed if not any(item==prefix or item.startswith(prefix) for prefix in allowed)]
            need(not unexpected,f"unexpected cross-phase diff {unexpected}")
            production_configs=[item for item in changed if re.search(r"jc-backend/src/main/resources/application.*\.(?:yml|yaml|properties)$",item) and item != "jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml"]
            forbidden=[item for item in changed if item.startswith(("database/","jc-recommendation-core/")) or item in ("jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java","jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java")]
            need(not forbidden and not production_configs,f"protected diff {forbidden+production_configs}")
            rca2_config=(ROOT/"jc-backend/src/main/resources/application-rca2-isolated-nonproduction.yml").read_text(encoding="utf-8")
            need("flag: off" in rca2_config and "traffic-percent: 0" in rca2_config and "max-production-dark-read-percent: 0" in rca2_config and "production-route-allowed: false" in rca2_config,"RCA2 isolated profile boundary")
            feed=(ROOT/"jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java").read_text(encoding="utf-8")
            need("RCA-2 request registration failed open" in feed and "return response;" in feed and "return registrar.registerFeed" not in feed,"RCA2 primary authority boundary")
            nums=[int(m.group(1)) for path in (ROOT/"database/journey-connect-db-v2.7").glob("*.sql") if (m:=re.match(r"(\d+)_",path.name))]
            need(set(nums)==set(range(1,53)) and len(nums)==52,"SQL inventory")
            return f"authorized RCA1B/SC/RCA2 phase diff {len(changed)} files; source/core/SQL/production config protected"
        rec("protected_authority_sql_and_config",protected,"git protected diff and SQL inventory")

        def isolation():
            text="\n".join(path.read_text(encoding="utf-8") for path in PKG.glob("*.java"))
            banned=("org.springframework","jakarta.persistence","javax.persistence","JdbcTemplate","DataSource","EntityManager","java.net.http","@Component","@Service","@Repository","@Controller","@Configuration","@Scheduled","System.getenv","System.getProperty","Instant.now","Clock.system","BehaviorProfileEvent")
            need(not [item for item in banned if item in text],"runtime/DB/system dependency found")
            need("recommendation_p2_experiment_exposure" in text and "604_800L" in text,"P2 authority/window marker")
            return "pure Java, no runtime/DB/clock dependency"
        rec("dependency_and_runtime_isolation",isolation,"forbidden-token scan")

        def execute():
            command,output=java_run(head); logs["RCA1_FIXTURE_RUNNER.log"]=output
            need("RCA1_DEPENDENCY_FREE_FIXTURE_RUNNER_PASS" in output,"runner marker")
            need("P1_RESULT=RECONCILED_WITH_EXPECTED_GAPS" in output,"P1 result")
            need("P2_RESULT=RECONCILED_WITH_MIGRATION_GAPS" in output,"P2 result")
            return command
        rec("dependency_free_reconciliation_execution",execute,"javac/java RCA1 runner")

        def evidence():
            required=("RCA1_RECONCILIATION_EVIDENCE.tsv","RCA1_RECONCILIATION_EVIDENCE.json","RCA1_RECONCILIATION_COUNTERS.tsv","RCA1_RECONCILIATION_COUNTERS.json","RCA1_LANE_VERDICTS.tsv","RCA1_P1_MISMATCH_INVENTORY.tsv","RCA1_P2_MISMATCH_INVENTORY.tsv")
            for name in required: need((OUT/name).is_file(),f"missing evidence {name}")
            evidence_rows=rows(OUT/"RCA1_RECONCILIATION_EVIDENCE.tsv"); keys=[(r["hashedCaseId"],r["comparisonDimension"]) for r in evidence_rows]; need(len(keys)==len(set(keys)),"duplicate evidence")
            raw="\n".join((OUT/name).read_text(encoding="utf-8") for name in required)
            for token in ("synthetic-subject:alpha","synthetic-user:42","synthetic-session:alpha","synthetic-run:alpha","user:42","subject:opaque"):
                need(token not in raw,f"raw identity leaked {token}")
            counter={r["counter"]:int(r["value"]) for r in rows(OUT/"RCA1_RECONCILIATION_COUNTERS.tsv")}; need(counter["reconciliation_case_count"]==62,"counter case count")
            need(set(counter)=={"reconciliation_case_count","p1_exact_match_count","p1_expected_gap_count","p1_unexpected_mismatch_count","p2_exact_match_count","p2_migration_required_count","p2_authority_mismatch_count","identity_blocked_count","checkpoint_mismatch_count","lineage_mismatch_count","inconclusive_count"},"counter inventory")
            verdicts={r["lane"]:r["verdict"] for r in rows(OUT/"RCA1_LANE_VERDICTS.tsv")}; need(verdicts=={"P1":"RECONCILED_WITH_EXPECTED_GAPS","P2":"RECONCILED_WITH_MIGRATION_GAPS"},"lane verdicts")
            need(all(r["testedSha"]==head for r in evidence_rows),"tested SHA evidence")
            return "deterministic JSON/TSV, redaction, counters and independent lane verdicts"
        rec("evidence_redaction_counters_and_lane_results",evidence,"generated evidence validation")

        if args.execute_regressions:
            def regression():
                command=[sys.executable,str(ROOT/"verification/rca0/run_rca0_verification.py"),"--execute-regressions"]
                output=sh(command); logs["RCA0_CORE_BACKEND_REGRESSION.log"]=output
                need('"result": "PASS"' in output,"RCA0 regression result")
                return "RCA0 verifier + recommendation core + backend test passed"
            rec("rca0_recommendation_core_and_backend_regressions",regression,"python verification/rca0/run_rca0_verification.py --execute-regressions")
        else:
            checks.append({"check":"rca0_recommendation_core_and_backend_regressions","status":"NOT_EXECUTED","command":"python verification/rca0/run_rca0_verification.py --execute-regressions","detail":"use --execute-regressions"})
    except Exception as exc:
        failures.append(f"verifier_internal: {exc}")
    for name in ("postgresql_reconciliation","runtime_dark_read","canary","load","replay","production_validation","actual_user_identity_mapping"):
        checks.append({"check":name,"status":"NOT_APPLICABLE","command":"NOT_APPLICABLE","detail":"RCA1 Model A offline synthetic-only scope"})
    summary={"contractId":"recommendation-shadow-reconciliation-v1","workStartSha":START,"sc3ExactFinalHead":SC3,"testedSha":head,"result":"PASS" if not failures else "FAIL","checks":checks,"failures":failures}
    (OUT/"RCA1_VERIFICATION_SUMMARY.json").write_text(json.dumps(summary,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    with (OUT/"RCA1_VERIFICATION_SUMMARY.tsv").open("w",encoding="utf-8",newline="") as handle:
        writer=csv.writer(handle,delimiter="\t",lineterminator="\n"); writer.writerow(["check","status","command","detail","tested_sha"])
        for check in checks: writer.writerow([check["check"],check["status"],check["command"],check["detail"],head])
    for name,value in logs.items(): (OUT/name).write_text(value,encoding="utf-8")
    print(json.dumps(summary,indent=2,sort_keys=True)); return 1 if failures else 0

if __name__=="__main__": sys.exit(main())
