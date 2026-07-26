#!/usr/bin/env python3
from __future__ import annotations
import argparse, csv, json, subprocess, sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
WORK_START_SHA="40ff229e2401e7d5d9c5323d469bcd012530e882"
RCA2_HEAD="511b19f80cdd42bb2fafde0563c7388b4f5b5f48"
RCA2_MERGE="b57c344c9b4e332966fe9f6d36a5da66a5faae71"
SC6_HEAD="20da93e932c50b5bebd549a56db40edb00ca1eea"
SC6_MERGE="40ff229e2401e7d5d9c5323d469bcd012530e882"
DOC_DIR=ROOT/"docs/platform/operations/op0"
CONTRACT_DIR=ROOT/"verification/operations/op0/contracts"
RUNTIME_DIR=ROOT/"verification/operations/op0/runtime"
ALLOWED_PREFIXES=("docs/platform/operations/op0/","verification/operations/op0/", ".github/workflows/op0-rca2-stage1-operations-preparation-governance-ci.yml")
REQUIRED_DOCS=[f"{i:02d}-{name}.md" for i,name in enumerate([
"OP-0-ENTRY-AND-BASELINE","SC-6-CONTINUITY-VERIFICATION","OPERATIONS-PREPARATION-SCOPE","OP-PHASE-MAP","WORKSTREAM-INVENTORY","OWNERSHIP-AND-RACI","DEPENDENCY-GRAPH","CRITICAL-PATH","OP-1-READINESS-CONTRACT","OP-2-READINESS-CONTRACT","STAGE-1-ENTRY-GATE","ENDPOINT-IMPLEMENTATION-REQUIREMENTS","CREDENTIAL-IMPLEMENTATION-REQUIREMENTS","ALLOWLIST-IMPLEMENTATION-REQUIREMENTS","COHORT-IMPLEMENTATION-REQUIREMENTS","CANDIDATE-ADAPTER-READINESS-REQUIREMENTS","METRIC-IMPLEMENTATION-BACKLOG","DASHBOARD-AND-ALERT-REQUIREMENTS","ROLLBACK-DRILL-PLAN","ROLE-APPROVAL-PACKAGE-REQUIREMENTS","MANUAL-ENABLEMENT-RUNBOOK-SKELETON","EVIDENCE-RETENTION-PLAN","BLOCKER-REGISTER","RISK-REGISTER","OP-1-HANDOFF-PROMPT"])]
REQUIRED_ARTIFACTS=["op0-baseline.json","op-phase-map.json","workstream-inventory.json","owner-matrix.json","dependency-graph.json","critical-path.json","op1-entry-gate.json","op2-entry-gate.json","stage1-enable-gate.json","metric-backlog.json","alert-inventory.json","rollback-plan.json","approval-matrix.json","blocker-register.json","risk-register.json"]
COMMON_FIELDS={"work_start_sha","source_sc6_exact_head","source_sc6_merge_commit","artifact_version","status","owner","updated_at"}
JOBS=["authoritative_baseline","sc6_continuity","document_completeness","machine_readable_artifacts","workstream_ownership","dependency_and_gate_validation","runtime_and_traffic_immutability","historical_evidence_protection","sql_protection"]

def run(*args,check=True):
    p=subprocess.run(args,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if check and p.returncode: raise AssertionError(f"command failed: {' '.join(args)}\n{p.stderr}")
    return p

def load(name): return json.loads((CONTRACT_DIR/name).read_text(encoding="utf-8"))
def changed(): return [x for x in run("git","diff","--name-only",f"{WORK_START_SHA}...HEAD").stdout.splitlines() if x]
def assert_true(v,msg):
    if not v: raise AssertionError(msg)

def authoritative_baseline():
    head=run("git","rev-parse","HEAD").stdout.strip()
    expected=ARGS.expected_head or head
    assert_true(head==expected,f"HEAD {head} != expected {expected}")
    assert_true(run("git","merge-base","--is-ancestor",WORK_START_SHA,head,check=False).returncode==0,"work-start is not ancestor")
    assert_true(head!=WORK_START_SHA,"branch must differ from main")
    assert_true(len(changed())>0,"changed files must be > 0")
    return {"tested_sha":head,"work_start_sha":WORK_START_SHA,"changed_files":len(changed())}

def sc6_continuity():
    for sha in [RCA2_HEAD,RCA2_MERGE,SC6_HEAD,SC6_MERGE]: run("git","cat-file","-e",sha+"^{commit}")
    assert_true("Merge pull request #29" in run("git","show","-s","--format=%B",RCA2_MERGE).stdout,"PR #29 merge evidence missing")
    assert_true("Merge pull request #30" in run("git","show","-s","--format=%B",SC6_MERGE).stdout,"PR #30 merge evidence missing")
    rca_parents=run("git","show","-s","--format=%P",RCA2_MERGE).stdout.split()
    sc6_parents=run("git","show","-s","--format=%P",SC6_MERGE).stdout.split()
    assert_true(RCA2_HEAD in rca_parents,"RCA-2 exact head is not PR #29 merge parent")
    assert_true(SC6_HEAD in sc6_parents,"SC-6 exact head is not PR #30 merge parent")
    assert_true(run("git","merge-base","--is-ancestor",SC6_MERGE,"HEAD",check=False).returncode==0,"SC-6 merge is not ancestor")
    assert_true(run("git","diff","--quiet",SC6_HEAD,SC6_MERGE,check=False).returncode==0,"SC-6 merge-tree equivalence failed")
    traffic=ROOT/"verification/sc-next-track/rca2-nonzero-nonprod-entry/SC6_TRAFFIC_STAGE_DECISION.tsv"
    rows={r['decision']:r['value'] for r in csv.DictReader(traffic.open(encoding='utf-8'),delimiter='\t')}
    expected={'target_environment':'ISOLATED_NON_PRODUCTION_RUNTIME','target_stage':'STAGE_1','target_traffic_percent':'1','current_traffic_percent':'0','traffic_enablement':'BLOCKED_PENDING_ALL_CONDITIONS','feature_flag_default':'OFF','manual_enablement_required':'YES','automatic_rollout':'FORBIDDEN','primary_result_authority':'CURRENT_P1_P2_ONLY','shadow_result_authority':'NONE','shadow_result_serving':'FORBIDDEN','production_traffic_percent':'0','production_activation':'NOT_AUTHORIZED','authority_transfer':'FORBIDDEN'}
    for k,v in expected.items(): assert_true(rows.get(k)==v,f"SC6 traffic continuity {k}")
    return {"pr29_merged":True,"pr30_merged":True,"sc6_merge_tree_equivalence":"PASS"}

def document_completeness():
    missing=[n for n in REQUIRED_DOCS if not (DOC_DIR/n).is_file()]
    assert_true(not missing,f"missing docs: {missing}")
    joined='\n'.join((DOC_DIR/n).read_text(encoding='utf-8') for n in REQUIRED_DOCS)
    for token in [WORK_START_SHA,SC6_HEAD,"CURRENT_NONPRODUCTION_TRAFFIC_PERCENT=0","PRODUCTION_TRAFFIC_PERCENT=0","AUTOMATIC_ROLLOUT=FORBIDDEN","PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY","SHADOW_RESULT_SERVING=FORBIDDEN","OP-1 Handoff Prompt"]:
        assert_true(token in joined,f"required document token missing: {token}")
    return {"document_count":len(REQUIRED_DOCS)}

def machine_readable_artifacts():
    for name in REQUIRED_ARTIFACTS:
        p=CONTRACT_DIR/name; assert_true(p.is_file(),f"missing artifact {name}")
        d=load(name); assert_true(COMMON_FIELDS<=set(d),f"metadata missing {name}")
        assert_true(d['work_start_sha']==WORK_START_SHA,f"work-start mismatch {name}")
        assert_true(d['source_sc6_exact_head']==SC6_HEAD,f"SC6 head mismatch {name}")
        assert_true(d['source_sc6_merge_commit']==SC6_MERGE,f"SC6 merge mismatch {name}")
    return {"artifact_count":len(REQUIRED_ARTIFACTS)}

def workstream_ownership():
    ws=load('workstream-inventory.json')['workstreams']
    assert_true(len(ws)==10,"workstream count must be 10")
    allowed={'NOT_STARTED','PLANNED','BLOCKED','READY_FOR_IMPLEMENTATION','NOT_APPLICABLE'}
    for w in ws:
        assert_true(w['owner'] and w['accountable_role'],f"owner missing {w['id']}")
        assert_true(w['status'] in allowed,f"invalid status {w['id']}")
        for key in ['objective','scope','dependencies','implementation_location','acceptance_criteria','required_evidence','rollback_requirement','forbidden_changes','target_op_phase']:
            assert_true(w.get(key),f"{key} missing {w['id']}")
    owners=load('owner-matrix.json')['owners']
    assert_true(len(owners)>=12,"owner matrix incomplete")
    assert_true(all(o['approval_status']=='PENDING' for o in owners),"actual approval must remain pending")
    return {"workstream_count":10,"owners_assigned":True}

def dependency_and_gate_validation():
    dep=load('dependency-graph.json'); cp=load('critical-path.json')
    types={e['dependency_type'] for e in dep['edges']}
    assert_true({'hard_dependency','soft_dependency','independent','external_dependency','approval_dependency'}<=types,"dependency types incomplete")
    assert_true(len(cp['path'])>=10,"critical path incomplete")
    for name in ['op1-entry-gate.json','op2-entry-gate.json','stage1-enable-gate.json']:
        g=load(name); assert_true(g['logic']=='AND',f"{name} must use AND"); assert_true(g['gate_pass'] is False,f"{name} must be blocked")
    stage=load('stage1-enable-gate.json')
    assert_true(len(stage['conditions'])==16,"Stage1 condition count")
    metrics=load('metric-backlog.json')
    assert_true(metrics['authoritative_metric_count']==27 and len(metrics['authoritative_metrics'])==27,"27 metric continuity")
    required={'traffic_selected_count','traffic_skipped_count','executor_active_count','executor_queue_depth','shadow_task_age_ms','shadow_cancelled_count','checkpoint_lag_ms'}
    assert_true(required=={m['metric'] for m in metrics['required_gap_metrics']},"metric backlog gaps")
    alerts=load('alert-inventory.json')['alerts']
    critical={a['id'] for a in alerts if a['severity']=='CRITICAL'}
    assert_true(len(critical)>=9,"critical alert inventory incomplete")
    levels=load('rollback-plan.json')['levels']
    assert_true([x['level'] for x in levels]==[f'LEVEL_{i}' for i in range(1,8)],"rollback levels incomplete")
    approvals=load('approval-matrix.json')['approvals']
    assert_true(len(approvals)==6 and all(not a['actual_approval_recorded'] for a in approvals),"approval requirements/pending state")
    assert_true((DOC_DIR/'24-OP-1-HANDOFF-PROMPT.md').is_file(),"OP-1 handoff missing")
    return {"dependency_graph":"COMPLETE","critical_path":"DEFINED","gates":"DEFINED_BLOCKED"}

def runtime_and_traffic_immutability():
    files=changed()
    bad=[f for f in files if not any(f.startswith(p) if p.endswith('/') else f==p for p in ALLOWED_PREFIXES)]
    assert_true(not bad,f"scope violation: {bad}")
    assert_true(not any(f.startswith('jc-backend/src/main/') for f in files),"runtime source change")
    assert_true(not any('application' in f.lower() and f.endswith(('.yml','.yaml','.properties')) for f in files),"traffic config change")
    b=load('op0-baseline.json')
    assert_true(b['current_nonproduction_traffic_percent']==0 and b['production_traffic_percent']==0,"traffic must remain zero")
    assert_true(b['feature_flag_default']=='OFF' and b['automatic_rollout']=='FORBIDDEN',"flag/rollout boundary")
    return {"runtime_source_change":False,"traffic_config_change":False,"current_traffic_percent":0}

def historical_evidence_protection():
    protected=('docs/platform/recommendation/rca2/','docs/platform/governance/sc-next-track/','verification/rca2/','verification/sc-next-track/')
    bad=[f for f in changed() if f.startswith(protected)]
    assert_true(not bad,f"historical evidence changed: {bad}")
    return {"historical_evidence_change":False}

def sql_protection():
    bad=[f for f in changed() if f.endswith('.sql') or f.startswith('database/')]
    assert_true(not bad,f"DB/SQL changed: {bad}")
    return {"db_change":"NONE","sql_change":"NONE"}

FUNCS={name:globals()[name] for name in JOBS}
parser=argparse.ArgumentParser(); parser.add_argument('--expected-head'); parser.add_argument('--only',choices=JOBS); ARGS=parser.parse_args()
selected=[ARGS.only] if ARGS.only else JOBS
results={}; failures=[]
for name in selected:
    try: results[name]={"status":"PASS","details":FUNCS[name]()}
    except Exception as e: results[name]={"status":"FAIL","error":str(e)}; failures.append(name)
overall='PASS' if not failures else 'FAIL'
head=run('git','rev-parse','HEAD').stdout.strip()
output={'result':overall,'failures':failures,'tested_sha':head,'work_start_sha':WORK_START_SHA,'source_sc6_exact_head':SC6_HEAD,'source_sc6_merge_commit':SC6_MERGE,'results':results}
RUNTIME_DIR.mkdir(parents=True,exist_ok=True); (RUNTIME_DIR/'op0-verification-result.json').write_text(json.dumps(output,indent=2)+"\n",encoding='utf-8')
print(json.dumps(output,indent=2))
sys.exit(0 if overall=='PASS' else 1)
