#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d /tmp/jc-ip9-direct.XXXXXX)"
trap 'rm -rf "$TMP"' EXIT
MAIN="$TMP/search-main"
TEST="$TMP/search-test"
REC_MAIN="$TMP/rec-main"
REC_TEST="$TMP/rec-test"
BRIDGE="$TMP/bridge"
mkdir -p "$MAIN" "$TEST" "$REC_MAIN" "$REC_TEST" "$BRIDGE/stubs" "$BRIDGE/classes"

printf 'Journey Connect IP-9 direct verification\n'
printf 'root=%s\n' "$ROOT"
java -version 2>&1

SEARCH_MODULES=(
  jc-intelligence-contracts
  jc-search-contracts
  jc-search-compatibility
  jc-search-runtime
  jc-search-integration
  jc-search-shadow-wiring
  jc-search-readiness
)
: > "$TMP/search-main-sources.txt"
: > "$TMP/search-test-sources.txt"
for module in "${SEARCH_MODULES[@]}"; do
  find "$ROOT/$module/src/main/java" -name '*.java' -type f >> "$TMP/search-main-sources.txt"
  find "$ROOT/$module/src/test/java" -name '*.java' -type f >> "$TMP/search-test-sources.txt"
done
sort -o "$TMP/search-main-sources.txt" "$TMP/search-main-sources.txt"
sort -o "$TMP/search-test-sources.txt" "$TMP/search-test-sources.txt"
printf '[compile] IP-1/IP-3..IP-8 main\n'
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d "$MAIN" @"$TMP/search-main-sources.txt"
printf 'PASS\n'
printf '[compile] IP-1/IP-3..IP-8 contract runners\n'
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp "$MAIN" -d "$TEST" @"$TMP/search-test-sources.txt"
printf 'PASS\n'
SEARCH_RUNNERS=(
  com.jc.intelligence.contract.IntelligenceContractsContractTest
  com.jc.intelligence.contract.search.SearchDomainContractsContractTest
  com.jc.intelligence.compat.search.explore.LegacyExploreCompatibilityContractTest
  com.jc.intelligence.runtime.search.SearchRuntimeContractTest
  com.jc.intelligence.integration.search.SearchIntegrationContractTest
  com.jc.intelligence.wiring.search.SearchShadowWiringContractTest
  com.jc.intelligence.readiness.search.SearchShadowReadinessContractTest
)
for runner in "${SEARCH_RUNNERS[@]}"; do
  printf '[run] %s\n' "$runner"
  (cd "$ROOT" && java -cp "$MAIN:$TEST" "$runner")
done

find "$ROOT/jc-recommendation-core/src/main/java" -name '*.java' -type f | sort > "$TMP/rec-main-sources.txt"
find "$ROOT/jc-recommendation-core/src/test/java" -name '*.java' -type f | sort > "$TMP/rec-test-sources.txt"
printf '[compile] Recommendation main\n'
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d "$REC_MAIN" @"$TMP/rec-main-sources.txt"
printf 'PASS\n'
printf '[compile] Recommendation contract runners\n'
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp "$REC_MAIN" -d "$REC_TEST" @"$TMP/rec-test-sources.txt"
printf 'PASS\n'
REC_RUNNERS=(
  com.jc.recommendation.foundation.CoreFoundationContractTest
  com.jc.recommendation.foundation.CoreWave1ContractTest
  com.jc.recommendation.foundation.CoreWave2ScoringContractTest
  com.jc.recommendation.foundation.CoreWave3RankingDiversityContractTest
  com.jc.recommendation.foundation.CoreWave3ExplorationContractTest
  com.jc.recommendation.foundation.CoreWave4RankingIntegrationContractTest
  com.jc.recommendation.foundation.CoreWave5ExposureContractTest
  com.jc.recommendation.foundation.CoreWave6AttributionContractTest
  com.jc.recommendation.foundation.CoreWave7OfflineEvaluationContractTest
  com.jc.recommendation.foundation.JavaCoreGoldenFixtureContractTest
  com.jc.recommendation.foundation.JavaCoreIsolationContractTest
  com.jc.recommendation.p1.P1CoreContractTest
  com.jc.recommendation.p2.P2CoreContractTest
)
for runner in "${REC_RUNNERS[@]}"; do
  printf '[run] %s\n' "$runner"
  (cd "$ROOT/jc-recommendation-core" && java -cp "$REC_MAIN:$REC_TEST" "$runner")
done

# Minimal compile stubs for the backend-local bridge package only. The full Spring backend remains an external Gradle attestation.
mkdir -p \
  "$BRIDGE/stubs/com/jc/backend/common" \
  "$BRIDGE/stubs/com/jc/backend/post" \
  "$BRIDGE/stubs/org/springframework/context/annotation" \
  "$BRIDGE/stubs/org/springframework/data/domain"
cat > "$BRIDGE/stubs/com/jc/backend/common/PageResponse.java" <<'JAVA'
package com.jc.backend.common;
import java.util.List;
public record PageResponse<T>(List<T> items,int page,int size,long totalElements,int totalPages,boolean last) {}
JAVA
cat > "$BRIDGE/stubs/com/jc/backend/post/PostDtos.java" <<'JAVA'
package com.jc.backend.post;
import java.time.Instant;
public final class PostDtos {
 private PostDtos() {}
 public record Author(Long id,String nickname,String profileImageUrl) {}
 public record Summary(Long id,String title,String regionCode,String regionName,String coverImageUrl,long viewCount,long likeCount,long bookmarkCount,Author author,Instant createdAt) {}
}
JAVA
cat > "$BRIDGE/stubs/org/springframework/context/annotation/Bean.java" <<'JAVA'
package org.springframework.context.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface Bean {}
JAVA
cat > "$BRIDGE/stubs/org/springframework/context/annotation/Configuration.java" <<'JAVA'
package org.springframework.context.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface Configuration { boolean proxyBeanMethods() default true; }
JAVA
cat > "$BRIDGE/stubs/org/springframework/data/domain/Pageable.java" <<'JAVA'
package org.springframework.data.domain;
public interface Pageable { int getPageNumber(); int getPageSize(); Sort getSort(); }
JAVA
cat > "$BRIDGE/stubs/org/springframework/data/domain/Sort.java" <<'JAVA'
package org.springframework.data.domain;
import java.util.stream.Stream;
public interface Sort {
 boolean isUnsorted(); Stream<Order> stream();
 interface Order { String getProperty(); boolean isAscending(); }
}
JAVA
cat > "$BRIDGE/DirectBridgeVerification.java" <<'JAVA'
import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.backend.search.shadow.*;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.wiring.search.v1.NoOpSearchShadowHook;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
public final class DirectBridgeVerification {
 private static int checks;
 public static void main(String[] args) {
  Pageable matching = pageable(0,20);
  PageResponse<PostDtos.Summary> response = response(0,20);
  AtomicReference<Object> captured = new AtomicReference<>();
  DefaultExploreShadowHookRequestFactory factory = new DefaultExploreShadowHookRequestFactory(() ->
   new ExploreShadowRequestContext("request:ip9","correlation:ip9",null,
    Instant.parse("2026-07-19T00:00:00Z"),Instant.parse("2026-07-19T00:00:00Z"),new ProducerBuildId("ip9-direct-build")));
  DefaultExploreSearchShadowBridge success = new DefaultExploreSearchShadowBridge(factory, request -> {
   captured.set(request.legacyResponse());
   return new NoOpSearchShadowHook<PageResponse<PostDtos.Summary>>().dispatch(request);
  });
  success.afterExplore("서울","KR-SEOUL",matching,response);
  check(captured.get()==response,"legacy identity");
  AtomicInteger failedHookCalls = new AtomicInteger();
  DefaultExploreSearchShadowBridge hookFailure = new DefaultExploreSearchShadowBridge(factory, request -> {
   failedHookCalls.incrementAndGet(); throw new IllegalStateException("hook");
  });
  hookFailure.afterExplore(null,null,matching,response);
  check(failedHookCalls.get()==1,"hook failure contained after dispatch");
  AtomicInteger mismatchHookCalls = new AtomicInteger();
  new DefaultExploreSearchShadowBridge(factory, request -> { mismatchHookCalls.incrementAndGet(); return null; })
   .afterExplore(null,null,pageable(1,20),response);
  check(mismatchHookCalls.get()==0,"page metadata mismatch isolated");
  new DefaultExploreSearchShadowBridge((a,b,c,d)->{throw new IllegalStateException("factory");}, request->null)
   .afterExplore(null,null,matching,response);
  check(true,"factory failure contained");
  try {
   new DefaultExploreSearchShadowBridge((a,b,c,d)->{throw new AssertionError("fatal");}, request->null)
    .afterExplore(null,null,matching,response);
   throw new AssertionError("fatal swallowed");
  } catch (AssertionError expected) { check("fatal".equals(expected.getMessage()),"fatal Error propagated"); }
  new DisabledExploreSearchShadowBridge().afterExplore(null,null,matching,response);
  check(true,"disabled no-op");
  System.out.println("IP-9 direct bridge assertions: "+checks+" PASS");
 }
 private static Pageable pageable(int page,int size){ return new Pageable(){
  public int getPageNumber(){return page;} public int getPageSize(){return size;}
  public Sort getSort(){return new Sort(){public boolean isUnsorted(){return true;} public Stream<Sort.Order> stream(){return Stream.empty();}};}
 };}
 private static PageResponse<PostDtos.Summary> response(int page,int size){ return new PageResponse<>(List.of(new PostDtos.Summary(
  1L,"title","KR-SEOUL","서울",null,0L,0L,0L,new PostDtos.Author(2L,"author",null),Instant.parse("2026-07-19T00:00:00Z"))),page,size,1L,1,true); }
 private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
}
JAVA
find "$BRIDGE/stubs" -name '*.java' -type f | sort > "$BRIDGE/stubs.txt"
find "$ROOT/jc-backend/src/main/java/com/jc/backend/search/shadow" -name '*.java' -type f | sort > "$BRIDGE/bridge.txt"
printf '[compile] backend-local Search shadow bridge with minimal API stubs\n'
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp "$MAIN" -d "$BRIDGE/classes" @"$BRIDGE/stubs.txt" @"$BRIDGE/bridge.txt" "$BRIDGE/DirectBridgeVerification.java"
printf 'PASS\n'
printf '[run] DirectBridgeVerification\n'
java -cp "$MAIN:$BRIDGE/classes" DirectBridgeVerification
printf 'DIRECT_REGRESSION=PASS\n'
