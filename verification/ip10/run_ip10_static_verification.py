from pathlib import Path
import hashlib, re, sys
ROOT=Path(__file__).resolve().parents[2]
V=ROOT/'verification/ip10'
fail=[]; checks=0
def check(cond,msg):
 global checks
 checks+=1
 if not cond: fail.append(msg)
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def parse_manifest(p):
 out=[]
 for line in p.read_text().splitlines():
  if line.strip():
   h,rel=line.split('  ',1); out.append((h,rel))
 return out
# protected 320
expected=parse_manifest(ROOT/'verification/ip8/IP8_PROTECTED_BASELINE_EXPECTED_SHA256.txt')
cur=[]
for h,rel in expected:
 p=ROOT/rel; check(p.is_file(),f'missing protected {rel}')
 if p.is_file():
  actual=sha(p); cur.append((actual,rel)); check(actual==h,f'protected mismatch {rel}')
(V/'IP10_PROTECTED_SOURCE_CURRENT_SHA256.txt').write_text('\n'.join(f'{h}  {r}' for h,r in cur)+'\n')
check(len(expected)==320,'protected manifest count')
# sql 26 against IP9 manifest
sql_expected=parse_manifest(ROOT/'verification/ip9/IP9_SQL_01_26_CURRENT_SHA256.txt')
sql_cur=[]
for h,rel in sql_expected:
 p=ROOT/rel; check(p.is_file(),f'missing sql {rel}')
 if p.is_file():
  actual=sha(p); sql_cur.append((actual,rel)); check(actual==h,f'sql mismatch {rel}')
(V/'IP10_SQL_01_26_CURRENT_SHA256.txt').write_text('\n'.join(f'{h}  {r}' for h,r in sql_cur)+'\n')
check(len(sql_expected)==26,'sql manifest count')
# backend IP9 baseline
baseline=parse_manifest(V/'IP10_IP9_BACKEND_BASELINE_SHA256.txt')
current=[]
allowed={'jc-backend/build.gradle.kts','jc-backend/src/main/java/com/jc/backend/search/shadow/SearchShadowBackendConfiguration.java'}
for h,rel in baseline:
 p=ROOT/rel; check(p.is_file(),f'missing backend protected {rel}')
 if p.is_file():
  actual=sha(p); current.append((actual,rel))
  if rel not in allowed: check(actual==h,f'unapproved backend delta {rel}')
(V/'IP10_BACKEND_CURRENT_SHA256.txt').write_text('\n'.join(f'{h}  {r}' for h,r in current)+'\n')
# default/prod protection
prod_files=list((ROOT/'jc-backend/src/main/resources').rglob('*'))
prod_text='\n'.join(p.read_text(errors='ignore') for p in prod_files if p.is_file())
check('search.shadow.stage' not in prod_text,'production resources contain stage properties')
check('search-shadow-stage' not in prod_text,'production resources activate stage profile')
# source invariants
stage=ROOT/'jc-backend/src/main/java/com/jc/backend/search/shadow/stage'
files=list(stage.glob('*.java')); check(len(files)==15,'stage production file count')
all_stage='\n'.join(p.read_text() for p in files)
for banned in ['EntityManager','JdbcTemplate','Kafka','Elasticsearch','OpenSearch','ForkJoinPool.commonPool','@Repository','@Service']:
 check(banned not in all_stage,f'banned dependency {banned}')
props=(stage/'StageSearchShadowProperties.java').read_text()
check('DEFAULT_SAMPLE_BASIS_POINTS = 0' in props,'default sample zero')
check('profiles.contains("prod") || profiles.contains("production")' in props,'production guard')
check('SearchShadowWiringMode.TEST_ONLY' in props,'test-only mode')
conf=(stage/'StageSearchShadowConfiguration.java').read_text()
check('@Conditional(StageSearchShadowActivationCondition.class)' in conf,'conditional active config')
check('InMemoryStageSearchCatalog' in conf and 'StageSearchShadowTaskExecutor' in conf,'active graph')
default=(ROOT/'jc-backend/src/main/java/com/jc/backend/search/shadow/SearchShadowBackendConfiguration.java').read_text()
check('@Conditional(DisabledSearchShadowActivationCondition.class)' in default,'disabled reverse condition')
# task graph static
build=(ROOT/'jc-backend/build.gradle.kts').read_text()
for task in ['ip9BackendHookContractTest','ip9ControlledBackendHookRegression','ip10TestStageShadowActivationRegression','ip10CombinedExternalRegressionClosure','ip8SearchRegressionClosure']:
 check(task in build,f'missing task {task}')
check('ignoreFailures' not in build,'ignoreFailures present')
check('ip9ControlledBackendHookRegression' in build[build.find('ip10CombinedExternalRegressionClosure'):],'ip10 closure missing ip9 dependency')
# tests/docs
jtests=list((ROOT/'jc-backend/src/test/java/com/jc/backend/search/shadow/stage').glob('*.java'))
check(len(jtests)==5,'stage junit class count')
method_count=sum(p.read_text().count('@Test') for p in jtests)
check(method_count>=12,'stage junit method count')
new_docs=[
'IP-10-TEST-STAGE-SHADOW-ACTIVATION.md','IP-10-RUNTIME-INPUT-EXECUTOR-AND-EVIDENCE-CONTRACT.md',
'IP-10-COMBINED-EXTERNAL-REGRESSION-CLOSURE.md','IP-10-VERIFICATION-AND-SELF-REVIEW.md','IP-10-HANDOFF.md']
for name in new_docs: check((ROOT/'docs/platform/intelligence'/name).is_file(),f'missing doc {name}')
readme=(ROOT/'docs/platform/intelligence/README.md').read_text()
for name in new_docs: check(name in readme,f'unlinked doc {name}')
# no generated gradle/build dirs
for p in ROOT.rglob('*'):
 rel=p.relative_to(ROOT).as_posix()
 if p.is_dir() and (p.name=='.gradle' or p.name=='build'):
  fail.append(f'generated directory included {rel}')
# contract IDs unique
ids=[]
for name in new_docs:
 text=(ROOT/'docs/platform/intelligence'/name).read_text()
 ids += re.findall(r'`(ip-10-[a-z0-9-]+-v[1-9][0-9]*)`',text)
check(len(ids)==len(set(ids)),'IP10 contract id duplicate')
summary=f'STATIC_CHECKS={checks}\nFAILURES={len(fail)}\n' + ''.join(f'FAIL {x}\n' for x in fail)
(V/'IP10_STATIC_VERIFICATION.log').write_text(summary)
(V/'IP10_PROTECTED_SOURCE_RESULT.txt').write_text(f'protected_source={len(expected)}/{len(expected)} exact\n' if not any(x.startswith('protected') or 'protected mismatch' in x for x in fail) else 'protected_source=FAIL\n')
(V/'IP10_SQL_01_26_RESULT.txt').write_text(f'canonical_sql={len(sql_expected)}/{len(sql_expected)} exact\n' if not any('sql' in x for x in fail) else 'canonical_sql=FAIL\n')
print(summary,end='')
sys.exit(1 if fail else 0)
