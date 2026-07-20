from __future__ import annotations
from pathlib import Path
import hashlib, re, sys

ROOT = Path(__file__).resolve().parents[2]
checks = 0
failures: list[str] = []

def check(condition: bool, label: str) -> None:
    global checks
    checks += 1
    if not condition:
        failures.append(label)

def text(rel: str) -> str:
    return (ROOT / rel).read_text(encoding='utf-8')

def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()

controller = text('jc-backend/src/main/java/com/jc/backend/post/PostController.java')
sequence = [
    'PageResponse<PostDtos.Summary> legacyResponse = postService.explore(keyword, region, pageable);',
    'exploreSearchShadowBridge.afterExplore(keyword, region, pageable, legacyResponse);',
    'return ApiResponse.ok(legacyResponse);',
]
positions = [controller.find(x) for x in sequence]
check(all(p >= 0 for p in positions), 'controller controlled hook sequence exists')
check(positions == sorted(positions), 'controller sequence order')
check(controller.count('postService.explore(keyword, region, pageable)') == 1, 'service call exactly once')
check('SearchShadowDispatchReceiptV1' not in controller, 'receipt not used by controller')

config = text('jc-backend/src/main/java/com/jc/backend/search/shadow/SearchShadowBackendConfiguration.java')
check('new DisabledExploreSearchShadowBridge()' in config, 'default disabled bridge')
for forbidden in ('@Profile', '@ConditionalOnProperty', 'DefaultExploreSearchShadowBridge'):
    check(forbidden not in config, f'configuration excludes {forbidden}')

active = text('jc-backend/src/main/java/com/jc/backend/search/shadow/DefaultExploreSearchShadowBridge.java')
check('catch (RuntimeException ignored)' in active, 'runtime exception containment')
check('catch (Error' not in active and 'catch (Throwable' not in active, 'fatal errors not swallowed')
for annotation in ('@Component', '@Service', '@Configuration', '@Bean'):
    check(annotation not in active, f'active bridge not auto registered: {annotation}')

factory = text('jc-backend/src/main/java/com/jc/backend/search/shadow/DefaultExploreShadowHookRequestFactory.java')
check('pageable.getPageNumber() != legacyResponse.page()' in factory, 'page number binding')
check('pageable.getPageSize() != legacyResponse.size()' in factory, 'page size binding')
check('SearchCursor' not in factory, 'no production cursor')

build = text('jc-backend/build.gradle.kts')
for token in (
    'implementation(project(":jc-search-shadow-wiring"))',
    'tasks.register<Test>("ip9BackendHookContractTest")',
    'tasks.register("ip9ControlledBackendHookRegression")',
    '"ip8SearchRegressionClosure"',
    '"ip1CompatibilityContractTest"',
    '"ip9BackendHookContractTest"',
):
    check(token in build, f'build contains {token}')
for token in ('ignoreFailures', 'isIgnoreFailures', 'ForkJoinPool', 'new Thread', 'Executors.newCachedThreadPool'):
    check(token not in build, f'build excludes {token}')

# Production resource config remains free of Search shadow activation.
for path in (ROOT / 'jc-backend/src/main/resources').rglob('*'):
    if path.is_file():
        value = path.read_text(encoding='utf-8', errors='replace')
        check('search.shadow' not in value and 'search-shadow-test' not in value,
              f'no production activation in {path.relative_to(ROOT)}')

# Protected manifests.
for manifest_rel, expected_count in (
    ('verification/ip8/IP8_PROTECTED_BASELINE_EXPECTED_SHA256.txt', 320),
    ('verification/ip8/IP8_SQL_01_26_EXPECTED_SHA256.txt', 26),
):
    lines = [x for x in text(manifest_rel).splitlines() if x.strip()]
    check(len(lines) == expected_count, f'{manifest_rel} count')
    for line in lines:
        expected, rel = line.split(None, 1)
        check(sha(rel) == expected, f'exact hash {rel}')

# Pre-IP9 backend manifest: only Controller may differ.
pre_lines = [x for x in text('verification/ip9/IP9_PRECHANGE_BACKEND_PROTECTED_SHA256.txt').splitlines() if x.strip()]
approved = 0
for line in pre_lines:
    expected, rel = line.split(None, 1)
    actual = sha(rel)
    if rel.endswith('/PostController.java'):
        check(actual != expected, 'controller approved delta exists')
        approved += 1
    else:
        check(actual == expected, f'backend protected exact {rel}')
check(approved == 1, 'exactly one approved backend protected delta')

# No forbidden infrastructure in the new backend package.
for path in (ROOT / 'jc-backend/src/main/java/com/jc/backend/search/shadow').glob('*.java'):
    value = path.read_text(encoding='utf-8')
    for token in ('EntityManager', 'JdbcTemplate', 'Repository', 'Kafka', 'search_exposure_v1', 'UUID.randomUUID', 'System.currentTimeMillis', 'ForkJoinPool', 'new Thread'):
        check(token not in value, f'{path.name} excludes {token}')

# Docs and relative links.
docs = ROOT / 'docs/platform/intelligence'
required_docs = [
    'IP-9-CONTROLLED-BACKEND-HOOK-IMPLEMENTATION.md',
    'IP-9-DISABLED-MODE-AND-FAILURE-ISOLATION-CONTRACT.md',
    'IP-9-VERIFICATION-AND-SELF-REVIEW.md',
    'IP-9-HANDOFF.md',
]
for name in required_docs:
    check((docs / name).is_file(), f'doc exists {name}')
for path in docs.glob('*.md'):
    value = path.read_text(encoding='utf-8')
    for target in re.findall(r'\[[^\]]+\]\(([^)]+\.md)(?:#[^)]+)?\)', value):
        if '://' not in target and not target.startswith('/'):
            check((path.parent / target).resolve().is_file(), f'valid link {path.name}->{target}')

# Contract IDs in document-info rows remain unique and well formed.
ids: dict[str, str] = {}
for path in docs.glob('*.md'):
    value = path.read_text(encoding='utf-8')
    for match in re.finditer(r'^\|\s*계약 ID\s*\|\s*`([^`]+)`\s*\|', value, re.MULTILINE):
        cid = match.group(1)
        check(bool(re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*-v[0-9]+', cid)), f'contract id format {cid}')
        check(cid not in ids, f'unique contract id {cid}')
        ids[cid] = path.name

# Wrapper and task preparation.
check((ROOT/'jc-backend/gradlew').is_file(), 'gradlew present')
check((ROOT/'jc-backend/gradlew.bat').is_file(), 'gradlew.bat present')
check((ROOT/'jc-backend/gradle/wrapper/gradle-wrapper.jar').is_file(), 'wrapper jar present')
wrapper = text('jc-backend/gradle/wrapper/gradle-wrapper.properties')
check('gradle-8.14.5-bin.zip' in wrapper, 'Gradle 8.14.5 wrapper')
check((ROOT/'jc-backend/gradlew').stat().st_mode & 0o111 != 0, 'gradlew executable')

print(f'IP-9 static verification checks={checks}')
if failures:
    for failure in failures:
        print(f'FAIL: {failure}')
    print(f'status=FAIL failures={len(failures)}')
    sys.exit(1)
print('status=PASS')
