#!/usr/bin/env python3
from pathlib import Path
import hashlib, csv, re, sys

root=Path(__file__).resolve().parents[2]
ver=root/'verification/ip11'
docs=root/'docs/platform/intelligence'
checks=[]

def check(name, condition, detail=''):
    checks.append((name,bool(condition),str(detail)))

def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
    return h.hexdigest()

def read_manifest(p):
    out=[]
    for line in p.read_text(encoding='utf-8').splitlines():
        if not line.strip(): continue
        h,rel=line.split('  ',1); out.append((rel,h))
    return out

required=[
'IP-11-PRODUCTION-SHADOW-OWNER-DECISION-PACKET.md',
'IP-11-PRODUCTION-SHADOW-ACTIVATION-GOVERNANCE-CONTRACT.md',
'IP-11-RACI-AND-OPERATING-AUTHORITY.md',
'IP-11-RUNTIME-INPUT-RESOURCE-AND-SAMPLING-BUDGET.md',
'IP-11-PRIVACY-SECURITY-RETENTION-AND-OBSERVABILITY.md',
'IP-11-EMERGENCY-DISABLE-AND-ROLLBACK-RUNBOOK.md',
'IP-11-EXTERNAL-ATTESTATION-PLAN.md',
'IP-11-GO-NO-GO-MATRIX.md',
'IP-11-VERIFICATION-AND-SELF-REVIEW.md',
'IP-11-HANDOFF.md']
for name in required:
    p=docs/name
    check('document_exists:'+name,p.is_file())
    check('document_nonempty:'+name,p.is_file() and p.stat().st_size>500,p.stat().st_size if p.exists() else 0)

readme=docs/'README.md'
rtext=readme.read_text(encoding='utf-8')
check('readme_ip11_state','GOVERNANCE_DOCUMENTED / CONTROL_BLOCKERS_OPEN / NO_GO' in rtext)
for name in required:
    check('readme_link:'+name,f']({name})' in rtext)

# Relative markdown links in README and IP-11 docs
link_re=re.compile(r'\[[^\]]+\]\(([^)]+)\)')
for p in [readme]+[docs/x for x in required]:
    text=p.read_text(encoding='utf-8')
    for target in link_re.findall(text):
        target=target.split('#',1)[0]
        if not target or '://' in target or target.startswith('mailto:'): continue
        check(f'link_valid:{p.name}:{target}',(p.parent/target).exists())

# Decision register
with (ver/'IP11_DECISION_REGISTER.tsv').open(encoding='utf-8') as f:
    decisions=list(csv.DictReader(f,delimiter='\t'))
ids=[r['decision_id'] for r in decisions]
allowed={'APPROVED','APPROVED_WITH_CONDITIONS','DEFERRED','REJECTED','OPEN_BLOCKER'}
check('decision_count',len(decisions)==12,len(decisions))
check('decision_unique_ids',len(set(ids))==12,len(set(ids)))
check('decision_exact_ids',set(ids)=={f'IP11-DEC-{i:03d}' for i in range(1,13)})
check('decision_status_allowed',all(r['status'] in allowed for r in decisions))
required_cols=['decision_id','topic','decision','status','owner','approver','evidence','effective_date','expiry_review_date','conditions','blockers','reversal_trigger','related_documents']
check('decision_required_columns',all(c in decisions[0] for c in required_cols))
check('decision_fields_nonblank',all(all(r[c].strip() for c in required_cols) for r in decisions))
owner_topics={'Activation Owner','Rollback Owner','Kill-switch Owner'}
check('unassigned_owner_decisions_not_approved',all(r['status']=='OPEN_BLOCKER' for r in decisions if r['topic'] in owner_topics))
check('runtime_source_open',next(r for r in decisions if r['decision_id']=='IP11-DEC-004')['status']=='OPEN_BLOCKER')
check('security_privacy_open',next(r for r in decisions if r['decision_id']=='IP11-DEC-008')['status']=='OPEN_BLOCKER')
check('observability_open',next(r for r in decisions if r['decision_id']=='IP11-DEC-009')['status']=='OPEN_BLOCKER')
check('attestation_not_approved',next(r for r in decisions if r['decision_id']=='IP11-DEC-012')['status'] in {'DEFERRED','OPEN_BLOCKER'})

# RACI
with (ver/'IP11_RACI_MATRIX.tsv').open(encoding='utf-8') as f:
    raci=list(csv.DictReader(f,delimiter='\t'))
check('raci_nonempty',len(raci)>=10,len(raci))
for role in ['business_owner','product_owner','backend_owner','search_owner','security_privacy','operations','release_operator','system_coordination','independent_verifier']:
    check('raci_role:'+role,role in raci[0])
for activity in ['Final Go','Enable/disable execution','Emergency disable','Rollback verification','External attestation']:
    check('raci_activity:'+activity,any(r['activity']==activity for r in raci))

# Go/no-go
with (ver/'IP11_GO_NO_GO_MATRIX.tsv').open(encoding='utf-8') as f:
    gates=list(csv.DictReader(f,delimiter='\t'))
required_gates={'activation owner assigned','rollback owner assigned','kill-switch owner/path','authoritative input approved','production guard verified','executor budget approved','latency/error budget approved','sampling ceiling approved','retention approved','privacy approved','security approved','observability ready','alerting ready','cohort approved','emergency disable tested','external attestation complete','legacy response exact','Search cutover disabled','persistence disabled','exposure disabled'}
check('gng_gate_count',len(gates)==20,len(gates))
check('gng_required_gates',set(r['gate'] for r in gates)==required_gates)
check('gng_open_blockers_present',sum(r['status']=='OPEN_BLOCKER' for r in gates)>=10)
gng_doc=(docs/'IP-11-GO-NO-GO-MATRIX.md').read_text(encoding='utf-8')
check('gng_final_no_go','current decision | `NO_GO`' in gng_doc and 'Current result: **NO_GO**' in gng_doc)
check('ip12_hold','IP-12 | `HOLD`' in (docs/'IP-11-HANDOFF.md').read_text(encoding='utf-8'))

# Required content
packet=(docs/required[0]).read_text(encoding='utf-8')
check('production_shadow_disabled','production shadow | `DISABLED`' in packet)
check('search_cutover_not_started','Search cutover | `NOT STARTED`' in packet)
check('baseline_sha_recorded','97931cefa9c591a603dc2ce8219678eb2a46214e1d5a2dc78388fdd7400c321f' in packet)
privacy=(docs/'IP-11-PRIVACY-SECURITY-RETENTION-AND-OBSERVABILITY.md').read_text(encoding='utf-8')
for token in ['raw query | `PROHIBITED`','raw user/session/JWT subject | `PROHIBITED`','full request/response payload | `PROHIBITED`','Micrometer/Prometheus metrics | `NOT PRESENT`']:
    check('privacy_observability:'+token,token in privacy)
runbook=(docs/'IP-11-EMERGENCY-DISABLE-AND-ROLLBACK-RUNBOOK.md').read_text(encoding='utf-8')
check('runbook_current_future_split','## 3. Current executable path versus future target' in runbook)
check('runbook_10_steps',all(f'| {i} |' in runbook for i in range(1,11)))
check('runbook_locations','Manipulation location' in runbook and runbook.count('`TBD`')>=4)
check('runbook_no_fake_toggle','No command is invented' in runbook)
budget=(docs/'IP-11-RUNTIME-INPUT-RESOURCE-AND-SAMPLING-BUDGET.md').read_text(encoding='utf-8')
check('synthetic_not_authoritative','synthetic test/stage data' in budget and 'not a production source of truth' in budget)
check('cohort_effective_zero','effective cohort remains empty' in budget)
check('sampling_zero','`0 bps`' in budget)
check('executor_budget_complete',all(x in budget for x in ['thread naming','shutdown grace','context propagation','circuit threshold']))
check('queue_budget_complete',all(x in budget for x in ['queue wait ceiling','queue age metric','rejection threshold','saturation alert']))
check('latency_budget_complete',all(x in budget for x in ['p50','p95','p99','hard runtime timeout','legacy endpoint added latency']))
check('error_budget_complete',all(x in budget for x in ['provider unavailable','runtime failure','comparison failure','evidence failure','circuit open']))
check('sampling_controls_complete',all(x in budget for x in ['sample seed','deterministic key','observation period','absolute ceiling']))
check('security_checklist','## 4. Security checklist' in privacy)
check('privacy_checklist','## 5. Privacy checklist' in privacy)
check('observability_destination_contract','Required production signal contract' in privacy and 'alert channel/on-call' in privacy)

# Hash manifests
def check_manifest_pair(prefix, expected_count=None):
    exp=read_manifest(ver/f'{prefix}_EXPECTED_SHA256.txt')
    cur=read_manifest(ver/f'{prefix}_CURRENT_SHA256.txt')
    check(prefix+':same_paths',[x[0] for x in exp]==[x[0] for x in cur])
    check(prefix+':same_hashes',exp==cur)
    if expected_count is not None: check(prefix+':count',len(exp)==expected_count,len(exp))
    for rel,h in cur:
        p=root/rel
        check(prefix+':file_exists:'+rel,p.is_file())
        if p.is_file(): check(prefix+':file_hash:'+rel,sha(p)==h)

check_manifest_pair('IP11_PROTECTED_SOURCE',320)
check_manifest_pair('IP11_SQL_01_26',26)
check_manifest_pair('IP11_BACKEND_PROTECTED',9)
check_manifest_pair('IP11_PRODUCTION_SOURCE')
check_manifest_pair('IP11_PRODUCTION_CONFIG')
check_manifest_pair('IP11_BUILD_LOGIC')

# No sensitive diff
with (ver/'IP11_SOURCE_ONLY_DIFF.tsv').open(encoding='utf-8') as f:
    rows=list(csv.reader(f,delimiter='\t'))
check('source_only_diff_empty',len(rows)==1,len(rows)-1)
nochange=(ver/'IP11_PRODUCTION_NO_CHANGE_RESULT.txt').read_text(encoding='utf-8')
for x in ['production_source_result=PASS','production_config_result=PASS','build_logic_result=PASS','canonical_sql_result=PASS','protected_source_result=PASS','production_activation_code_added=NO','production_enable_config_added=NO','search_cutover_added=NO','new_sql_or_migration_added=NO']:
    check('nochange:'+x,x in nochange)

# No production enable properties in main resources
prod_resources=[]
for p in root.glob('**/src/main/resources/**/*'):
    if p.is_file():
        text=p.read_text(encoding='utf-8',errors='ignore')
        if re.search(r'search[.\-]shadow|explicit-allow|search-shadow-stage|search-shadow-test',text,re.I): prod_resources.append(p.relative_to(root).as_posix())
check('production_resource_activation_absent',not prod_resources,','.join(prod_resources))

# Package paths
all_paths=[]; non_ascii=[]; suspicious=[]; over=[]
for p in root.rglob('*'):
    if p.is_file():
        rel=(Path(root.name)/p.relative_to(root)).as_posix(); all_paths.append(rel)
        if len(rel)>240: over.append(rel)
        if any(ord(c)>127 for c in rel): non_ascii.append(rel)
        if '#U' in rel or any(x in rel for x in ['∞','Ω','φ','╢']): suspicious.append(rel)
check('root_name_length',len(root.name)<=40,len(root.name))
check('paths_le_240',not over,max(map(len,all_paths)) if all_paths else 0)
check('paths_ascii',not non_ascii,len(non_ascii))
check('paths_not_garbled',not suspicious,len(suspicious))
rename_rows=(ver/'IP11_PATH_RENAME_MAP.tsv').read_text(encoding='utf-8').splitlines()
check('rename_count',len(rename_rows)-1==10,len(rename_rows)-1)
check('rename_content_preserved',all(line.endswith('\tTRUE') for line in rename_rows[1:]))

# External attestation is explicitly not executed
att=(ver/'IP11_EXTERNAL_ATTESTATION_STATUS.txt').read_text(encoding='utf-8')
check('gradle_not_executed','Gradle 8.14.5: NOT EXECUTED — USER-DIRECTED SKIP' in att)
check('postgres_not_executed','Docker/Testcontainers/PostgreSQL 15: NOT EXECUTED — USER-DIRECTED SKIP' in att)

# Document manifest
for rel,h in read_manifest(ver/'IP11_DOCUMENT_MANIFEST_SHA256.txt'):
    check('doc_manifest:'+rel,(root/rel).is_file() and sha(root/rel)==h)

passed=sum(ok for _,ok,_ in checks); failed=len(checks)-passed
print(f'IP-11 static verification checks={len(checks)} pass={passed} fail={failed}')
for name,ok,detail in checks:
    print(('PASS' if ok else 'FAIL')+'\t'+name+(('\t'+detail) if detail else ''))
if failed: sys.exit(1)
