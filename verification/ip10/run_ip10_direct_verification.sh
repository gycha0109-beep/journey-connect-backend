#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$ROOT/verification/ip10/.direct-build"
rm -rf "$TMP"
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT
SEARCH_MAIN="$TMP/search-main"
SEARCH_TEST="$TMP/search-test"
REC_MAIN="$TMP/rec-main"
REC_TEST="$TMP/rec-test"
BACKEND="$TMP/backend"
mkdir -p "$SEARCH_MAIN" "$SEARCH_TEST" "$REC_MAIN" "$REC_TEST" "$BACKEND/stubs" "$BACKEND/classes"

printf 'Journey Connect IP-10 direct verification\nroot=%s\n' "$ROOT"
java -version 2>&1

SEARCH_MODULES=(
  jc-intelligence-contracts jc-search-contracts jc-search-compatibility jc-search-runtime
  jc-search-integration jc-search-shadow-wiring jc-search-readiness
)
: > "$TMP/search-main-sources.txt"
: > "$TMP/search-test-sources.txt"
for module in "${SEARCH_MODULES[@]}"; do
  find "$ROOT/$module/src/main/java" -name '*.java' -type f >> "$TMP/search-main-sources.txt"
  find "$ROOT/$module/src/test/java" -name '*.java' -type f >> "$TMP/search-test-sources.txt"
done
sort -o "$TMP/search-main-sources.txt" "$TMP/search-main-sources.txt"
sort -o "$TMP/search-test-sources.txt" "$TMP/search-test-sources.txt"
printf '[compile] IP-1/IP-3..IP-8 main (-Xlint:all -Werror)\n'
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d "$SEARCH_MAIN" @"$TMP/search-main-sources.txt"
printf 'PASS\n'
printf '[compile] IP-1/IP-3..IP-8 contract runners\n'
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp "$SEARCH_MAIN" -d "$SEARCH_TEST" @"$TMP/search-test-sources.txt"
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
  (cd "$ROOT" && timeout 120s java -cp "$SEARCH_MAIN:$SEARCH_TEST" "$runner")
done

find "$ROOT/jc-recommendation-core/src/main/java" -name '*.java' -type f | sort > "$TMP/rec-main-sources.txt"
find "$ROOT/jc-recommendation-core/src/test/java" -name '*.java' -type f | sort > "$TMP/rec-test-sources.txt"
printf '[compile] Recommendation main (-Xlint:all -Werror)\n'
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
  (cd "$ROOT/jc-recommendation-core" && timeout 120s java -cp "$REC_MAIN:$REC_TEST" "$runner")
done

# Minimal compile stubs for backend-local bridge and explicit test/stage assembly.
mkdir -p \
  "$BACKEND/stubs/com/jc/backend/common" "$BACKEND/stubs/com/jc/backend/post" \
  "$BACKEND/stubs/org/springframework/context/annotation" \
  "$BACKEND/stubs/org/springframework/boot/autoconfigure/condition" \
  "$BACKEND/stubs/org/springframework/core/env" "$BACKEND/stubs/org/springframework/core/type" \
  "$BACKEND/stubs/org/springframework/data/domain"
cat > "$BACKEND/stubs/com/jc/backend/common/PageResponse.java" <<'JAVA'
package com.jc.backend.common;
import java.util.List;
public record PageResponse<T>(List<T> items,int page,int size,long totalElements,int totalPages,boolean last) {}
JAVA
cat > "$BACKEND/stubs/com/jc/backend/post/PostDtos.java" <<'JAVA'
package com.jc.backend.post;
import java.time.Instant;
public final class PostDtos {
 private PostDtos() {}
 public record Author(Long id,String nickname,String profileImageUrl) {}
 public record Summary(Long id,String title,String regionCode,String regionName,String coverImageUrl,long viewCount,long likeCount,long bookmarkCount,Author author,Instant createdAt) {}
}
JAVA
cat > "$BACKEND/stubs/org/springframework/context/annotation/Bean.java" <<'JAVA'
package org.springframework.context.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface Bean { String destroyMethod() default "(inferred)"; }
JAVA
cat > "$BACKEND/stubs/org/springframework/context/annotation/Configuration.java" <<'JAVA'
package org.springframework.context.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface Configuration { boolean proxyBeanMethods() default true; }
JAVA
cat > "$BACKEND/stubs/org/springframework/context/annotation/Conditional.java" <<'JAVA'
package org.springframework.context.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE,ElementType.METHOD})
public @interface Conditional { Class<?>[] value(); }
JAVA
cat > "$BACKEND/stubs/org/springframework/context/annotation/Condition.java" <<'JAVA'
package org.springframework.context.annotation;
import org.springframework.core.type.AnnotatedTypeMetadata;
public interface Condition { boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata); }
JAVA
cat > "$BACKEND/stubs/org/springframework/context/annotation/ConditionContext.java" <<'JAVA'
package org.springframework.context.annotation;
import org.springframework.core.env.Environment;
public interface ConditionContext { Environment getEnvironment(); }
JAVA
cat > "$BACKEND/stubs/org/springframework/boot/autoconfigure/condition/ConditionalOnMissingBean.java" <<'JAVA'
package org.springframework.boot.autoconfigure.condition;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE,ElementType.METHOD})
public @interface ConditionalOnMissingBean { Class<?>[] value() default {}; }
JAVA
cat > "$BACKEND/stubs/org/springframework/boot/autoconfigure/condition/ConditionOutcome.java" <<'JAVA'
package org.springframework.boot.autoconfigure.condition;
public final class ConditionOutcome {
 private final boolean match; private final String message;
 private ConditionOutcome(boolean match,String message){this.match=match;this.message=message;}
 public static ConditionOutcome match(String message){return new ConditionOutcome(true,message);}
 public static ConditionOutcome noMatch(String message){return new ConditionOutcome(false,message);}
 public boolean isMatch(){return match;} public String getMessage(){return message;}
}
JAVA
cat > "$BACKEND/stubs/org/springframework/boot/autoconfigure/condition/SpringBootCondition.java" <<'JAVA'
package org.springframework.boot.autoconfigure.condition;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
public abstract class SpringBootCondition implements Condition {
 public abstract ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata);
 @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata){return getMatchOutcome(context,metadata).isMatch();}
}
JAVA
cat > "$BACKEND/stubs/org/springframework/core/type/AnnotatedTypeMetadata.java" <<'JAVA'
package org.springframework.core.type;
public interface AnnotatedTypeMetadata {}
JAVA
cat > "$BACKEND/stubs/org/springframework/core/env/Environment.java" <<'JAVA'
package org.springframework.core.env;
public interface Environment {
 String[] getActiveProfiles();
 String getProperty(String key);
 String getProperty(String key,String defaultValue);
 <T> T getProperty(String key,Class<T> targetType,T defaultValue);
}
JAVA
cat > "$BACKEND/stubs/org/springframework/data/domain/Pageable.java" <<'JAVA'
package org.springframework.data.domain;
public interface Pageable { int getPageNumber(); int getPageSize(); Sort getSort(); }
JAVA
cat > "$BACKEND/stubs/org/springframework/data/domain/Sort.java" <<'JAVA'
package org.springframework.data.domain;
import java.util.stream.Stream;
public interface Sort { boolean isUnsorted(); Stream<Order> stream(); interface Order { String getProperty(); boolean isAscending(); } }
JAVA
find "$BACKEND/stubs" -name '*.java' -type f | sort > "$BACKEND/stub-sources.txt"
find "$ROOT/jc-backend/src/main/java/com/jc/backend/search/shadow" -name '*.java' -type f | sort > "$BACKEND/backend-shadow-sources.txt"
printf '[compile] IP-9/IP-10 backend-local Search shadow source with minimal framework API stubs\n'
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp "$SEARCH_MAIN" -d "$BACKEND/classes" \
  @"$BACKEND/stub-sources.txt" @"$BACKEND/backend-shadow-sources.txt" \
  "$ROOT/verification/ip10/DirectStageShadowVerification.java"
printf 'PASS\n'
printf '[run] DirectStageShadowVerification\n'
java -cp "$SEARCH_MAIN:$BACKEND/classes" DirectStageShadowVerification
printf 'DIRECT_REGRESSION=PASS\n'
