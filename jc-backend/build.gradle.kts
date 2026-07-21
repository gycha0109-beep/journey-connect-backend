import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.journeyconnect"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":jc-recommendation-core"))
    implementation(project(":jc-intelligence-contracts"))
    implementation(project(":jc-search-shadow-wiring"))
    implementation(project(":jc-search-production-controls"))
    // Spring Boot, JPA, Security, Validation, OpenAPI를 함께 묶어 API 서버의 기본 실행 환경을 구성합니다.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin 설정 클래스와 Spring 프록시를 Java 도메인 코드와 함께 사용하기 위한 런타임 의존성입니다.
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.hibernate.orm:hibernate-spatial")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register<Test>("p0Verification") {
    group = "verification"
    description = "Runs P0 through P0-8 source, SQL, and architecture contracts in Java."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform {
        includeTags("p0-verification")
    }
}


tasks.register<Test>("p1ContractVerification") {
    group = "verification"
    description = "Runs P1 static, mode, and candidate mapping contracts."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.jc.backend.verification.P1RecommendationStaticTest")
        includeTestsMatching("com.jc.backend.recommendation.application.RecommendationP1ModeDeciderTest")
        includeTestsMatching("com.jc.backend.recommendation.application.RecommendationP1CandidateMapperTest")
    }
}

tasks.register<Test>("p1PreferenceIntegration") {
    group = "verification"
    description = "Runs authenticated P1 cold-start preference replacement integration."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.jc.backend.recommendation.application.RecommendationPreferenceIntegrationTest")
    }
}

tasks.register<Test>("p1BehaviorProfileIntegration") {
    group = "verification"
    description = "Runs persisted behavior to P1 profile PostgreSQL integration."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.jc.backend.recommendation.application.RecommendationP1BehaviorProfileIntegrationTest")
    }
}

tasks.register<Test>("p1ShadowIntegration") {
    group = "verification"
    description = "Runs the P1 SHADOW PostgreSQL integration gate."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.jc.backend.recommendation.application.RecommendationP1ShadowIntegrationTest")
    }
}

tasks.register<Test>("p1CanaryIntegration") {
    group = "verification"
    description = "Runs the P1 CANARY, rollback, and repeatability PostgreSQL integration gate."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.jc.backend.recommendation.application.RecommendationP1CanaryIntegrationTest")
    }
}

tasks.register("p1Verification") {
    group = "verification"
    description = "Runs all P1 contracts as isolated verification tasks."
    dependsOn("p1ContractVerification", "p1PreferenceIntegration", "p1BehaviorProfileIntegration", "p1ShadowIntegration", "p1CanaryIntegration")
}


tasks.register<Test>("p2ContractVerification") {
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter { includeTestsMatching("com.jc.backend.verification.P2RecommendationStaticTest") }
}

tasks.register<Test>("p2EvaluationIntegration") {
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter { includeTestsMatching("com.jc.backend.recommendation.application.RecommendationP2EvaluationIntegrationTest") }
}

tasks.register("p2Verification") {
    group = "verification"
    dependsOn("p2ContractVerification", "p2EvaluationIntegration")
}


tasks.register<JavaExec>("ip1CompatibilityContractTest") {
    group = "verification"
    description = "Runs IP-1 recommendation compatibility and source authority contract assertions."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.backend.intelligence.compat.recommendation.RecommendationCompatibilityContractTestMain")
    dependsOn(tasks.testClasses)
}

tasks.check {
    dependsOn("ip1CompatibilityContractTest")
}

/**
 * IP-8 unified dependency-free Search Intelligence regression closure.
 * Backend Spring/Testcontainers regression remains an explicit separate command.
 */
tasks.register("ip8SearchRegressionClosure") {
    group = "verification"
    description = "Runs IP-1/IP-3..IP-8 Search contracts and protected recommendation-core regressions."
    dependsOn(
        ":jc-intelligence-contracts:intelligenceContractTest",
        ":jc-search-contracts:searchDomainContractTest",
        ":jc-search-compatibility:searchCompatibilityContractTest",
        ":jc-search-runtime:searchRuntimeContractTest",
        ":jc-search-integration:searchIntegrationContractTest",
        ":jc-search-shadow-wiring:searchShadowWiringContractTest",
        ":jc-search-readiness:searchReadinessRegressionContractTest",
        ":jc-recommendation-core:coreFoundationContractTest",
        ":jc-recommendation-core:coreWave1ContractTest",
        ":jc-recommendation-core:coreWave2ScoringContractTest",
        ":jc-recommendation-core:coreWave3RankingDiversityContractTest",
        ":jc-recommendation-core:coreWave3ExplorationContractTest",
        ":jc-recommendation-core:coreWave4RankingIntegrationContractTest",
        ":jc-recommendation-core:coreWave5ExposureContractTest",
        ":jc-recommendation-core:coreWave6AttributionContractTest",
        ":jc-recommendation-core:coreWave7OfflineEvaluationContractTest",
        ":jc-recommendation-core:javaCoreGoldenFixtureContractTest",
        ":jc-recommendation-core:javaCoreIsolationContractTest",
        ":jc-recommendation-core:p1CoreContractTest",
        ":jc-recommendation-core:p2CoreContractTest",
    )
}


/** IP-9 controlled backend hook and disabled-mode contract tests. */
tasks.register<Test>("ip9BackendHookContractTest") {
    group = "verification"
    description = "Runs IP-9 controlled explore hook, disabled wiring, and failure-isolation tests."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.jc.backend.post.PostControllerSearchShadowHookTest")
        includeTestsMatching("com.jc.backend.search.shadow.ExploreSearchShadowBridgeContractTest")
        includeTestsMatching("com.jc.backend.search.shadow.SearchShadowBackendConfigurationTest")
        includeTestsMatching("com.jc.backend.verification.IP9ControlledBackendHookStaticTest")
    }
}

tasks.register("ip9ControlledBackendHookRegression") {
    group = "verification"
    description = "Runs IP-8 Search closure, IP-1 backend compatibility, and IP-9 controlled backend hook regression."
    dependsOn(
        "ip8SearchRegressionClosure",
        "ip1CompatibilityContractTest",
        "ip9BackendHookContractTest",
    )
}

/** IP-10 explicit test/stage Search shadow activation tests. */
tasks.register<Test>("ip10TestStageShadowActivationRegression") {
    group = "verification"
    description = "Runs IP-10 test/stage active Search shadow wiring, bounded execution, and production-guard tests."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.jc.backend.search.shadow.stage.*")
        includeTestsMatching("com.jc.backend.verification.IP10TestStageShadowStaticTest")
    }
}

/** IP-9 external attestation plus IP-10 test/stage activation and complete backend verification closure. */
tasks.register("ip10CombinedExternalRegressionClosure") {
    group = "verification"
    description = "Runs IP-9/IP-10 Search closure, backend test/check, and P0/P1/P2 verification without ignoring failures."
    dependsOn(
        "ip9ControlledBackendHookRegression",
        "ip10TestStageShadowActivationRegression",
        "p0Verification",
        "p1Verification",
        "p2Verification",
        "check",
    )
}


/** IP-11.5 authoritative projection and fail-closed eligibility contracts. */
tasks.register<Test>("ip115ProjectionAndEligibilityContractTest") {
    group = "verification"
    description = "Runs IP-11.5 projection, projector, eligibility, runtime-provider, and SQL static contracts."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses, ":jc-search-production-controls:productionShadowTechnicalContractTest")
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.jc.backend.search.shadow.production.ProductionProjectionAndEligibilityContractTest")
        includeTestsMatching("com.jc.backend.search.shadow.production.IP115ProductionShadowStaticTest")
    }
}

/** IP-11.5 kill-switch, empty cohort, zero-sampling, and emergency-disable regression. */
tasks.register<Test>("ip115KillSwitchAndCohortRegression") {
    group = "verification"
    description = "Runs IP-11.5 default-killed switch, internal cohort, zero sampling, and disable-drill tests."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.jc.backend.search.shadow.production.ProductionKillSwitchCohortRegressionTest")
        includeTestsMatching("com.jc.backend.search.shadow.production.ProductionShadowTechnicalConfigurationTest")
    }
}

/** IP-9/IP-10 protected closure plus all IP-11.5 technical readiness contracts. */
tasks.register("ip115ProductionShadowTechnicalReadinessRegression") {
    group = "verification"
    description = "Runs protected IP-9/IP-10 regressions and IP-11.5 projection, control, profile, and drill contracts."
    dependsOn(
        "ip9ControlledBackendHookRegression",
        "ip10TestStageShadowActivationRegression",
        "ip115ProjectionAndEligibilityContractTest",
        "ip115KillSwitchAndCohortRegression",
    )
}

/** Full external attestation gate; remains NOT EXECUTED when Gradle/PostgreSQL execution is skipped. */
tasks.register("ip115CombinedExternalAttestation") {
    group = "verification"
    description = "Runs IP-11.5 technical readiness with backend check and P0/P1/P2 PostgreSQL verification."
    dependsOn(
        "ip115ProductionShadowTechnicalReadinessRegression",
        "p0Verification",
        "p1Verification",
        "p2Verification",
        "check",
    )
}

/** IP-12 production property, allowlist, sampling, Micrometer, and resource unit contracts. */
tasks.register<Test>("verifyIp12ProductionShadowWiring") {
    group = "verification"
    description = "Runs IP-12 production properties, account hashing, operational gate, Micrometer, and resource isolation tests."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.jc.backend.search.shadow.production.ProductionSearchShadowPropertiesTest")
        includeTestsMatching("com.jc.backend.search.shadow.production.ProductionInternalAccountHashResolverTest")
        includeTestsMatching("com.jc.backend.search.shadow.production.MicrometerSearchShadowMetricSinkTest")
        includeTestsMatching("com.jc.backend.search.shadow.production.ProductionSearchShadowResourceIsolationTest")
        includeTestsMatching("com.jc.backend.search.shadow.production.IP12ProductionShadowStaticTest")
    }
}

tasks.register<Test>("verifyIp12SamplingCeiling") {
    group = "verification"
    description = "Runs the IP-12 0..10 BPS production sampling ceiling and 10/10000 determinism contract."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter { includeTestsMatching("com.jc.backend.search.shadow.production.ProductionSearchShadowSamplingGateTest") }
}

tasks.register<Test>("verifyIp12SpringWiring") {
    group = "verification"
    description = "Runs default, stage-conflict, production-disabled, and invalid-production Spring bean graph tests."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter { includeTestsMatching("com.jc.backend.search.shadow.production.ProductionSearchShadowConfigurationTest") }
}

tasks.register<Test>("verifyIp12DisableDrill") {
    group = "verification"
    description = "Runs the production-equivalent internal fixture dispatch and emergency kill-switch disable drill."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter { includeTestsMatching("com.jc.backend.search.shadow.production.ProductionSearchShadowDisableDrillTest") }
}

tasks.register<Test>("verifyIp125InternalPilotReadiness") {
    group = "verification"
    description = "Runs IP-12.5 operational input, approval, activation-window, rollback-owner, and hold-state gates."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.jc.backend.search.shadow.production.ProductionSearchShadowPilotReadinessTest")
        includeTestsMatching("com.jc.backend.search.shadow.production.ProductionSearchShadowPropertiesTest")
        includeTestsMatching("com.jc.backend.search.shadow.production.ProductionSearchShadowConfigurationTest")
        includeTestsMatching("com.jc.backend.search.shadow.production.IP12ProductionShadowStaticTest")
    }
}

tasks.register("verifyIp125") {
    group = "verification"
    description = "Runs the full protected IP-12 closure and IP-12.5 internal-pilot readiness gates."
    dependsOn("verifyIp12", "verifyIp125InternalPilotReadiness")
}

tasks.register("verifyIp12") {
    group = "verification"
    description = "Runs IP-9, IP-10, IP-11.5 and all IP-12 operational wiring gates with backend and P0/P1/P2 checks."
    dependsOn(
        "ip9ControlledBackendHookRegression",
        "ip10TestStageShadowActivationRegression",
        "ip115ProductionShadowTechnicalReadinessRegression",
        "verifyIp12ProductionShadowWiring",
        "verifyIp12SamplingCeiling",
        "verifyIp12SpringWiring",
        "verifyIp12DisableDrill",
        "p0Verification",
        "p1Verification",
        "p2Verification",
        "check",
    )
}
