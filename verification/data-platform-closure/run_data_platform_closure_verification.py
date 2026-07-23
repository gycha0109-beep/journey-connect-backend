#!/usr/bin/env python3
from __future__ import annotations
import csv, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
MAIN='c528f6fb0942389b70a348cb9aa672eb7819a392'
DOCS=[
'docs/platform/data/DATA-PLATFORM-TECHNICAL-BASELINE-V1.md','docs/platform/data/DATA-PLATFORM-AUTHORITY-CLOSURE-V1.md','docs/platform/data/DATA-PLATFORM-PRODUCTION-READINESS-GAPS-V1.md','docs/platform/data/DATA-PLATFORM-PRODUCTION-ACTIVATION-DEPENDENCIES-V1.md','docs/platform/data/DATA-PLATFORM-CHANGE-POLICY-V1.md','docs/platform/data/HANDOFF-DATA-TO-RECOMMENDATION-V1.md','docs/platform/data/HANDOFF-DATA-TO-INTELLIGENCE-V1.md','docs/platform/data/HANDOFF-DATA-TO-SEARCH-V1.md','docs/platform/data/HANDOFF-DATA-TO-OPERATIONS-V1.md','docs/platform/data/HANDOFF-DATA-TO-RELIABILITY-V1.md','docs/platform/data/DATA-PLATFORM-CLOSURE-HANDOFF.md']
GOV=['docs/platform/governance/JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md','docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md','docs/platform/governance/SC-DECISION-REGISTER.md','docs/platform/governance/SC-PLATFORM-REGISTRY.md','docs/platform/governance/SC-HANDOFF.md','docs/platform/data/DP-7-HANDOFF.md']
EVIDENCE=['DATA_PLATFORM_CLOSURE_BASELINE.tsv','DATA_PLATFORM_PHASE_STATUS.tsv','DATA_PLATFORM_OBJECT_INVENTORY.tsv','DATA_PLATFORM_CONTRACT_INVENTORY.tsv','DATA_PLATFORM_AUTHORITY_MATRIX.tsv','DATA_PLATFORM_ROLE_GRANT_MATRIX.tsv','DATA_PLATFORM_POLICY_INVENTORY.tsv','DATA_PLATFORM_FINGERPRINT_INVENTORY.tsv','DATA_PLATFORM_RETENTION_INVENTORY.tsv','DATA_PLATFORM_PRODUCTION_GAPS.tsv','DATA_PLATFORM_RECOMMENDATION_HANDOFF.tsv','DATA_PLATFORM_INTELLIGENCE_HANDOFF.tsv','DATA_PLATFORM_SEARCH_HANDOFF.tsv','DATA_PLATFORM_OPERATIONS_HANDOFF.tsv','DATA_PLATFORM_RELIABILITY_HANDOFF.tsv','DATA_PLATFORM_ACTIVATION_GATES.tsv','DATA_PLATFORM_CHANGE_POLICY.tsv','DATA_PLATFORM_PROTECTED_STATE.tsv','DATA_PLATFORM_POST_MERGE_VALIDATION.tsv','DATA_PLATFORM_CLOSURE_DECISIONS.tsv','DATA_PLATFORM_CLOSURE_STATUS.tsv']
EV=ROOT/'verification/data-platform-closure'
def fail(m): raise SystemExit('FAIL: '+m)
for rel in DOCS+GOV:
 p=ROOT/rel
 if not p.is_file() or not p.read_text(encoding='utf-8').strip(): fail('missing document '+rel)
for name in EVIDENCE:
 p=EV/name
 if not p.is_file(): fail('missing evidence '+name)
 with p.open(encoding='utf-8',newline='') as h: rows=list(csv.reader(h,delimiter='\t'))
 if len(rows)<2 or any(not c.strip() for c in rows[0]): fail('invalid evidence '+name)
combined='\n'.join((ROOT/r).read_text(encoding='utf-8') for r in DOCS+GOV)
for marker in (MAIN,'DP-0~DP-7','SQL `01..52`','SQL `53+`','CONDITIONALLY_COMPATIBLE','INCONCLUSIVE','Operations','Reliability','Production shadow: DISABLED','Sampling: 0 BPS','Cohort: EMPTY','historical migration rewrite','NOT_AUTHORIZED'):
 if marker not in combined: fail('marker missing '+marker)
sql=ROOT/'database/journey-connect-db-v2.7'
for n in range(1,53):
 if len(list(sql.glob(f'{n:02d}_*.sql')))!=1: fail(f'SQL {n:02d} missing/duplicated')
if list(sql.glob('5[3-9]_*.sql')) or list(sql.glob('[6-9][0-9]_*.sql')): fail('SQL53+ present')
prod=(ROOT/'jc-backend/src/main/resources/application-prod.yml').read_text(encoding='utf-8')
for marker in ('enabled: ${JC_SEARCH_SHADOW_PRODUCTION_ENABLED:false}','kill-switch: ${JC_SEARCH_SHADOW_PRODUCTION_KILL_SWITCH:true}','sampling-bps: ${JC_SEARCH_SHADOW_PRODUCTION_SAMPLING_BPS:0}','allowlist-hashes: ${JC_SEARCH_SHADOW_PRODUCTION_ALLOWLIST_HASHES:}'):
 if marker not in prod: fail('production default missing '+marker)
allowed=set(DOCS+GOV+['verification/data-platform-closure/run_data_platform_closure_verification.py','.github/workflows/data-platform-closure-ci.yml','.github/workflows/data-contract-ci.yml','.github/workflows/data-postgres-ci.yml','.github/workflows/dp6-allocation-ci.yml','.github/workflows/dp7-allocation-ci.yml','.github/workflows/backend-pr-ci.yml','.github/workflows/recommendation-p0-db-ci.yml','.github/workflows/sc-baseline-reconciliation.yml','verification/dp7/run_dp7_allocation_verification.py','verification/sc-dp1-baseline-reconciliation/run_sc_baseline_reconciliation.py'])
subprocess.run(['git','fetch','origin','main','--depth=1'],cwd=ROOT,check=False,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
changed=subprocess.run(['git','diff','--name-only','origin/main...HEAD'],cwd=ROOT,check=True,text=True,capture_output=True).stdout.splitlines()
for rel in filter(None,changed):
 if rel.startswith('verification/data-platform-closure/'): continue
 if rel not in allowed: fail('unexpected diff '+rel)
for rel in changed:
 if rel.startswith(('database/','jc-backend/src/main/','jc-backend/src/main/resources/','jc-recommendation-core/','jc-intelligence-contracts/','jc-search-')): fail('protected change '+rel)
print('Data Platform technical closure documents, evidence, SQL range and protected state: PASS')
