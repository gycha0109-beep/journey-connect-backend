#!/usr/bin/env python3
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name, ok, detail=''):
    checks.append((name,bool(ok),detail))

def text(rel): return (ROOT/rel).read_text(encoding='utf-8')

props=text('jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowProperties.java')
validator=text('jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowPropertiesValidator.java')
runtime=text('jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowRuntimeConfig.java')
gate=text('jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowOperationalGate.java')
controller=text('jc-backend/src/main/java/com/jc/backend/post/PostController.java')
build=text('jc-backend/build.gradle.kts')
workflow=text('.github/workflows/backend-pr-ci.yml')

check('default_enabled_false', 'private boolean enabled;' in props)
check('default_kill_switch_true', 'private boolean killSwitch = true;' in props)
check('default_sampling_zero', 'private int samplingBps;' in props)
check('approved_maximum_10', 'APPROVED_MAXIMUM_SAMPLING_BPS = 10' in props)
check('sampling_above_10_rejected', 'production sampling must be within 0..10 BPS' in validator)
for field in ['activationApprovalRef','activationApproverRef','activationExecutorRef','rollbackOwnerRef','metricVerificationRef','activationWindowStart','activationWindowEnd']:
    check('property_'+field, field in props)
check('activation_requires_allowlist', 'activation requires a non-empty approved account hash allowlist' in validator)
check('activation_requires_approval', 'activation approval reference is required' in validator)
check('activation_requires_approver', 'activation approver reference is required' in validator)
check('activation_requires_executor', 'activation executor reference is required' in validator)
check('activation_requires_rollback', 'rollback owner reference is required' in validator)
check('activation_requires_metric', 'metric verification reference is required' in validator)
check('activation_requires_window', 'activation window is required' in validator)
check('window_start_before_end', 'activation window start must precede end' in validator)
check('runtime_window_gate', 'activationWindowAllows(clock.instant())' in gate)
check('window_before_identity', gate.index('activationWindowAllows') < gate.index('accountHashSupplier.get'))
check('disabled_before_identity', gate.index('!config.enabled()') < gate.index('accountHashSupplier.get'))
check('kill_before_identity', gate.index('safeKilled()') < gate.index('accountHashSupplier.get'))
check('zero_sample_before_identity', gate.index('effectiveSamplingBps() == 0') < gate.index('accountHashSupplier.get'))
check('controller_legacy_authority', 'ApiResponse.ok(legacyResponse)' in controller and 'afterExplore' in controller)
check('controller_no_properties', 'ProductionSearchShadowProperties' not in controller)
check('ip125_task', 'verifyIp125InternalPilotReadiness' in build and 'tasks.register("verifyIp125")' in build)
check('ci_runs_ip125', './gradlew verifyIp125' in workflow)
for resource in ['application-prod.yml','application-production.yml']:
    r=text('jc-backend/src/main/resources/'+resource)
    check(resource+'_safe_enabled', 'enabled: ${JC_SEARCH_SHADOW_PRODUCTION_ENABLED:false}' in r)
    check(resource+'_safe_kill', 'kill-switch: ${JC_SEARCH_SHADOW_PRODUCTION_KILL_SWITCH:true}' in r)
    check(resource+'_safe_sample', 'sampling-bps: ${JC_SEARCH_SHADOW_PRODUCTION_SAMPLING_BPS:0}' in r)
    check(resource+'_empty_hashes', 'allowlist-hashes: ${JC_SEARCH_SHADOW_PRODUCTION_ALLOWLIST_HASHES:}' in r)
    check(resource+'_no_fixture_hash', not re.search(r'[0-9a-f]{64}', r))

docs=[
 'IP-12-5-INTERNAL-PILOT-ACTIVATION-READINESS.md',
 'IP-12-5-ACTIVATION-AND-ROLLBACK-RUNBOOK.md',
 'IP-12-5-PILOT-EVIDENCE-TEMPLATES.md',
 'IP-12-5-GO-NO-GO-DECISION.md',
 'IP-12-5-HANDOFF.md']
for d in docs: check('doc_'+d, (ROOT/'docs/platform/intelligence'/d).is_file())
all_doc='\n'.join(text('docs/platform/intelligence/'+d) for d in docs)
check('final_decision_hold', 'IP-12.5 HOLD_OPERATIONAL_INPUTS_PENDING' in all_doc)
check('production_disabled', 'Production shadow: DISABLED' in all_doc)
check('effective_zero', 'Effective sampling: 0 BPS' in all_doc)
check('cohort_empty', 'Actual cohort: empty / 0%' in all_doc)
check('no_account_hash_in_docs', not re.search(r'(?<![0-9a-f])[0-9a-f]{64}(?![0-9a-f])', all_doc))
check('sql_count_28', len(list((ROOT/'database/journey-connect-db-v2.7').glob('[0-9][0-9]_*.sql')))==28)
check('no_sql_29', not any(p.name.startswith('29_') for p in (ROOT/'database/journey-connect-db-v2.7').glob('*.sql')))

passed=sum(ok for _,ok,_ in checks)
for n,ok,d in checks: print(('PASS' if ok else 'FAIL')+'\t'+n+'\t'+str(d))
print(f'SUMMARY\t{passed}/{len(checks)} PASS\t{len(checks)-passed} FAIL')
sys.exit(0 if passed==len(checks) else 1)
