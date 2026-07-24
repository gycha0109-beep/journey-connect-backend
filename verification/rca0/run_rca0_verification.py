#!/usr/bin/env python3
import argparse,csv,json,re,shutil,subprocess,sys,tempfile
from pathlib import Path

START="a89dd336cfdd20f650eac4aee8dd2db8de8f3c04"
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/"verification/rca0/runtime"
PKG=ROOT/"jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption"
MAIN=ROOT/"verification/rca0/java/com/jc/backend/recommendation/dataadoption/Rca0ContractTestMain.java"
F1=ROOT/"jc-backend/src/test/resources/recommendation-data-adoption/p1-fixtures-v1.tsv"
F2=ROOT/"jc-backend/src/test/resources/recommendation-data-adoption/p2-fixtures-v1.tsv"
D1=ROOT/"jc-data-contracts/src/main/java/com/jc/data/contract/v1/projection/RecommendationProfileInputProjection.java"
D2=ROOT/"jc-data-contracts/src/main/java/com/jc/data/contract/v1/projection/ExperimentOutcomeInputProjection.java"
S1="jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java"
S2="jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java"
IDS={"recommendation-data-consumer-alignment-v1","recommendation-profile-input-consumer-v1","experiment-outcome-input-consumer-v1","recommendation-data-consumer-fixture-v1"}
P1={"p1_valid_7","p1_valid_30","p1_valid_90","p1_unsupported_schema_version","p1_invalid_activity_window","p1_missing_subject","p1_missing_checkpoint","p1_missing_lineage","p1_aggregate_to_event_stream_rejected","p1_explicit_preference_missing","p1_identity_mapping_missing","p1_identity_scheme_mismatch"}
P2={"p2_valid_exact_exposure_outcome","p2_click_only","p2_like_only","p2_save_only","p2_share_only","p2_combined_engagement","p2_non_p2_exposure_rejected","p2_behavior_impression_rejected","p2_view_rejected","p2_hide_rejected","p2_report_rejected","p2_outcome_window_mismatch","p2_unbound_fallback_rejected","p2_subject_mismatch_rejected","p2_session_mismatch_rejected","p2_run_mismatch_rejected","p2_exposure_mismatch_rejected","p2_stale_assignment_migration","p2_dataset_hash_migration","p2_identity_mapping_missing","p2_identity_scheme_mismatch"}
DOCS=["RCA-0-IMPLEMENTATION-REPORT.md","RCA-0-P1-CONSUMER-COMPATIBILITY-MATRIX.md","RCA-0-P2-OUTCOME-COMPATIBILITY-MATRIX.md","RCA-0-IDENTITY-PRIVACY-BOUNDARY.md","RCA-0-PROTECTED-AUTHORITY-REPORT.md","RCA-0-VERIFICATION-SUMMARY.md","RCA-0-HANDOFF.md"]
SECTIONS={"Scope","Current Baseline","Contract Impact","Authority","Dependencies","Allowed Changes","Forbidden Changes","Verification","Compatibility","Risks","Handoff"}

def sh(cmd,check=True): return subprocess.run(cmd,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=check)
def git(*a): return sh(["git",*a]).stdout.strip()
def need(ok,msg):
    if not ok: raise AssertionError(msg)
def fixture(path):
    with path.open(encoding="utf-8",newline="") as f:
        rows=list(csv.DictReader(f,delimiter="\t"))
    need(rows and list(rows[0])==["scenario","expected","fields"],f"bad fixture header {path}")
    names=[r["scenario"] for r in rows]; need(len(names)==len(set(names)),f"duplicate fixture {path}")
    return set(names),[r["expected"] for r in rows]
def fields(path,names):
    t=path.read_text(encoding="utf-8"); missing=sorted(x for x in names if not re.search(rf"\b{re.escape(x)}\b",t)); need(not missing,f"missing fields {missing}")
def java_run():
    build=Path(tempfile.mkdtemp(prefix="rca0-"))
    try:
        src=sorted(map(str,PKG.glob("*.java")))+[str(MAIN)]
        c=["javac","--release","21","-Xlint:all","-Werror","-d",str(build),*src]; a=sh(c)
        r=["java","-cp",str(build),"com.jc.backend.recommendation.dataadoption.Rca0ContractTestMain",str(F1),str(F2)]; b=sh(r)
        return " ".join(c)+" && "+" ".join(r),a.stdout+b.stdout
    finally: shutil.rmtree(build,ignore_errors=True)
def baseline():
    with (ROOT/"verification/rca0/RCA0_BASELINE.tsv").open(encoding="utf-8") as f:
        for r in csv.DictReader(f,delimiter="\t"):
            if r["key"]=="work_start_sha": return r["value"]
    raise AssertionError("work_start_sha absent")

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--execute-regressions",action="store_true"); args=ap.parse_args()
    OUT.mkdir(parents=True,exist_ok=True); checks=[]; failures=[]; logs={}; head="UNKNOWN"
    def rec(name,fn,command="NOT_APPLICABLE"):
        try: checks.append({"name":name,"status":"PASS","command":command,"detail":fn() or "verified"})
        except Exception as e: failures.append(f"{name}: {e}"); checks.append({"name":name,"status":"FAIL","command":command,"detail":str(e)})
    try:
        head=git("rev-parse","HEAD")
        rec("exact_authoritative_work_start_sha",lambda:(need(baseline()==START,"SHA mismatch") or "exact SHA recorded"))
        rec("work_start_is_ancestor",lambda:(sh(["git","merge-base","--is-ancestor",START,head]).stdout or "ancestor verified"),f"git merge-base --is-ancestor {START} {head}")
        rec("contract_id_registration",lambda:(need(IDS<=set(re.findall(r'"([a-z0-9-]+-v1)"',"\n".join(p.read_text() for p in PKG.glob("*.java")))),"contract ID missing") or "four IDs registered"))
        changed=git("diff","--name-only",f"{START}..{head}").splitlines()
        rec("rp_naming_conflict_absent",lambda:(need(not re.search(r"RecommendationPlatform|RP\s*(?:=|:|means)\s*Recommendation","\n".join((ROOT/p).read_text(errors="ignore") for p in changed if (ROOT/p).is_file()),re.I),"RP naming conflict") or "RP=Reliability; RCA workstream"))
        def fx():
            a,ac=fixture(F1); b,bc=fixture(F2); need(a==P1,f"P1 fixture mismatch {P1^a}"); need(b==P2,f"P2 fixture mismatch {P2^b}"); need(not {"RUNTIME_READY","PRODUCTION_READY","AUTHORITATIVE","CUTOVER_APPROVED"}&set(ac+bc),"forbidden classification"); return "12 P1 and 21 P2 unique scenarios"
        rec("required_fixture_inventory",fx)
        p1={"subjectRef","projectionAsOf","sourceCheckpointRef","profileSchemaVersion","projectionPolicyVersion","activityWindowDays","interactionCounts","recentRegions","recentContentRefs","recentTagRefs","engagementSignals","negativeSignals","sourceEventCount","sourceLineageFingerprint","projectionRecordFingerprint"}
        p2={"experimentRef","experimentVersion","variantRef","exposureRef","runRef","subjectRef","sessionRef","exposedAt","outcomeWindowSeconds","clicked","liked","saved","shared","fallbackObserved","outcomeEventRefs","sourceCheckpointRef","sourceEventCount","sourceLineageFingerprint","projectionRecordFingerprint"}
        rec("data_record_field_equivalence",lambda:(fields(D1,p1),fields(D2,p2),"Data source fields verified")[2])
        def protected():
            bad=[p for p in changed if p in {S1,S2} or p.startswith("jc-recommendation-core/") or (p.startswith("database/journey-connect-db-v2.7/") and p.endswith(".sql"))]; need(not bad,f"protected diff {bad}"); return "P1/P2 sources, core, SQL unchanged"
        rec("protected_source_and_sql_diff",protected,f"git diff --name-only {START}..{head}")
        def sqls():
            nums=[int(m.group(1)) for p in (ROOT/"database/journey-connect-db-v2.7").glob("*.sql") if (m:=re.match(r"(\d+)_",p.name))]; need(set(nums)==set(range(1,53)) and len(nums)==52,"SQL inventory mismatch"); return "SQL 01..52 once; 53+ absent"
        rec("sql_01_52_protected_53_plus_absent",sqls)
        def isolated():
            t="\n".join(p.read_text() for p in PKG.glob("*.java")); bad=[x for x in ["org.springframework","jakarta.persistence","JdbcTemplate","DataSource","EntityManager","@Component","@Service","@Repository","@Controller","@Configuration","System.getenv","System.getProperty","Instant.now","Clock.system","java.net.http"] if x in t]; need(not bad,f"forbidden tokens {bad}"); return "pure Java isolation verified"
        rec("adoption_package_isolation",isolated)
        rec("production_profile_control_unchanged",lambda:(need(not [p for p in changed if re.search(r"application[^/]*\.(?:yml|yaml|properties)$",p) or p.startswith("jc-search-production-controls/")],"production control changed") or "production profiles/controls unchanged"))
        def docs():
            for n in DOCS:
                t=(ROOT/"docs/platform/recommendation"/n).read_text(); h=set(re.findall(r"^##\s+(.+)$",t,re.M)); need(SECTIONS<=h,f"{n} sections missing {SECTIONS-h}"); need(START in t,f"{n} SHA missing")
            return "seven documents complete"
        rec("required_document_structure",docs)
        def contract():
            cmd,out=java_run(); logs["rca0_contract.log"]=out; return cmd
        rec("rca0_contract_fixture_execution",contract,"javac/java dependency-free contract test")
        if args.execute_regressions:
            core=[str(ROOT/"jc-backend/gradlew"),"-p",str(ROOT/"jc-backend"),":jc-recommendation-core:check","--stacktrace","--no-daemon"]
            back=[str(ROOT/"jc-backend/gradlew"),"-p",str(ROOT/"jc-backend"),"test","--stacktrace","--no-daemon"]
            def gradle(name,cmd):
                def go(): logs[name+".log"]=sh(cmd).stdout; return name+" passed"
                rec(name+"_regression",go," ".join(cmd))
            gradle("recommendation_core",core); gradle("backend",back)
        else:
            checks += [{"name":"recommendation_core_regression","status":"NOT_EXECUTED","command":":jc-recommendation-core:check","detail":"use --execute-regressions"},{"name":"backend_regression","status":"NOT_EXECUTED","command":"jc-backend test","detail":"use --execute-regressions"}]
        for n in ["postgresql","shadow_reconciliation","canary","load","replay","production"]: checks.append({"name":n,"status":"NOT_APPLICABLE","command":"NOT_APPLICABLE","detail":"RCA-0 contract/fixture scope"})
    except Exception as e: failures.append(f"verifier_internal: {e}")
    summary={"contractId":"recommendation-data-consumer-alignment-v1","workStartSha":START,"testedSha":head,"result":"PASS" if not failures else "FAIL","checks":checks,"failures":failures}
    (OUT/"RCA0_VERIFICATION_SUMMARY.json").write_text(json.dumps(summary,indent=2,sort_keys=True)+"\n")
    with (OUT/"RCA0_VERIFICATION_SUMMARY.tsv").open("w",newline="") as f:
        w=csv.writer(f,delimiter="\t",lineterminator="\n"); w.writerow(["check","status","command","detail","tested_sha"]); [w.writerow([c["name"],c["status"],c["command"],c["detail"],head]) for c in checks]
    for n,t in logs.items(): (OUT/n).write_text(t)
    print(json.dumps(summary,indent=2,sort_keys=True)); return 1 if failures else 0
if __name__=="__main__": sys.exit(main())
