#!/usr/bin/env python3
from __future__ import annotations
import csv, re, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'verification/sc-dp1-baseline-reconciliation'
REQUIRED=['docs/platform/governance/JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md','docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md','docs/platform/governance/SC-DECISION-REGISTER.md','docs/platform/governance/SC-RACI.md','docs/platform/governance/SC-PLATFORM-REGISTRY.md','docs/platform/governance/SC-DP1-BASELINE-RECONCILIATION.md','docs/platform/governance/SC-DP3-ENTRY-DECISIONS.md','docs/platform/governance/SC-DP4-5-PERSISTENCE-ALLOCATION.md','docs/platform/governance/SC-DP5-PROJECTION-ALLOCATION.md','docs/platform/governance/SC-DP6-QUALITY-ALLOCATION.md','docs/platform/governance/SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md','docs/platform/governance/SC-HANDOFF.md','docs/platform/data/DP-0-DATA-PLATFORM-CONTRACT-FOUNDATION.md','docs/platform/data/DP-0-P2-BASELINE-ALIGNMENT.md','docs/platform/data/DP-0-HANDOFF.md','docs/platform/data/DATA-PLATFORM-ARCHITECTURE-V1.md','docs/platform/data/PLATFORM-EVENT-CONTRACT-V1.md','docs/platform/data/BEHAVIOR-EVENT-TAXONOMY-V1.md','docs/platform/data/EVENT-IDEMPOTENCY-AND-FINGERPRINT-V1.md','docs/platform/data/EVENT-RETRY-QUARANTINE-REPLAY-V1.md','docs/platform/data/DATA-LINEAGE-AND-SNAPSHOT-V1.md','docs/platform/data/DATA-RETENTION-AND-PRIVACY-V1.md','docs/platform/data/P0-RECOMMENDATION-EVENT-ADAPTER-V1.md','docs/platform/data/DP-5-PROJECTION-AND-SNAPSHOT-FOUNDATION.md','docs/platform/data/DP-5-PROJECTION-MATRIX.md','docs/platform/data/DP-5-HANDOFF.md','docs/platform/data/DP-6-DATA-QUALITY-AND-LINEAGE-VALIDATION-HARDENING.md','docs/platform/data/DP-6-QUALITY-MATRIX.md','docs/platform/data/DP-6-HANDOFF.md','docs/platform/data/DP-7-CROSS-TRACK-INTEGRATION-VALIDATION.md','docs/platform/data/DP-7-INTEGRATION-MATRIX.md','docs/platform/data/DP-7-AUTHORITY-MATRIX.md','docs/platform/data/DP-7-PRIVACY-RETENTION-MATRIX.md','docs/platform/data/DP-7-HANDOFF.md','docs/platform/proposals/DP-0-TRACK-CHANGE-PROPOSAL.md','docs/platform/data/DATA-PLATFORM-TECHNICAL-BASELINE-V1.md','docs/platform/data/DATA-PLATFORM-AUTHORITY-CLOSURE-V1.md','docs/platform/data/DATA-PLATFORM-PRODUCTION-READINESS-GAPS-V1.md','docs/platform/data/DATA-PLATFORM-PRODUCTION-ACTIVATION-DEPENDENCIES-V1.md','docs/platform/data/DATA-PLATFORM-CHANGE-POLICY-V1.md','docs/platform/data/HANDOFF-DATA-TO-RECOMMENDATION-V1.md','docs/platform/data/HANDOFF-DATA-TO-INTELLIGENCE-V1.md','docs/platform/data/HANDOFF-DATA-TO-SEARCH-V1.md','docs/platform/data/HANDOFF-DATA-TO-OPERATIONS-V1.md','docs/platform/data/HANDOFF-DATA-TO-RELIABILITY-V1.md','docs/platform/data/DATA-PLATFORM-CLOSURE-HANDOFF.md']
ALLOWED=('docs/platform/governance/','docs/platform/data/','docs/platform/proposals/','verification/sc-dp1-baseline-reconciliation/','verification/dp1/','verification/dp2/','verification/dp3/','verification/dp4/','verification/dp4-5/','verification/dp5/','verification/dp6/','verification/dp7/','verification/data-platform-closure/','.github/workflows/','jc-backend/settings.gradle.kts','jc-backend/src/test/java/com/jc/backend/search/shadow/production/IP12ProductionShadowStaticTest.java','jc-data-contracts/','database/journey-connect-db-v2.7/')
def fail(m): raise SystemExit('FAIL: '+m)
for rel in REQUIRED:
 if not (ROOT/rel).is_file(): fail('missing '+rel)
for rel in ('docs/platform/governance/JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md','docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md','docs/platform/data/DP-0-DATA-PLATFORM-CONTRACT-FOUNDATION.md','docs/platform/data/DP-0-P2-BASELINE-ALIGNMENT.md'):
 text=(ROOT/rel).read_text(encoding='utf-8')
 if 'journey-connect-db-v2.7/01..28' not in text: fail('historical marker '+rel)
 if 'jc-data-contracts' not in text or 'com.jc.data.contract' not in text: fail('module marker '+rel)
gov=(ROOT/'docs/platform/governance/JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md').read_text(encoding='utf-8')
if 'IP 기술 기준선 종결\n→ DP\n→ OP\n→ RP\n→ 교차 트랙 통합 검증' not in gov: fail('sequence')
if 'historical recommendation' not in gov: fail('historical recommendation')
alloc={'SC-DP4-5-PERSISTENCE-ALLOCATION.md':('Implementation authority: `GRANTED`','35_data_recommendation_adapter_shadow_evidence.sql','SQL `01..34` remains protected','SQL `38+` remains unallocated'),'SC-DP5-PROJECTION-ALLOCATION.md':('Implementation authority: `GRANTED`','38_data_projection_snapshot_foundation.sql','jc_data_projection_writer'),'SC-DP6-QUALITY-ALLOCATION.md':('APPROVED / MERGED','Implementation authority: `GRANTED`','43_data_quality_validation_foundation.sql','data-quality-policy-v1'),'SC-DP7-CROSS-TRACK-INTEGRATION-ALLOCATION.md':('APPROVED / MERGED','Implementation authority: `GRANTED`','d18c91a28b271c9f9891b522c6371017a3d0dd79','48_cross_track_integration_validation_foundation.sql','52_cross_track_integration_validation.sql','jc_data_integration_writer','data-cross-track-integration-policy-v1','SQL `01..47` remains protected','SQL `53+` remains unallocated')}
for name,markers in alloc.items():
 text=(ROOT/'docs/platform/governance'/name).read_text(encoding='utf-8')
 for m in markers:
  if m not in text: fail(name+' '+m)
link=re.compile(r'\[[^\]]+\]\(([^)]+)\)')
for rel in REQUIRED:
 p=ROOT/rel
 for target in link.findall(p.read_text(encoding='utf-8')):
  if '://' in target or target.startswith('#'): continue
  clean=target.split('#',1)[0]
  if clean and not (p.parent/clean).resolve().exists(): fail('broken link '+target+' in '+rel)
reg=(ROOT/'docs/platform/governance/SC-PLATFORM-REGISTRY.md').read_text(encoding='utf-8')
for cid in ('platform-event-v1','data-platform-architecture-v1','event-idempotency-fingerprint-v1','data-lineage-snapshot-v1','recommendation-profile-input-v1','experiment-outcome-input-v1','data-projection-snapshot-v1','data-quality-policy-v1','data-quality-verdict-sha256-v1','data-cross-track-integration-policy-v1','data-cross-track-integration-run-v1','data-cross-track-integration-check-v1','data-cross-track-contract-mapping-v1','data-cross-track-authority-matrix-v1','data-cross-track-privacy-retention-matrix-v1','data-cross-track-integration-verdict-v1','integration-input-sha256-v1','integration-check-evidence-sha256-v1','integration-mapping-sha256-v1','integration-verdict-sha256-v1','cross-track-contract-matrix-sha256-v1','data-platform-technical-baseline-v1','data-platform-change-policy-v1'):
 if cid not in reg: fail('registry '+cid)
sql=ROOT/'database/journey-connect-db-v2.7'
for n in range(1,53):
 if len(list(sql.glob(f'{n:02d}_*.sql')))!=1: fail(f'SQL {n:02d}')
if list(sql.glob('5[3-9]_*.sql')) or list(sql.glob('[6-9][0-9]_*.sql')): fail('SQL53+')
subprocess.run(['git','fetch','origin','main','--depth=1'],cwd=ROOT,check=False,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
diff=subprocess.run(['git','diff','--name-only','origin/main...HEAD'],cwd=ROOT,check=True,text=True,capture_output=True).stdout.splitlines()
for rel in filter(None,diff):
 if not any(rel==p or rel.startswith(p) for p in ALLOWED): fail('unexpected '+rel)
changed={r for r in diff if r.startswith('database/') and r.endswith('.sql')}
expected={str(next(sql.glob(f'{n:02d}_*.sql')).relative_to(ROOT)).replace('\\','/') for n in range(48,53)}
if changed and changed!=expected: fail('successor SQL '+str(sorted(changed)))
if any(r.startswith(('jc-backend/src/main/','jc-recommendation-core/','jc-intelligence-contracts/','jc-search-')) for r in diff): fail('protected source')
for p in OUT.glob('*.tsv'):
 with p.open(encoding='utf-8',newline='') as h: rows=list(csv.reader(h,delimiter='\t'))
 if not rows: fail('empty '+str(p))
print('SC baseline reconciliation through Data Platform technical closure: PASS')
