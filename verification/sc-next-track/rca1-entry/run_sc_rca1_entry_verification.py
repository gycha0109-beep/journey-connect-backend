#!/usr/bin/env python3
from __future__ import annotations

import csv
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
EV = ROOT / "verification/sc-next-track/rca1-entry"
OUT = EV / "runtime"
SQL = ROOT / "database/journey-connect-db-v2.7"
MAIN = "f802a105e46a62718616acaa7a3db6c172e7ed10"
RCA0 = "d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d"
DOCS = [
 "docs/platform/governance/sc-next-track/SC-3-RCA-1-ENTRY-AUTHORIZATION-AND-BASELINE-ALLOCATION.md",
 "docs/platform/governance/sc-next-track/13-SC-RCA1-EXECUTION-MODEL-DECISION.md",
 "docs/platform/governance/sc-next-track/14-SC-RCA1-P1-RECONCILIATION-AUTHORITY-AND-EQUIVALENCE.md",
 "docs/platform/governance/sc-next-track/15-SC-RCA1-P2-RECONCILIATION-AUTHORITY-AND-EQUIVALENCE.md",
 "docs/platform/governance/sc-next-track/16-SC-RCA1-IDENTITY-MAPPING-GOVERNANCE.md",
 "docs/platform/governance/sc-next-track/17-SC-RCA1-EVIDENCE-AND-PRIVACY-POLICY.md",
 "docs/platform/governance/sc-next-track/18-SC-RCA1-OPERATIONS-RELIABILITY-PREREQUISITE-MATRIX.md",
 "docs/platform/governance/sc-next-track/19-SC-RCA1-DB-SQL-IMPACT-DECISION.md",
 "docs/platform/governance/sc-next-track/20-SC-RCA1-VERIFICATION-PLAN.md",
 "docs/platform/governance/sc-next-track/21-SC-RCA1-EXIT-CRITERIA-AND-RCA2-HANDOFF.md",
 "docs/platform/governance/sc-next-track/22-RCA-1-IMPLEMENTATION-HANDOFF-PROMPT.md",
]
GOV = [
 "docs/platform/governance/JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md",
 "docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md",
 "docs/platform/governance/SC-PLATFORM-REGISTRY.md",
 "docs/platform/governance/SC-DECISION-REGISTER.md",
 "docs/platform/governance/SC-RACI.md",
 "docs/platform/governance/SC-HANDOFF.md",
]
SECTIONS = {"Scope","Current Baseline","Decision","Rationale","Authority","Dependencies","Allowed Changes","Forbidden Changes","Identity/Privacy","DB/SQL Impact","Production Impact","Verification","Risks","Exit Criteria","Handoff"}
P1 = {"EXACT_FIELD_PARITY","DERIVED_VALUE_PARITY","AGGREGATE_WINDOW_PARITY","ORDERING_NOT_COMPARABLE","EVENT_GRAIN_MISSING","EXPLICIT_PREFERENCE_MISSING","TRANSFORM_POLICY_MISSING","FINGERPRINT_SEMANTICS_PROTECTED","IDENTITY_BLOCKED"}
P2 = {"EXPOSURE_REFERENCE_PARITY","ASSIGNMENT_PARITY","SUBJECT_SESSION_RUN_PARITY","OUTCOME_WINDOW_PARITY","ENGAGEMENT_EVENT_PARITY","FALLBACK_BINDING_PARITY","STALE_UNEXPOSED_ASSIGNMENT_GAP","OBSERVATION_DEDUPE_GAP","CANONICAL_DATASET_HASH_PROTECTED","RELEASE_EVIDENCE_PROTECTED","IDENTITY_BLOCKED"}
ALLOWED = {"MATCH_EXACT","MATCH_DERIVED","EXPECTED_SEMANTIC_GAP","MIGRATION_REQUIRED","IDENTITY_MAPPING_REQUIRED","IDENTITY_SCHEME_MISMATCH","SOURCE_CHECKPOINT_MISMATCH","SOURCE_STALE","LINEAGE_MISMATCH","EXPOSURE_AUTHORITY_MISMATCH","OUTCOME_WINDOW_MISMATCH","FALLBACK_BINDING_MISMATCH","PROTECTED_AUTHORITY_DIFFERENCE","RECONCILIATION_INCONCLUSIVE"}
FORBIDDEN = {"RUNTIME_READY","PRODUCTION_READY","CUTOVER_READY","AUTHORITATIVE","AUTHORITY_TRANSFERRED"}


def sh(*cmd: str, check: bool = True) -> str:
    return subprocess.run(cmd,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=check).stdout.strip()

def need(ok: bool, msg: str) -> None:
    if not ok: raise AssertionError(msg)

def read(rel: str) -> str:
    p=ROOT/rel; need(p.is_file(),f"missing {rel}"); t=p.read_text(encoding="utf-8"); need(bool(t.strip()),f"empty {rel}"); return t

def rows(name: str) -> list[dict[str,str]]:
    with (EV/name).open(encoding="utf-8",newline="") as f:
        r=csv.DictReader(f,delimiter="\t"); data=list(r); fields=r.fieldnames or []
    need(bool(fields) and bool(data),f"invalid {name}")
    sig=[tuple(x.get(k,"") for k in fields) for x in data]; need(len(sig)==len(set(sig)),f"duplicates {name}")
    return data

def fixture(rel: str) -> int:
    with (ROOT/rel).open(encoding="utf-8",newline="") as f: data=list(csv.DictReader(f,delimiter="\t"))
    need(len({x["scenario"] for x in data})==len(data),f"fixture duplicate {rel}"); return len(data)

def main() -> int:
    OUT.mkdir(parents=True,exist_ok=True); checks=[]; failures=[]; head="UNKNOWN"
    def rec(name,fn,cmd):
        try: checks.append({"check":name,"status":"PASS","command":cmd,"detail":fn() or "verified"})
        except Exception as e: failures.append(f"{name}: {e}"); checks.append({"check":name,"status":"FAIL","command":cmd,"detail":str(e)})
    try:
        head=sh("git","rev-parse","HEAD"); sh("git","fetch","origin","main","--depth=2",check=False)
        def baseline():
            need(sh("git","rev-parse","origin/main")==MAIN,"origin/main moved")
            need("Merge pull request #23" in sh("git","show","-s","--format=%s",MAIN),"PR23 merge absent")
            sh("git","merge-base","--is-ancestor",RCA0,MAIN); sh("git","diff","--quiet",RCA0,MAIN); sh("git","merge-base","--is-ancestor",MAIN,head)
            return "actual main, PR23 merge, RCA0 tree and branch ancestry verified"
        rec("authoritative_main_and_rca0_merge",baseline,"git ancestry/tree checks")
        def documents():
            for rel in DOCS:
                t=read(rel); h=set(re.findall(r"^##\s+(.+)$",t,re.M)); need(SECTIONS<=h,f"sections missing {rel}"); need(MAIN in t,f"main SHA missing {rel}")
            inv=rows("SC_RCA1_ENTRY_DOCUMENTS.tsv"); need({x["path"] for x in inv}==set(DOCS),"document inventory mismatch")
            return "11 required documents complete"
        rec("required_documents",documents,"document structure and inventory")
        def governance():
            t="\n".join(read(x) for x in GOV)
            for m in (MAIN,RCA0,"RCA1_ENTRY_AUTHORIZED","MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION","SYNTHETIC_ONLY","SQL `01..52`","SQL `53+`","PRODUCTION_ACTIVATION: NOT_AUTHORIZED","CURRENT_P1_P2_AUTHORITY_UNCHANGED"):
                need(m in t,f"governance marker {m}")
            need("RP remains reserved for Reliability Platform" in t or "`RP` means Reliability Platform" in t,"RP marker")
            return "six governance documents aligned"
        rec("governance_alignment",governance,"read governance")
        def rca0():
            need("RCA0_CONTRACT_AND_FIXTURE_COMPLETE" in read("docs/platform/recommendation/RCA-0-HANDOFF.md"),"handoff marker")
            java="\n".join(p.read_text(encoding="utf-8") for p in (ROOT/"jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption").glob("*.java"))
            for cid in ("recommendation-data-consumer-alignment-v1","recommendation-profile-input-consumer-v1","experiment-outcome-input-consumer-v1","recommendation-data-consumer-fixture-v1"): need(cid in java,f"contract {cid}")
            need(fixture("jc-backend/src/test/resources/recommendation-data-adoption/p1-fixtures-v1.tsv")==12,"P1 count")
            need(fixture("jc-backend/src/test/resources/recommendation-data-adoption/p2-fixtures-v1.tsv")==21,"P2 count")
            return "RCA0 handoff, four contracts and 12/21 fixtures verified"
        rec("rca0_assets",rca0,"read RCA0 assets")
        def authority():
            p1=read("jc-backend/src/main/java/com/jc/backend/recommendation/p1/RecommendationP1ProfileSource.java")
            for m in ("recommendation_user_preference","recommendation_behavior_event","public.posts"): need(m in p1,f"P1 marker {m}")
            p2=read("jc-backend/src/main/java/com/jc/backend/recommendation/p2/RecommendationP2ObservationSource.java")
            for m in ("recommendation_p2_experiment_assignment","recommendation_p2_experiment_exposure","recommendation_p1_profile_snapshot","b.event_type in ('click','like','save','share')","interval '7 days'","r.run_status = 'fallback'"): need(m in p2,f"P2 marker {m}")
            return "current P1/P2 authority markers verified"
        rec("p1_p2_authority",authority,"read current sources")
        def protected():
            nums=[int(m.group(1)) for p in SQL.glob("*.sql") if (m:=re.match(r"(\d+)_",p.name))]; need(set(nums)==set(range(1,53)) and len(nums)==52,"SQL inventory")
            prod=read("jc-backend/src/main/resources/application-prod.yml")
            for m in ("enabled: ${JC_SEARCH_SHADOW_PRODUCTION_ENABLED:false}","kill-switch: ${JC_SEARCH_SHADOW_PRODUCTION_KILL_SWITCH:true}","sampling-bps: ${JC_SEARCH_SHADOW_PRODUCTION_SAMPLING_BPS:0}"): need(m in prod,f"prod marker {m}")
            return "SQL 01..52 exact, 53+ absent, production defaults protected"
        rec("sql_and_production_protection",protected,"SQL/config inventory")
        def decisions():
            d={x["decision"]:x for x in rows("SC_RCA1_ENTRY_DECISIONS.tsv")}
            expected={"authoritative_main":MAIN,"rca0_exact_final_head":RCA0,"entry":"RCA1_ENTRY_AUTHORIZED","classification":"JOINT_INTELLIGENCE_RELIABILITY_ADOPTION","execution_model":"MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION","identity_mode":"SYNTHETIC_ONLY","db_change":"NONE","sql_allocation":"NOT_REQUIRED","runtime_wiring":"NOT_AUTHORIZED","production_impact":"NONE","production_activation":"NOT_AUTHORIZED","implementation":"SEPARATE_PR_REQUIRED"}
            for k,v in expected.items(): need(d.get(k,{}).get("value")==v,f"decision {k}")
            return "single Model A, synthetic identity and no DB/runtime decisions verified"
        rec("entry_decisions",decisions,"decision TSV")
        def taxonomy():
            need({x["dimension"] for x in rows("SC_RCA1_P1_DIMENSIONS.tsv")}==P1,"P1 taxonomy")
            need({x["dimension"] for x in rows("SC_RCA1_P2_DIMENSIONS.tsv")}==P2,"P2 taxonomy")
            t=rows("SC_RCA1_RESULT_TAXONOMY.tsv"); need({x["result"] for x in t if x["status"]=="allowed"}==ALLOWED,"allowed taxonomy"); need({x["result"] for x in t if x["status"]=="forbidden"}==FORBIDDEN,"forbidden taxonomy")
            ident={x["field"]:x for x in rows("SC_RCA1_IDENTITY_GOVERNANCE.tsv")}; need(ident["IDENTITY_MAPPING_FAILURE_POLICY"]["value"]=="FAIL_CLOSED","identity fail closed"); need(ident["REAL_IDENTITY_MAPPING_OWNER"]["status"]=="BLOCKED","real identity not blocked")
            return "lane, result and identity taxonomies verified"
        rec("taxonomies_and_identity",taxonomy,"machine evidence TSVs")
        def diff():
            changed=sh("git","diff","--name-only",f"{MAIN}..{head}").splitlines(); exact=set(GOV)|set(DOCS)|{".github/workflows/sc-baseline-reconciliation.yml","verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py","verification/sc-next-track/run_sc_next_track_reconciliation.py"}
            unexpected=[p for p in changed if p not in exact and not p.startswith("verification/sc-next-track/rca1-entry/")]; need(not unexpected,f"unexpected {unexpected}")
            protected=[p for p in changed if p.startswith(("database/","jc-backend/src/main/","jc-backend/src/test/resources/recommendation-data-adoption/","jc-recommendation-core/","docs/platform/recommendation/RCA-0-","verification/rca0/","docs/platform/governance/sc-next-track/01-","docs/platform/governance/sc-next-track/02-","docs/platform/governance/sc-next-track/03-","docs/platform/governance/sc-next-track/04-","docs/platform/governance/sc-next-track/05-","docs/platform/governance/sc-next-track/06-","docs/platform/governance/sc-next-track/07-","docs/platform/governance/sc-next-track/08-","docs/platform/governance/sc-next-track/09-","docs/platform/governance/sc-next-track/10-","docs/platform/governance/sc-next-track/11-","docs/platform/governance/sc-next-track/12-"))]; need(not protected,f"protected {protected}")
            text="\n".join(read(p) for p in changed if (ROOT/p).is_file()); need(not re.search(r"\bRP\s*(?:=|:|means)\s*Recommendation(?:\s+Platform)?\b",text,re.I),"RP conflict")
            return f"governance-only diff {len(changed)} files; RCA0 historical evidence unchanged"
        rec("governance_only_diff",diff,"git protected diff")
        def prompt():
            t=read("docs/platform/governance/sc-next-track/22-RCA-1-IMPLEMENTATION-HANDOFF-PROMPT.md")
            for m in ("RCA1_ENTRY_BLOCKED_BY_SC3_MERGE","MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION","SYNTHETIC_ONLY","P1_RECONCILIATION_EXECUTED","P2_RECONCILIATION_EXECUTED","Do not merge without explicit user approval"): need(m in t,f"prompt {m}")
            return "implementation handoff complete"
        rec("implementation_handoff",prompt,"read prompt")
    except Exception as e: failures.append(f"verifier_internal: {e}")
    for name in ("postgresql","shadow_comparison","runtime","canary","load","replay","production"):
        checks.append({"check":name,"status":"NOT_EXECUTED","command":"NOT_EXECUTED","detail":"SC-3 governance authorization only"})
    summary={"contractId":"sc-3-rca1-entry-authorization-v1","authoritativeMain":MAIN,"rca0ExactFinalHead":RCA0,"testedSha":head,"result":"PASS" if not failures else "FAIL","checks":checks,"failures":failures}
    (OUT/"SC_RCA1_ENTRY_VERIFICATION_SUMMARY.json").write_text(json.dumps(summary,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    with (OUT/"SC_RCA1_ENTRY_VERIFICATION_SUMMARY.tsv").open("w",encoding="utf-8",newline="") as f:
        w=csv.writer(f,delimiter="\t",lineterminator="\n"); w.writerow(["check","status","command","detail","tested_sha"]); [w.writerow([c["check"],c["status"],c["command"],c["detail"],head]) for c in checks]
    print(json.dumps(summary,indent=2,sort_keys=True)); return 1 if failures else 0

if __name__=="__main__": sys.exit(main())
