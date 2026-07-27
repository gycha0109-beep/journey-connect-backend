#!/usr/bin/env python3
from __future__ import annotations
import argparse, base64, hashlib, json, os, re, subprocess, sys, urllib.request
from pathlib import Path

ROOT=Path(os.getenv('GITHUB_WORKSPACE') or Path(__file__).resolve().parents[3]).resolve()
ART=ROOT/'verification/admin/adm0/adm0-artifacts.json'
DOC=ROOT/'docs/admin/adm0/ADM-0-ADMIN-CAPABILITY-SCHEMA-INTEGRATION-BASELINE.md'
ENTRY=ROOT/'docs/admin/adm0/ADM-0-ENTRY-VERIFICATION.md'
WORKFLOW=ROOT/'.github/workflows/adm0-admin-baseline-governance.yml'
EVIDENCE=ROOT/'verification/admin/adm0/evidence/adm0-verification-evidence.json'
EXPECTED_BACKEND='251f2d14c91c6e5bebb9dcb245aa8b1d7e859976'
EXPECTED_UI='e2c2c283e7f10e32806d4fb5285081e7254b5782'
ALLOWED=(
 '.github/workflows/adm0-admin-baseline-governance.yml',
 'docs/admin/adm0/',
 'verification/admin/adm0/',
)
REQUIRED_ARTIFACTS={
'adm0-baseline','source-repository-inventory','admin-capability-matrix','admin-role-permission-matrix',
'existing-schema-inventory','schema-gap-analysis','migration-plan','admin-api-contract','moderation-state-machine',
'audit-contract','ui-reuse-matrix','repository-responsibility-matrix','branch-integration-plan','final-sync-contract',
'dependency-graph','risk-register','blocker-register','adm1-entry-gate'}
REQUIRED_JOBS={
'authoritative_backend_baseline','youngtak_source_intake','admin_ui_assessment','capability_matrix',
'schema_inventory','migration_plan','api_contract','authorization_contract','audit_contract',
'repository_integration_contract','scope_protection','historical_evidence_protection','sql_protection','independent_verifier'}

class Failure(Exception): pass

def expect(condition, message):
    if not condition: raise Failure(message)

def load():
    expect(ART.is_file(),f'artifact bundle missing: {ART}')
    try:
        return json.loads(ART.read_text(encoding='utf-8'))
    except json.JSONDecodeError as exc:
        raise Failure(f'artifact JSON invalid at line {exc.lineno}, column {exc.colno}: {exc.msg}') from exc

def http_json(url, use_token=True):
    req=urllib.request.Request(url,headers={'Accept':'application/vnd.github+json','User-Agent':'jc-adm0-verifier'})
    token=os.getenv('GITHUB_TOKEN') if use_token else None
    if token: req.add_header('Authorization',f'Bearer {token}')
    with urllib.request.urlopen(req,timeout=20) as response:
        return json.load(response)

def http_repo_file(repository, path, ref):
    payload=http_json(f"https://api.github.com/repos/{repository}/contents/{path}?ref={ref}", use_token=False)
    expect(payload.get('encoding')=='base64' and payload.get('content'),'GitHub contents response is missing base64 content')
    return base64.b64decode(payload['content']).decode('utf-8')

def changed_files():
    base=os.getenv('ADM0_BASE_SHA')
    head=os.getenv('ADM0_HEAD_SHA')
    if not base or not head: return []
    out=subprocess.check_output(['git','diff','--name-only',base,head],cwd=ROOT,text=True)
    return [line for line in out.splitlines() if line]

def check_head():
    expected=os.getenv('ADM0_HEAD_SHA')
    if expected:
        actual=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
        expect(actual==expected,f'checkout is not exact PR head: {actual} != {expected}')

def check_authoritative_backend(data,offline):
    expect(data['bundle_metadata']['backend_main_sha']==EXPECTED_BACKEND,'backend SHA contract mismatch')
    if not offline:
        actual=http_json('https://api.github.com/repos/gycha0109-beep/journey-connect-backend/commits/main')['sha']
        expect(actual==EXPECTED_BACKEND,f'backend main drift: {actual}')

def check_youngtak(data,offline):
    expect(data['bundle_metadata']['ui_source_head_sha']==EXPECTED_UI,'UI SHA contract mismatch')
    if offline: return
    actual=http_json('https://api.github.com/repos/YTAK99/Journey-Connect/commits/youngtak', use_token=False)['sha']
    expect(actual==EXPECTED_UI,f'youngtak source drift: {actual}')
    admin=http_repo_file('YTAK99/Journey-Connect','jc-frontend/src/pages/AdminPage.jsx',EXPECTED_UI)
    app=http_repo_file('YTAK99/Journey-Connect','jc-frontend/src/App.jsx',EXPECTED_UI)
    for pattern in ['/users/me/posts','isLogin()','createPost','deletePost','filteredPosts.slice','posts.reduce']:
        expect(pattern in admin,f'AdminPage source assertion missing: {pattern}')
    for pattern in ['ROLE_ADMIN','hasRole','user?.role','user.role']:
        expect(pattern not in admin,f'current AdminPage unexpectedly contains role guard pattern: {pattern}')
    expect('path="/admin"' in app,'/admin route missing')

def check_ui_assessment():
    text=DOC.read_text(encoding='utf-8')
    for value in ['ADMIN_UI_SHELL=REUSABLE','ADMIN_AUTHORIZATION=NOT_IMPLEMENTED','FULL_SOURCE_BRANCH_MERGE=FORBIDDEN','SELECTIVE_UI_PORT']:
        expect(value in text or value in (ROOT/'docs/admin/adm0/README.md').read_text(encoding='utf-8'),f'UI assessment missing {value}')

def check_artifacts(data,names):
    present=set(data['artifacts'])
    expect(names<=present,f'missing artifacts: {sorted(names-present)}')
    keys={'work_start_sha','backend_repository','backend_main_sha','ui_source_repository','ui_source_branch','ui_source_head_sha','artifact_version','status','owner','updated_at'}
    for name in names:
        expect(keys<=set(data['artifacts'][name]),f'{name} missing common metadata')

def check_scope():
    bad=[]
    for path in changed_files():
        if path==ALLOWED[0] or any(path.startswith(prefix) for prefix in ALLOWED[1:]): continue
        bad.append(path)
    expect(not bad,f'ADM-0 scope violation: {bad}')

def check_sql():
    bad=[p for p in changed_files() if p.lower().endswith('.sql') or '/db/migration/' in p.lower() or p.startswith('database/')]
    expect(not bad,f'SQL/DB change forbidden: {bad}')

def check_history():
    bad=[p for p in changed_files() if re.search(r'(^|/)(rca|sc|op|dp|ip)[-_]',p,re.I) and not p.startswith('docs/admin/adm0/')]
    expect(not bad,f'historical evidence mutation: {bad}')

def check_workflow():
    text=WORKFLOW.read_text(encoding='utf-8')
    for job in REQUIRED_JOBS:
        expect(re.search(rf'^  {re.escape(job)}:\s*$',text,re.M),f'workflow job missing: {job}')
    expect('github.event.pull_request.head.sha' in text,'workflow must checkout exact PR head')
    expect('actions/checkout@v6' in text,'workflow checkout major must be v6')
    expect('actions/setup-python@v6' in text,'workflow setup-python major must be v6')

def check_docs():
    for path in [DOC,ENTRY,ROOT/'docs/admin/adm0/README.md',ROOT/'docs/admin/adm0/ADM-1-ENTRY-GATE-AND-HANDOFF.md',ROOT/'docs/admin/adm0/ADM-1-IMPLEMENTATION-PROMPT.md']:
        expect(path.is_file(),f'document missing: {path}')
    text=DOC.read_text(encoding='utf-8')
    for value in ['ADMIN_CAPABILITY_SCHEMA_AND_INTEGRATION_BASELINE_ESTABLISHED','ADM1_ENTRY=BLOCKED_PENDING_USER_APPROVAL','RUNTIME_SOURCE_CHANGE=NO','SQL_CHANGE=NO','YOUNGTAK_REPOSITORY_CHANGE=NO']:
        expect(value in text,f'baseline decision missing {value}')
    expect('IMPLEMENTATION_COMPLETE' not in text,'ADM-0 must not claim implementation complete')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--check',default='all')
    parser.add_argument('--offline',action='store_true')
    args=parser.parse_args()
    data=load(); check_head()
    checks={
      'authoritative_backend_baseline':lambda:check_authoritative_backend(data,args.offline),
      'youngtak_source_intake':lambda:check_youngtak(data,args.offline),
      'admin_ui_assessment':check_ui_assessment,
      'capability_matrix':lambda:check_artifacts(data,{'admin-capability-matrix'}),
      'schema_inventory':lambda:check_artifacts(data,{'existing-schema-inventory','schema-gap-analysis'}),
      'migration_plan':lambda:check_artifacts(data,{'migration-plan'}),
      'api_contract':lambda:check_artifacts(data,{'admin-api-contract','moderation-state-machine'}),
      'authorization_contract':lambda:check_artifacts(data,{'admin-role-permission-matrix'}),
      'audit_contract':lambda:check_artifacts(data,{'audit-contract'}),
      'repository_integration_contract':lambda:check_artifacts(data,{'repository-responsibility-matrix','branch-integration-plan','final-sync-contract','dependency-graph'}),
      'scope_protection':check_scope,
      'historical_evidence_protection':check_history,
      'sql_protection':check_sql,
      'independent_verifier':lambda:(check_docs(),check_workflow(),check_artifacts(data,REQUIRED_ARTIFACTS),check_scope(),check_history(),check_sql()),
    }
    selected=list(checks) if args.check=='all' else [args.check]
    for name in selected:
        expect(name in checks,f'unknown check: {name}')
        print(f'ADM-0 check start: {name}', flush=True)
        checks[name]()
        print(f'ADM-0 check pass: {name}', flush=True)
    evidence={'schema_version':'adm0-verification-evidence-v1','status':'PASS','checked':selected,'head':os.getenv('ADM0_HEAD_SHA') or 'LOCAL_OFFLINE','artifact_sha256':hashlib.sha256(ART.read_bytes()).hexdigest()}
    EVIDENCE.parent.mkdir(parents=True,exist_ok=True)
    EVIDENCE.write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(evidence))

if __name__=='__main__':
    try: main()
    except Exception as exc:
        print(f'ADM-0 verification failed: {exc}',file=sys.stderr)
        sys.exit(1)
