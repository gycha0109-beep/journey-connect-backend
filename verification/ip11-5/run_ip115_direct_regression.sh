#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${TMPDIR:-/tmp}/jc-ip115-direct-classes"
SOURCES="${TMPDIR:-/tmp}/jc-ip115-direct-sources.txt"
rm -rf "$OUT"; mkdir -p "$OUT"; : > "$SOURCES"
for module in jc-intelligence-contracts jc-search-contracts jc-search-compatibility jc-search-runtime jc-search-integration jc-search-shadow-wiring jc-search-readiness jc-search-production-controls jc-recommendation-core; do
  find "$ROOT/$module/src/main/java" "$ROOT/$module/src/test/java" -name '*.java' 2>/dev/null >> "$SOURCES" || true
done
sort -u "$SOURCES" -o "$SOURCES"
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d "$OUT" @"$SOURCES"
echo "DIRECT_COMPILE_PASS sources=$(wc -l < "$SOURCES")"
run(){ local cwd="$1"; local clazz="$2"; echo "=== $clazz ==="; (cd "$cwd" && java -cp "$OUT" "$clazz"); }
for clazz in \
 com.jc.intelligence.contract.IntelligenceContractsContractTest \
 com.jc.intelligence.contract.search.SearchDomainContractsContractTest \
 com.jc.intelligence.compat.search.explore.LegacyExploreCompatibilityContractTest \
 com.jc.intelligence.runtime.search.SearchRuntimeContractTest \
 com.jc.intelligence.integration.search.SearchIntegrationContractTest \
 com.jc.intelligence.wiring.search.SearchShadowWiringContractTest \
 com.jc.intelligence.readiness.search.SearchShadowReadinessContractTest \
 com.jc.intelligence.production.search.ProductionShadowTechnicalContractTest; do run "$ROOT" "$clazz"; done
for clazz in \
 com.jc.recommendation.foundation.CoreFoundationContractTest \
 com.jc.recommendation.foundation.CoreWave1ContractTest \
 com.jc.recommendation.foundation.CoreWave2ScoringContractTest \
 com.jc.recommendation.foundation.CoreWave3RankingDiversityContractTest \
 com.jc.recommendation.foundation.CoreWave3ExplorationContractTest \
 com.jc.recommendation.foundation.CoreWave4RankingIntegrationContractTest \
 com.jc.recommendation.foundation.CoreWave5ExposureContractTest \
 com.jc.recommendation.foundation.CoreWave6AttributionContractTest \
 com.jc.recommendation.foundation.CoreWave7OfflineEvaluationContractTest \
 com.jc.recommendation.foundation.JavaCoreGoldenFixtureContractTest \
 com.jc.recommendation.foundation.JavaCoreIsolationContractTest \
 com.jc.recommendation.p1.P1CoreContractTest \
 com.jc.recommendation.p2.P2CoreContractTest; do run "$ROOT/jc-recommendation-core" "$clazz"; done
