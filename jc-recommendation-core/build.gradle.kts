plugins {
    `java-library`
}

group = "com.journeyconnect"
version = "1.2.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

fun registerContractTask(taskName: String, descriptionText: String, mainClassName: String) =
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        description = descriptionText
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set(mainClassName)
        dependsOn(tasks.testClasses)
    }

val coreFoundationContractTest = registerContractTask(
    "coreFoundationContractTest",
    "Runs the dependency-free Java port foundation contract test.",
    "com.jc.recommendation.foundation.CoreFoundationContractTest",
)
val coreWave1ContractTest = registerContractTask(
    "coreWave1ContractTest",
    "Runs the Java port Wave 1 contract test.",
    "com.jc.recommendation.foundation.CoreWave1ContractTest",
)
val coreWave2ScoringContractTest = registerContractTask(
    "coreWave2ScoringContractTest",
    "Runs the Java port Wave 2 scoring contract test.",
    "com.jc.recommendation.foundation.CoreWave2ScoringContractTest",
)
val coreWave3RankingDiversityContractTest = registerContractTask(
    "coreWave3RankingDiversityContractTest",
    "Runs the Java port Wave 3 ranking and diversity contract test.",
    "com.jc.recommendation.foundation.CoreWave3RankingDiversityContractTest",
)
val coreWave3ExplorationContractTest = registerContractTask(
    "coreWave3ExplorationContractTest",
    "Runs the Java port Wave 3 exploration contract test.",
    "com.jc.recommendation.foundation.CoreWave3ExplorationContractTest",
)
val coreWave4RankingIntegrationContractTest = registerContractTask(
    "coreWave4RankingIntegrationContractTest",
    "Runs the Java port Wave 4 ranking integration contract test.",
    "com.jc.recommendation.foundation.CoreWave4RankingIntegrationContractTest",
)
val coreWave5ExposureContractTest = registerContractTask(
    "coreWave5ExposureContractTest",
    "Runs the Java port Wave 5 exposure trace contract test.",
    "com.jc.recommendation.foundation.CoreWave5ExposureContractTest",
)
val coreWave6AttributionContractTest = registerContractTask(
    "coreWave6AttributionContractTest",
    "Runs the Java port Wave 6 behavior and attribution contract test.",
    "com.jc.recommendation.foundation.CoreWave6AttributionContractTest",
)
val coreWave7OfflineEvaluationContractTest = registerContractTask(
    "coreWave7OfflineEvaluationContractTest",
    "Runs the Java Core 1.0 replay and offline evaluation contract test.",
    "com.jc.recommendation.foundation.CoreWave7OfflineEvaluationContractTest",
)
val javaCoreGoldenFixtureContractTest = registerContractTask(
    "javaCoreGoldenFixtureContractTest",
    "Verifies all committed golden fixtures using Java-owned oracles.",
    "com.jc.recommendation.foundation.JavaCoreGoldenFixtureContractTest",
)
val javaCoreIsolationContractTest = registerContractTask(
    "javaCoreIsolationContractTest",
    "Verifies that the recommendation core remains framework-free pure Java.",
    "com.jc.recommendation.foundation.JavaCoreIsolationContractTest",
)

tasks.check {
    dependsOn(
        coreFoundationContractTest,
        coreWave1ContractTest,
        coreWave2ScoringContractTest,
        coreWave3RankingDiversityContractTest,
        coreWave3ExplorationContractTest,
        coreWave4RankingIntegrationContractTest,
        coreWave5ExposureContractTest,
        coreWave6AttributionContractTest,
        coreWave7OfflineEvaluationContractTest,
        javaCoreGoldenFixtureContractTest,
        javaCoreIsolationContractTest,
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}



val p1CoreContractTest = registerContractTask(
    "p1CoreContractTest",
    "Runs the P1 behavior profile, policy, ranking, diversity, and comparison contract test.",
    "com.jc.recommendation.p1.P1CoreContractTest",
)

tasks.named("check") { dependsOn(p1CoreContractTest) }

val p2CoreContractTest = registerContractTask(
    "p2CoreContractTest",
    "Runs the P2 statistical evaluation, gate, and release state contract test.",
    "com.jc.recommendation.p2.P2CoreContractTest",
)

tasks.named("check") { dependsOn(p2CoreContractTest) }
