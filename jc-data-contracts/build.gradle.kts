plugins {
    `java-library`
}

group = "com.journeyconnect"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(project(":jc-recommendation-core"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val dataContractTest = tasks.register<JavaExec>("dataContractTest") {
    group = "verification"
    description = "Runs dependency-free Data Platform event contract and fixture checks."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.data.contract.DataContractsContractTest")
    dependsOn(tasks.testClasses)
}

val dp2FingerprintContractTest = tasks.register<JavaExec>("dp2FingerprintContractTest") {
    group = "verification"
    description = "Runs the SC-approved DP-2 SHA-256 fingerprint contract checks."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.data.contract.Dp2FingerprintContractTest")
    dependsOn(tasks.testClasses)
}

val dp3RetryPolicyContractTest = tasks.register<JavaExec>("dp3RetryPolicyContractTest") {
    group = "verification"
    description = "Runs the SC-approved DP-3 retry, quarantine and lease contract checks."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.data.contract.Dp3RetryPolicyContractTest")
    dependsOn(tasks.testClasses)
}

val dp4RecommendationAdapterContractTest = tasks.register<JavaExec>("dp4RecommendationAdapterContractTest") {
    group = "verification"
    description = "Runs the DP-4 P0 recommendation shadow adapter compatibility contract checks."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.data.contract.Dp4RecommendationAdapterContractTest")
    dependsOn(tasks.testClasses)
}

val dp5ProjectionContractTest = tasks.register<JavaExec>("dp5ProjectionContractTest") {
    group = "verification"
    description = "Runs DP-5 deterministic projection and snapshot contract checks."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.data.contract.Dp5ProjectionContractTest")
    dependsOn(tasks.testClasses)
}

val dp5ProjectionBoundaryContractTest = tasks.register<JavaExec>("dp5ProjectionBoundaryContractTest") {
    group = "verification"
    description = "Runs DP-5 fail-closed time and identity boundary contract checks."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.data.contract.Dp5ProjectionBoundaryContractTest")
    dependsOn(tasks.testClasses)
}

val dp6DataQualityContractTest = tasks.register<JavaExec>("dp6DataQualityContractTest") {
    group = "verification"
    description = "Runs DP-6 data quality, lineage, rebuild and verdict contract checks."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.data.contract.Dp6DataQualityContractTest")
    dependsOn(tasks.testClasses)
}

tasks.check {
    dependsOn(
        dataContractTest,
        dp2FingerprintContractTest,
        dp3RetryPolicyContractTest,
        dp4RecommendationAdapterContractTest,
        dp5ProjectionContractTest,
        dp5ProjectionBoundaryContractTest,
        dp6DataQualityContractTest,
    )
}
