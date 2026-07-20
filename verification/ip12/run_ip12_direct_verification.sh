#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASE="${TMPDIR:-/tmp}/jc-ip12-direct"
PURE="$BASE/pure"
BACK="$BASE/backend"
STUB="$BASE/stubs"
SRC="$BASE/pure-sources.txt"
rm -rf "$BASE"; mkdir -p "$PURE" "$BACK" "$STUB"; : > "$SRC"
for module in jc-intelligence-contracts jc-search-contracts jc-search-compatibility jc-search-runtime jc-search-integration jc-search-shadow-wiring jc-search-readiness jc-search-production-controls jc-recommendation-core; do
  find "$ROOT/$module/src/main/java" "$ROOT/$module/src/test/java" -name '*.java' 2>/dev/null >> "$SRC" || true
done
sort -u "$SRC" -o "$SRC"
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d "$PURE" @"$SRC"
echo "PURE_COMPILE_PASS sources=$(wc -l < "$SRC")"

stub(){ mkdir -p "$STUB/$(dirname "$1")"; cat > "$STUB/$1"; }
stub org/springframework/boot/context/properties/ConfigurationProperties.java <<'EOF'
package org.springframework.boot.context.properties; import java.lang.annotation.*; @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) public @interface ConfigurationProperties { String prefix(); }
EOF
stub org/springframework/boot/context/properties/EnableConfigurationProperties.java <<'EOF'
package org.springframework.boot.context.properties; import java.lang.annotation.*; @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) public @interface EnableConfigurationProperties { Class<?>[] value() default {}; }
EOF
stub org/springframework/boot/ApplicationArguments.java <<'EOF'
package org.springframework.boot; public interface ApplicationArguments { }
EOF
stub org/springframework/boot/ApplicationRunner.java <<'EOF'
package org.springframework.boot; @FunctionalInterface public interface ApplicationRunner { void run(ApplicationArguments args) throws Exception; }
EOF
stub org/springframework/context/annotation/Bean.java <<'EOF'
package org.springframework.context.annotation; import java.lang.annotation.*; @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) public @interface Bean { String destroyMethod() default ""; }
EOF
stub org/springframework/context/annotation/Configuration.java <<'EOF'
package org.springframework.context.annotation; import java.lang.annotation.*; @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) public @interface Configuration { boolean proxyBeanMethods() default true; }
EOF
stub org/springframework/context/annotation/Conditional.java <<'EOF'
package org.springframework.context.annotation; import java.lang.annotation.*; @Target({ElementType.TYPE,ElementType.METHOD}) @Retention(RetentionPolicy.RUNTIME) public @interface Conditional { Class<? extends Condition>[] value(); }
EOF
stub org/springframework/context/annotation/Primary.java <<'EOF'
package org.springframework.context.annotation; import java.lang.annotation.*; @Target({ElementType.TYPE,ElementType.METHOD}) @Retention(RetentionPolicy.RUNTIME) public @interface Primary { }
EOF
stub org/springframework/context/annotation/Condition.java <<'EOF'
package org.springframework.context.annotation; import org.springframework.core.type.AnnotatedTypeMetadata; public interface Condition { boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata); }
EOF
stub org/springframework/context/annotation/ConditionContext.java <<'EOF'
package org.springframework.context.annotation; import org.springframework.core.env.Environment; public interface ConditionContext { Environment getEnvironment(); }
EOF
stub org/springframework/core/type/AnnotatedTypeMetadata.java <<'EOF'
package org.springframework.core.type; public interface AnnotatedTypeMetadata { }
EOF
stub org/springframework/core/env/Environment.java <<'EOF'
package org.springframework.core.env; public interface Environment { String[] getActiveProfiles(); String getProperty(String key); default String getProperty(String key,String d){String v=getProperty(key);return v==null?d:v;} default <T> T getProperty(String key,Class<T> type,T d){return d;} }
EOF
stub org/springframework/boot/autoconfigure/condition/ConditionOutcome.java <<'EOF'
package org.springframework.boot.autoconfigure.condition; public final class ConditionOutcome { private final boolean match; private ConditionOutcome(boolean m){match=m;} public static ConditionOutcome match(String m){return new ConditionOutcome(true);} public static ConditionOutcome noMatch(String m){return new ConditionOutcome(false);} public boolean isMatch(){return match;} }
EOF
stub org/springframework/boot/autoconfigure/condition/SpringBootCondition.java <<'EOF'
package org.springframework.boot.autoconfigure.condition; import org.springframework.context.annotation.*; import org.springframework.core.type.AnnotatedTypeMetadata; public abstract class SpringBootCondition implements Condition { public abstract ConditionOutcome getMatchOutcome(ConditionContext c,AnnotatedTypeMetadata m); public final boolean matches(ConditionContext c,AnnotatedTypeMetadata m){return getMatchOutcome(c,m).isMatch();} }
EOF
stub org/springframework/data/domain/Page.java <<'EOF'
package org.springframework.data.domain; import java.util.List; public interface Page<T>{List<T> getContent();int getNumber();int getSize();long getTotalElements();int getTotalPages();boolean isLast();}
EOF
stub org/springframework/data/domain/Pageable.java <<'EOF'
package org.springframework.data.domain; public interface Pageable { int getPageNumber(); int getPageSize(); Sort getSort(); }
EOF
stub org/springframework/data/domain/Sort.java <<'EOF'
package org.springframework.data.domain; import java.util.*; import java.util.stream.Stream; public class Sort { private final List<Order> orders; public Sort(List<Order> o){orders=o;} public static Sort unsorted(){return new Sort(List.of());} public boolean isUnsorted(){return orders.isEmpty();} public Stream<Order> stream(){return orders.stream();} public static final class Order { private final String p; private final boolean a; public Order(String p,boolean a){this.p=p;this.a=a;} public String getProperty(){return p;} public boolean isAscending(){return a;} } }
EOF
stub org/springframework/data/domain/PageRequest.java <<'EOF'
package org.springframework.data.domain; public final class PageRequest implements Pageable { private final int p,s; private PageRequest(int p,int s){this.p=p;this.s=s;} public static PageRequest of(int p,int s){return new PageRequest(p,s);} public int getPageNumber(){return p;} public int getPageSize(){return s;} public Sort getSort(){return Sort.unsorted();} }
EOF
for a in Valid; do stub jakarta/validation/$a.java <<EOF
package jakarta.validation; import java.lang.annotation.*; @Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.TYPE_USE}) @Retention(RetentionPolicy.RUNTIME) public @interface $a { }
EOF
done
for a in NotBlank Size; do stub jakarta/validation/constraints/$a.java <<EOF
package jakarta.validation.constraints; import java.lang.annotation.*; @Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.TYPE_USE}) @Retention(RetentionPolicy.RUNTIME) public @interface $a { int max() default 2147483647; }
EOF
done
stub org/springframework/dao/DataAccessException.java <<'EOF'
package org.springframework.dao; public class DataAccessException extends RuntimeException { private static final long serialVersionUID=1L; public DataAccessException(String m){super(m);} }
EOF
stub org/springframework/jdbc/core/RowMapper.java <<'EOF'
package org.springframework.jdbc.core; import java.sql.*; @FunctionalInterface public interface RowMapper<T>{T mapRow(ResultSet rs,int rowNum)throws SQLException;}
EOF
stub org/springframework/jdbc/core/JdbcTemplate.java <<'EOF'
package org.springframework.jdbc.core; import java.util.*; public class JdbcTemplate { public <T> List<T> query(String sql,RowMapper<T> mapper,Object... args){return List.of();} public <T> T queryForObject(String sql,Class<T> type,Object... args){return null;} }
EOF
stub org/springframework/security/core/Authentication.java <<'EOF'
package org.springframework.security.core; public interface Authentication { boolean isAuthenticated(); Object getPrincipal(); }
EOF
stub org/springframework/security/core/context/SecurityContext.java <<'EOF'
package org.springframework.security.core.context; import org.springframework.security.core.Authentication; public interface SecurityContext { Authentication getAuthentication(); }
EOF
stub org/springframework/security/core/context/SecurityContextHolder.java <<'EOF'
package org.springframework.security.core.context; public final class SecurityContextHolder { private static SecurityContext c=()->null; public static SecurityContext getContext(){return c;} public static void setContext(SecurityContext x){c=x;} }
EOF
stub org/springframework/security/oauth2/jwt/Jwt.java <<'EOF'
package org.springframework.security.oauth2.jwt; public class Jwt { private final String s; public Jwt(String s){this.s=s;} public String getSubject(){return s;} }
EOF
stub org/slf4j/Logger.java <<'EOF'
package org.slf4j; public interface Logger { default void info(String f,Object... a){} }
EOF
stub org/slf4j/LoggerFactory.java <<'EOF'
package org.slf4j; public final class LoggerFactory { private static final Logger L=new Logger(){}; public static Logger getLogger(Class<?> c){return L;} }
EOF
stub io/micrometer/core/instrument/Tags.java <<'EOF'
package io.micrometer.core.instrument; public final class Tags { public static Tags empty(){return new Tags();} public Tags and(String k,String v){return this;} }
EOF
stub io/micrometer/core/instrument/Counter.java <<'EOF'
package io.micrometer.core.instrument; public interface Counter { void increment(); }
EOF
stub io/micrometer/core/instrument/Timer.java <<'EOF'
package io.micrometer.core.instrument; import java.time.Duration; public interface Timer { void record(Duration d); }
EOF
stub io/micrometer/core/instrument/MeterRegistry.java <<'EOF'
package io.micrometer.core.instrument; public class MeterRegistry { public Counter counter(String n,Tags t){return ()->{};} public Timer timer(String n,Tags t){return d->{};} public <T> T gauge(String n,Tags t,T o){return o;} }
EOF

BACKSRC="$BASE/backend-sources.txt"; : > "$BACKSRC"
find "$STUB" -name '*.java' >> "$BACKSRC"
printf '%s\n' \
 "$ROOT/jc-backend/src/main/java/com/jc/backend/common/PageResponse.java" \
 "$ROOT/jc-backend/src/main/java/com/jc/backend/region/RegionDtos.java" \
 "$ROOT/jc-backend/src/main/java/com/jc/backend/post/PostDtos.java" \
 "$ROOT/jc-backend/src/main/java/com/jc/backend/search/shadow/ExploreSearchShadowBridge.java" \
 "$ROOT/jc-backend/src/main/java/com/jc/backend/search/shadow/ExploreShadowHookRequestFactory.java" \
 "$ROOT/jc-backend/src/main/java/com/jc/backend/search/shadow/ExploreShadowRequestContext.java" \
 "$ROOT/jc-backend/src/main/java/com/jc/backend/search/shadow/ExploreShadowRequestContextProvider.java" \
 "$ROOT/jc-backend/src/main/java/com/jc/backend/search/shadow/DefaultExploreSearchShadowBridge.java" \
 "$ROOT/jc-backend/src/main/java/com/jc/backend/search/shadow/DefaultExploreShadowHookRequestFactory.java" \
 "$ROOT/jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageSearchShadowProperties.java" \
 "$ROOT/jc-backend/src/main/java/com/jc/backend/search/shadow/DisabledSearchShadowActivationCondition.java" >> "$BACKSRC"
find "$ROOT/jc-backend/src/main/java/com/jc/backend/search/shadow/production" -name '*.java' >> "$BACKSRC"
find "$ROOT/verification/ip12/direct-src" -name '*.java' >> "$BACKSRC"
sort -u "$BACKSRC" -o "$BACKSRC"
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp "$PURE" -d "$BACK" @"$BACKSRC"
echo "BACKEND_IP12_STUB_COMPILE_PASS sources=$(wc -l < "$BACKSRC")"
java -cp "$PURE:$BACK" com.jc.backend.search.shadow.production.IP12DirectOperationalContract
