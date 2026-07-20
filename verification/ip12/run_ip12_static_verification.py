#!/usr/bin/env python3
from pathlib import Path
import csv, hashlib, re, sys
ROOT=Path(__file__).resolve().parents[2]
V=ROOT/'verification/ip12'
checks=[]
def check(name, ok, detail=''):
    checks.append((name,bool(ok),str(detail)))
def text(rel): return (ROOT/rel).read_text(encoding='utf-8')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

required_docs=[
'IP-12-CONTROLLED-PRODUCTION-SHADOW-OPERATIONAL-WIRING.md',
'IP-12-PRODUCTION-PROPERTY-AND-ACTIVATION-CONTRACT.md',
'IP-12-TEN-BPS-ENFORCEMENT-AND-SAMPLING-CONTRACT.md',
'IP-12-INTERNAL-ACCOUNT-ALLOWLIST-IMPLEMENTATION.md',
'IP-12-KILL-SWITCH-AND-DISABLE-DRILL.md',
'IP-12-MICROMETER-AND-STRUCTURED-LOGGING.md',
'IP-12-SECURITY-PRIVACY-AND-FAILURE-ISOLATION-REVIEW.md',
'IP-12-VERIFICATION-AND-SELF-REVIEW.md',
'IP-12-GO-NO-GO-DELTA.md','IP-12-HANDOFF.md']
for n in required_docs: check('doc:'+n,(ROOT/'docs/platform/intelligence'/n).is_file())
for n in ['IP-11-9-EXTERNAL-ATTESTATION-CORRECTION.md','IP-11-9-EXTERNAL-ATTESTATION-AND-OPERATIONAL-READINESS-CLOSURE.md','IP-11-9-EXECUTION-ENVIRONMENT-AND-ATTESTATION-RESULT.md','IP-11-9-GO-NO-GO-REASSESSMENT.md','IP-11-9-HANDOFF.md','IP-11-9-ACCOUNT-ALLOWLIST-CONTRACT.md','IP-11-9-KILL-SWITCH-RESTART-AND-DISABLE-DRILL.md']:
    check('ip119_doc:'+n,(ROOT/'docs/platform/intelligence'/n).is_file())

p=text('jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowProperties.java')
v=text('jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowPropertiesValidator.java')
y=text('jc-backend/src/main/resources/application-prod.yml')
hook=text('jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionExploreSearchShadowHook.java')
gate=text('jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowOperationalGate.java')
log=text('jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowOperationalLogger.java')
metric=text('jc-backend/src/main/java/com/jc/backend/search/shadow/production/MicrometerSearchShadowMetricSink.java')
config=text('jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowConfiguration.java')
controller=text('jc-backend/src/main/java/com/jc/backend/post/PostController.java')
resource=text('jc-search-production-controls/src/main/java/com/jc/intelligence/production/search/v1/ProductionShadowResourcePolicyV1.java')
executor=text('jc-search-production-controls/src/main/java/com/jc/intelligence/production/search/v1/ProductionShadowTaskExecutor.java')

check('default_enabled_false','private boolean enabled;' in p and 'enabled: ${JC_SEARCH_SHADOW_PRODUCTION_ENABLED:false}' in y)
check('default_kill_true','private boolean killSwitch = true;' in p and 'KILL_SWITCH:true' in y)
check('default_sample_zero','private int samplingBps;' in p and 'SAMPLING_BPS:0' in y)
check('maximum_approved_10','APPROVED_MAXIMUM_SAMPLING_BPS = 10' in p and '!= ProductionSearchShadowProperties.APPROVED_MAXIMUM_SAMPLING_BPS' in v)
check('sample_range_0_10','properties.getSamplingBps() < 0' in v and 'properties.getSamplingBps() > ProductionSearchShadowProperties.APPROVED_MAXIMUM_SAMPLING_BPS' in v)
check('resource_exact','core=1 and max=2' in v and 'queue capacity must remain 8' in v and '200/300 ms' in v and 'candidate ceiling must remain 100' in v)
check('yaml_resources',all(s in y for s in ['max-approved-sampling-bps: 10','max-candidates: 100','timeout-ms: 200','hard-timeout-ms: 300','core-concurrency: 1','max-concurrency: 2','queue-capacity: 8']))
check('allowlist_default_empty','JC_SEARCH_SHADOW_PRODUCTION_ALLOWLIST_HASHES:' in y)
check('no_fixture_hash_in_resources',not re.search(r'(?i)[0-9a-f]{64}',y))
check('allowlist_sha_validator','[0-9a-f]{64}' in v and 'cannot exceed three accounts' in v)
check('lazy_identity_resolution','accountHashResolver::currentAccountHash' in hook and 'Supplier<Optional<String>> accountHashSupplier' in gate)
check('kill_before_identity',gate.index('safeKilled()') < gate.index('accountHashSupplier.get()'))
check('sample_before_identity',gate.index('effectiveSamplingBps() == 0') < gate.index('accountHashSupplier.get()'))
check('empty_cohort_before_identity',gate.index('!config.hasCohort()') < gate.index('accountHashSupplier.get()'))
check('bounded_executor','new ArrayBlockingQueue<>(policy.queueCapacity())' in executor and 'ThreadPoolExecutor.AbortPolicy' in executor)
check('no_common_pool','ForkJoinPool.commonPool' not in executor and 'newCachedThreadPool' not in executor and 'LinkedBlockingQueue' not in executor)
check('single_completion','AtomicBoolean completionReported' in executor and 'completionReported.compareAndSet(false, true)' in executor)
check('request_thread_no_join','submitTimed' in gate and 'Future.get' not in gate and '.join(' not in gate)
check('metric_prefix','PREFIX = "journey.search."' in metric)
check('metric_tag_prohibition','query|user|account|session|jwt|post|document|trace|correlation' in metric)
check('metric_failure_isolated',metric.count('catch (RuntimeException ignored)') >= 3)
check('safe_logging','allowlistCount' in log and 'raw' not in log.lower() and 'LOGGER.info' in log)
check('production_profile_condition','ProductionSearchShadowProfileCondition.class' in config)
check('no_persistent_evidence','NoOpSearchShadowEvidenceSink' in config and 'JdbcSearchShadowEvidence' not in config)
check('controller_legacy_identity','PageResponse<PostDtos.Summary> legacyResponse = postService.explore' in controller and 'return ApiResponse.ok(legacyResponse);' in controller)
check('controller_no_search_output','SearchShadowDispatchReceiptV1' not in controller)
check('resource_policy_10','maximumSampleBasisPoints != 10' in resource and 'maximumCandidateCount != 100' in resource)
check('actuator_dependency','spring-boot-starter-actuator' in text('jc-backend/build.gradle.kts'))
for task in ['verifyIp12ProductionShadowWiring','verifyIp12SamplingCeiling','verifyIp12SpringWiring','verifyIp12DisableDrill','verifyIp12']:
    check('gradle_task:'+task,('tasks.register' in text('jc-backend/build.gradle.kts') and f'"{task}"' in text('jc-backend/build.gradle.kts')))

sqls=sorted((ROOT/'database/journey-connect-db-v2.7').glob('[0-9][0-9]_*.sql'))
check('sql_count_28',len(sqls)==28,len(sqls))
check('no_sql_29',not any(p.name.startswith('29_') for p in sqls))
check('sql28_trigger_fix',all(s in text('database/journey-connect-db-v2.7/28_search_document_projection_smoke_test.sql') for s in ['DISABLE TRIGGER posts_set_updated_at','updated_at = clock_timestamp()','ENABLE TRIGGER posts_set_updated_at']))

# Direct logs
for rel,needle in [('verification/ip12/IP12_DIRECT_VERIFICATION.log','IP-12 direct operational contract: 41 PASS'),('verification/ip12/IP12_UPSTREAM_DIRECT_REGRESSION.log','P2 core contract: PASS (23 scenarios)')]:
    check('log:'+rel,(ROOT/rel).is_file() and needle in text(rel))
check('backend_stub_compile','BACKEND_IP12_STUB_COMPILE_PASS sources=68' in text('verification/ip12/IP12_DIRECT_VERIFICATION.log'))
check('pure_compile','PURE_COMPILE_PASS sources=586' in text('verification/ip12/IP12_DIRECT_VERIFICATION.log'))

# Protected result
prot=text('verification/ip12/IP12_PROTECTED_SOURCE_RESULT.txt')
check('protected_320','count=320' in prot and 'pass=320' in prot and 'fail=0' in prot)

# Decision IDs
ids=[]
with (ROOT/'verification/ip11/IP11_DECISION_REGISTER.tsv').open(encoding='utf-8') as f:
    for row in csv.DictReader(f,delimiter='\t'): ids.append(row['decision_id'])
check('decision_count_12',len(ids)==12,len(ids)); check('decision_unique',len(ids)==len(set(ids)))

# Markdown relative links
bad=[]
for md in (ROOT/'docs/platform/intelligence').glob('*.md'):
    body=md.read_text(encoding='utf-8')
    for target in re.findall(r'\[[^\]]+\]\(([^)]+)\)',body):
        if '://' in target or target.startswith('#'): continue
        clean=target.split('#')[0]
        if clean and not (md.parent/clean).exists(): bad.append((md.name,target))
check('document_links_valid',not bad,bad[:10])

# Current state consistent
joined='\n'.join(text('docs/platform/intelligence/'+n) for n in required_docs)
for label in ['Production shadow: DISABLED','Effective production sampling: 0 BPS','Actual cohort: empty / 0%','Search cutover: NOT STARTED']:
    check('state:'+label,label in joined)
check('no_traffic_go','NO_GO_FOR_TRAFFIC' in joined and 'production shadow enabled' not in joined.lower())

# Package requirement source path audit (pre-zip)
paths=[p.relative_to(ROOT).as_posix() for p in ROOT.rglob('*') if p.is_file()]
check('source_non_ascii_paths',all(all(ord(ch)<128 for ch in p) for p in paths))
check('source_path_under_220',max(map(len,paths),default=0)<=220,max(map(len,paths),default=0))

out=[]
for name,ok,detail in checks: out.append(f"{'PASS' if ok else 'FAIL'}\t{name}\t{detail}")
out.append(f"SUMMARY\t{sum(ok for _,ok,_ in checks)}/{len(checks)} PASS\t{sum(not ok for _,ok,_ in checks)} FAIL")
log='\n'.join(out)+'\n'; (V/'IP12_STATIC_VERIFICATION.log').write_text(log,encoding='utf-8')
print(log,end='')
sys.exit(1 if any(not ok for _,ok,_ in checks) else 0)
