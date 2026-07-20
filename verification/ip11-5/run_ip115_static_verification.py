from pathlib import Path
import hashlib, re, csv, os
ROOT=Path(__file__).resolve().parents[2]
BASE=Path('/mnt/data/ip115_baseline/JC-IP-11-5-Final')
V=ROOT/'verification/ip11-5'
checks=[]
def check(cond,msg):
    checks.append((bool(cond),msg))
    if not cond: print('FAIL',msg)
def sha(p):
    h=hashlib.sha256();
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
def tree(root):
    return {p.relative_to(root).as_posix():sha(p) for p in root.rglob('*') if p.is_file() and not any(x in p.parts for x in ('build','.gradle','.idea','out'))}
base=tree(BASE); cur=tree(ROOT)
# baseline zip
check(hashlib.sha256(Path('/mnt/data/JC-IP-11-5-Final.zip').read_bytes()).hexdigest()=='1c810b64b9098a5f02f68acefdf8101d803514771c6f9ac6f4d0e6a566afa66c','baseline ZIP SHA exact')
# docs
names=[
'IP-11-5-PRODUCTION-SHADOW-TECHNICAL-CONTROL-BLOCKER-CLOSURE.md','IP-11-5-AUTHORITATIVE-SEARCH-READ-PROJECTION.md',
'IP-11-5-VISIBILITY-ELIGIBILITY-AND-RUNTIME-INPUT-CONTRACT.md','IP-11-5-KILL-SWITCH-AND-INTERNAL-COHORT-CONTRACT.md',
'IP-11-5-RESOURCE-BUDGET-AND-OBSERVABILITY-CONTRACT.md','IP-11-5-PRIVACY-SAFE-EVIDENCE-CONTRACT.md',
'IP-11-5-EXTERNAL-ATTESTATION-AND-REGRESSION.md','IP-11-5-VERIFICATION-AND-SELF-REVIEW.md',
'IP-11-5-GO-NO-GO-DELTA.md','IP-11-5-HANDOFF.md']
for n in names: check((ROOT/'docs/platform/intelligence'/n).is_file(),f'doc exists {n}')
check('IP-11.5 Technical Controls' in (ROOT/'docs/platform/intelligence/README.md').read_text(), 'README indexes IP-11.5')
# protected baseline 320
manifest=ROOT/'verification/ip7/IP7_PROTECTED_BASELINE_EXPECTED_SHA256.txt'
lines=[x for x in manifest.read_text().splitlines() if x.strip()]
check(len(lines)==320,'protected manifest 320')
prot=[]
for line in lines:
    h,rel=line.split(maxsplit=1); rel=rel.lstrip('* ')
    p=ROOT/rel
    ok=p.is_file() and sha(p)==h
    # PostController was approved at IP-9 and manifest may predate it.
    if rel=='jc-backend/src/main/java/com/jc/backend/post/PostController.java':
        ok=p.is_file() and 'exploreSearchShadowBridge.afterExplore' in p.read_text() and 'ApiResponse.ok(legacyResponse)' in p.read_text()
    prot.append((rel,h,sha(p) if p.is_file() else 'MISSING',ok))
check(all(x[3] for x in prot),'protected source exact/approved controller delta')
with (V/'IP115_PROTECTED_SOURCE_MANIFEST.tsv').open('w') as f:
    f.write('path\texpected_sha256\tcurrent_sha256\tstatus\n')
    for rel,e,c,ok in prot:f.write(f'{rel}\t{e}\t{c}\t{"PASS" if ok else "FAIL"}\n')
# canonical SQL
sql_expected=[]
for i in range(1,27):
    matches=sorted((ROOT/'database/journey-connect-db-v2.7').glob(f'{i:02d}_*.sql'))
    check(len(matches)==1,f'canonical SQL {i:02d} unique')
    if matches:
        rel=matches[0].relative_to(ROOT).as_posix(); sql_expected.append((rel,base.get(rel),cur.get(rel)))
check(all(e==c and e for _,e,c in sql_expected),'canonical SQL 01..26 exact')
with (V/'IP115_SQL_01_26_MANIFEST.tsv').open('w') as f:
    f.write('path\tbaseline_sha256\tcurrent_sha256\tstatus\n')
    for rel,e,c in sql_expected:f.write(f'{rel}\t{e}\t{c}\t{"PASS" if e==c and e else "FAIL"}\n')
# new migrations
for n in ['27_search_document_projection.sql','28_search_document_projection_smoke_test.sql']:
    p=ROOT/'database/journey-connect-db-v2.7'/n
    check(p.is_file(),f'new migration exists {n}')
with (V/'IP115_NEW_MIGRATION_MANIFEST.tsv').open('w') as f:
    f.write('path\tsha256\tstatus\n')
    for n in ['27_search_document_projection.sql','28_search_document_projection_smoke_test.sql']:
        p=ROOT/'database/journey-connect-db-v2.7'/n;f.write(f'{p.relative_to(ROOT).as_posix()}\t{sha(p)}\tSTATIC_ONLY_DB_NOT_EXECUTED\n')
# diff
allpaths=sorted(set(base)|set(cur)); diff=[]
for rel in allpaths:
    if base.get(rel)!=cur.get(rel): diff.append(('ADD' if rel not in base else 'DELETE' if rel not in cur else 'MODIFY',rel,base.get(rel,''),cur.get(rel,'')))
with (V/'IP115_FULL_PROJECT_DIFF.tsv').open('w') as f:
    f.write('change\tpath\tbaseline_sha256\tcurrent_sha256\n');
    for row in diff:f.write('\t'.join(row)+'\n')
source_ext={'.java','.kt','.kts','.sql','.yml','.yaml','.properties'}
source_diff=[r for r in diff if Path(r[1]).suffix in source_ext]
with (V/'IP115_SOURCE_ONLY_DIFF.tsv').open('w') as f:
    f.write('change\tpath\tbaseline_sha256\tcurrent_sha256\n');
    for row in source_diff:f.write('\t'.join(row)+'\n')
allowed_existing={
'jc-backend/build.gradle.kts','jc-backend/settings.gradle.kts',
'jc-search-readiness/src/test/java/com/jc/intelligence/readiness/search/SearchShadowReadinessContractTest.java',
'docs/platform/intelligence/README.md','docs/platform/intelligence/IP-11-GO-NO-GO-MATRIX.md','verification/ip11/IP11_DECISION_REGISTER.tsv'}
def allowed(rel):
    if rel in allowed_existing:return True
    return rel.startswith(('jc-search-production-controls/','jc-backend/src/main/java/com/jc/backend/search/shadow/production/',
        'jc-backend/src/test/java/com/jc/backend/search/shadow/production/','docs/platform/intelligence/IP-11-5-',
        'verification/ip11-5/')) or rel in {'database/journey-connect-db-v2.7/27_search_document_projection.sql','database/journey-connect-db-v2.7/28_search_document_projection_smoke_test.sql'}
check(all(allowed(r[1]) for r in diff),'full diff limited to approved IP-11.5 scope')
# legacy and config exact
legacy=['jc-backend/src/main/java/com/jc/backend/post/PostController.java','jc-backend/src/main/java/com/jc/backend/post/PostService.java',
'jc-backend/src/main/java/com/jc/backend/post/JourneyPostRepository.java','jc-backend/src/main/java/com/jc/backend/post/PostDtos.java',
'jc-backend/src/main/java/com/jc/backend/common/ApiResponse.java','jc-backend/src/main/kotlin/com/jc/backend/config/SecurityConfig.kt']
for rel in legacy:check(base.get(rel)==cur.get(rel) and base.get(rel) is not None,f'legacy protected exact {rel}')
resources=[r for r in base if r.startswith('jc-backend/src/main/resources/')]
check(all(base[r]==cur.get(r) for r in resources),'production resources exact')
with (V/'IP115_PRODUCTION_CONFIG_DIFF.txt').open('w') as f:f.write('NONE\n' if all(base[r]==cur.get(r) for r in resources) else 'DIFF\n')
# module and task graph
settings=(ROOT/'jc-backend/settings.gradle.kts').read_text(); build=(ROOT/'jc-backend/build.gradle.kts').read_text()
check(settings.count('include(":jc-search-production-controls")')==1 and settings.count('project(":jc-search-production-controls")')==1,'module registered once')
for t in ['ip115ProjectionAndEligibilityContractTest','ip115KillSwitchAndCohortRegression','ip115ProductionShadowTechnicalReadinessRegression','ip115CombinedExternalAttestation']:
    check(t in build,f'Gradle task declared {t}')
# default safety
cfg=(ROOT/'jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowTechnicalConfiguration.java').read_text()
check('DisabledSearchShadowKillSwitch' in cfg and 'EmptyProductionShadowCohortSelector' in cfg and 'productionDefault()' in cfg,'default killed empty zero graph')
check('ProductionShadowTaskExecutor productionShadowTaskExecutor' not in cfg,'default graph has no executor')
condition=(ROOT/'jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionShadowTechnicalCapabilityCondition.java').read_text()
check('profiles.contains("prod") || profiles.contains("production")' in condition,'prod profile guard')
sampling=(ROOT/'jc-search-production-controls/src/main/java/com/jc/intelligence/production/search/v1/ProductionShadowSamplingAuthorization.java').read_text()
check('IP-11.5 cannot grant production sampling approval' in sampling and 'return technicalTestOverride?proposedBasisPoints:0' in sampling,'effective production sample zero enforced')
# privacy/activation scans
new_main=[p for p in (ROOT/'jc-search-production-controls/src/main/java').rglob('*.java')]+[p for p in (ROOT/'jc-backend/src/main/java/com/jc/backend/search/shadow/production').rglob('*.java')]
alltext='\n'.join(p.read_text() for p in new_main)
check('LoggerFactory' not in alltext and '.info(' not in alltext and '.debug(' not in alltext,'no new raw logging surface')
evidence=(ROOT/'jc-search-production-controls/src/main/java/com/jc/intelligence/production/search/v1/PrivacySafeSearchShadowEvidenceV1.java').read_text()
for token in ['rawQuery','normalizedQuery','userId','sessionId','jwt','sourcePostId','documentId','candidateIds']:
    check(token not in evidence,f'evidence schema excludes {token}')
metric=(ROOT/'jc-search-production-controls/src/main/java/com/jc/intelligence/production/search/v1/InMemorySearchShadowMetricSink.java').read_text()
check('(query|user|session|jwt|post|document|correlation)' in metric,'metric forbidden-dimension guard')
controller=(ROOT/'jc-backend/src/main/java/com/jc/backend/post/PostController.java').read_text()
check('ProductionShadowTechnicalGate' not in controller,'Controller unaware of IP-11.5 wiring')
check('SearchRuntime' not in controller,'Search result cutover absent')
check('search_exposure_v1' not in alltext,'Search exposure writer absent')
# SQL behavior static
m27=(ROOT/'database/journey-connect-db-v2.7/27_search_document_projection.sql').read_text()
for token in ["p.visibility = 'public'","p.status = 'published'","p.deleted_at IS NULL","p.moderation_status = 'visible'","removed_ineligible_or_missing","hash_mismatch_rejected"]:
    check(token in m27,f'migration fail-closed token {token}')
check('CREATE TRIGGER' not in m27,'no synchronous Post transaction trigger')
# JUnit manifest
jtests=sorted((ROOT/'jc-backend/src/test/java/com/jc/backend/search/shadow/production').glob('*.java'))
with (V/'IP115_JUNIT_CLASS_METHOD_MANIFEST.tsv').open('w') as f:
    f.write('class\ttest_methods\texecution_status\n')
    for p in jtests:
        count=len(re.findall(r'@Test\s+(?:void|public\s+void)\s+([A-Za-z0-9_]+)',p.read_text()))
        f.write(f'{p.stem}\t{count}\tNOT_EXECUTED_USER_DIRECTED_SKIP\n')
        check(count>0,f'JUnit methods declared {p.stem}')
# doc links (relative markdown links only)
for p in [ROOT/'docs/platform/intelligence'/n for n in names]+[ROOT/'docs/platform/intelligence/README.md']:
    text=p.read_text()
    for link in re.findall(r'\[[^\]]+\]\(([^)]+)\)',text):
        if '://' in link or link.startswith('#'):continue
        target=(p.parent/link.split('#')[0]).resolve()
        check(target.exists(),f'doc link valid {p.name}->{link}')
# contract IDs duplicate among IP11.5 docs/source constant strings
ids=[]
for p in [ROOT/'docs/platform/intelligence'/n for n in names]+new_main:
    ids += re.findall(r'\b(?:ip-11-5|search-document|production-shadow)[a-z0-9-]*-v1\b',p.read_text().lower())
# duplicates are references; only ensure core registry values are unique
ids_file=ROOT/'jc-search-production-controls/src/main/java/com/jc/intelligence/production/search/v1/SearchProductionContractIds.java'
vals=re.findall(r'"([a-z0-9-]+-v1)"',ids_file.read_text())
check(len(vals)==len(set(vals)),'production contract registry IDs unique')
# paths
for rel in cur:
    check(len(('JC-IP-11-5-Tech-Final/'+rel).encode('utf-8'))<=240,f'path <=240 {rel}')
    check(all(ord(c)<128 for c in rel),f'ASCII path {rel}')
# results
passed=sum(1 for x,_ in checks if x); failed=len(checks)-passed
(V/'IP115_STATIC_VERIFICATION.log').write_text('\n'.join(f'{"PASS" if ok else "FAIL"}\t{msg}' for ok,msg in checks)+f'\nSUMMARY\t{passed}/{len(checks)} PASS; {failed} FAIL\n')
print(f'IP-11.5 static verification: {passed}/{len(checks)} PASS; {failed} FAIL')
if failed: raise SystemExit(1)
