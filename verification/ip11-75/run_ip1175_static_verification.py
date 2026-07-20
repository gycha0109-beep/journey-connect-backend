from pathlib import Path
import csv, hashlib, re, sys, os
root=Path(__file__).resolve().parents[2]
ver=root/'verification/ip11-75'
docs=root/'docs/platform/intelligence'
checks=[]
def ck(name,cond,detail=''):
    checks.append((name,bool(cond),detail))

def rows(path):
    with path.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
D=rows(ver/'IP1175_DECISION_REGISTER.tsv')
R=rows(ver/'IP1175_RACI_MATRIX.tsv')
G=rows(ver/'IP1175_GO_NO_GO_MATRIX.tsv')
ck('decision_count_12',len(D)==12,str(len(D)))
ck('decision_unique',len({r['decision_id'] for r in D})==12)
ck('owners_nonempty',all(r['owner'].strip() for r in D))
ck('raci_count_12',len(R)==12,str(len(R)))
ck('raci_one_accountable',all(r['accountable_count']=='1' for r in R))
ck('gng_count_20',len(G)==20,str(len(G)))
alltext='\n'.join(p.read_text(encoding='utf-8',errors='replace') for p in docs.glob('IP-11*.md'))
for token in ['14일','30일','0 BPS','10 BPS','Production shadow: DISABLED','Search cutover: NOT STARTED','NO_GO','EXTERNAL_ATTESTATION_PENDING']:
    ck('token_'+token,token in alltext)
for token in ['Raw query','raw identity']:
    ck('prohibition_'+token,token.lower() in alltext.lower())
for name in [
'IP-11-75-GOVERNANCE-DECISION-CLOSURE.md','IP-11-75-APPROVED-OWNER-RACI-AND-AUTHORITY.md','IP-11-75-RETENTION-PRIVACY-AND-ACCESS-POLICY.md','IP-11-75-RESOURCE-SAMPLING-AND-COHORT-APPROVAL.md','IP-11-75-OBSERVABILITY-AND-OPERATIONAL-READINESS.md','IP-11-75-GO-NO-GO-RECLASSIFICATION.md','IP-11-75-VERIFICATION-AND-SELF-REVIEW.md','IP-11-75-HANDOFF.md']:
    ck('doc_'+name,(docs/name).is_file())
# markdown link validation in intelligence docs
bad=[]
for p in docs.glob('*.md'):
    t=p.read_text(encoding='utf-8',errors='replace')
    for m in re.finditer(r'\[[^\]]+\]\(([^)]+)\)',t):
        link=m.group(1).split('#',1)[0]
        if not link or '://' in link or link.startswith('mailto:'):continue
        target=(p.parent/link).resolve()
        if not target.exists():bad.append(f'{p.name}:{link}')
ck('markdown_links',not bad,';'.join(bad[:10]))

# Manifest/no-change evidence
def read_tsv(path):
    with path.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))
for fname,label in [
 ('IP1175_PRODUCTION_SOURCE_MANIFEST.tsv','production_source_no_change'),
 ('IP1175_PRODUCTION_CONFIG_MANIFEST.tsv','production_config_no_change'),
 ('IP1175_GRADLE_BUILD_LOGIC_MANIFEST.tsv','gradle_build_no_change'),
 ('IP1175_SQL_01_28_MANIFEST.tsv','sql_01_28_no_change'),
 ('IP1175_SQL_01_26_MANIFEST.tsv','sql_01_26_exact'),
 ('IP1175_SQL_27_28_MANIFEST.tsv','sql_27_28_exact'),
 ('IP1175_PROTECTED_SOURCE_RESULT.tsv','protected_source_exact')]:
    rr=read_tsv(ver/fname); ck(label,bool(rr) and all(x['status']=='PASS' for x in rr),str(len(rr)))
source_diff=(ver/'IP1175_SOURCE_ONLY_DIFF.tsv').read_text(encoding='utf-8').splitlines()
ck('source_only_diff_zero',len(source_diff)==1,str(len(source_diff)-1))
# Approved policy exact values
policy=(ver/'IP1175_APPROVED_POLICY_SUMMARY.txt').read_text(encoding='utf-8')
for token in ['retention_aggregate=14 days','retention_error_summary=30 days','effective_sampling=0 BPS','pilot_ceiling=10 BPS','actual_cohort=empty / 0%','production_shadow=DISABLED']:
    ck('policy_'+token,token in policy)
# Production control source remains capability-only; governance notes broader provisional sample capability
resource=(ver/'IP1175_RESOURCE_BUDGET_CHECK.txt').read_text(encoding='utf-8')
for token in ['core=1','max=2','queue=8','runtime=200ms','hard<=300ms','candidates=100','current_sample=0bps','pilot_ceiling=10bps']:
    ck('resource_'+token,token in resource)

# production guard text
ck('current_sample_zero','effective production sampling: `0 BPS`' in alltext or 'Effective production sampling: 0 BPS' in alltext)
ck('actual_cohort_empty','actual cohort' in alltext.lower() and ('empty' in alltext.lower()))
ck('no_go','`NO_GO`' in alltext)
for n,ok,d in checks:
    print(('PASS' if ok else 'FAIL')+'\t'+n+('\t'+d if d else ''))
fail=sum(not x[1] for x in checks)
print(f'SUMMARY\tchecks={len(checks)}\tpass={len(checks)-fail}\tfail={fail}')
sys.exit(1 if fail else 0)
