plugins {
    `java-library`
}

group = "com.journeyconnect"
version = "1.0.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories { mavenCentral() }

dependencies {
    api(project(":jc-search-contracts"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val searchCompatibilityContractTest = tasks.register<JavaExec>("searchCompatibilityContractTest") {
    group = "verification"
    description = "Runs IP-4 legacy explore read compatibility contract assertions."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.intelligence.compat.search.explore.LegacyExploreCompatibilityContractTest")
    dependsOn(tasks.testClasses)
}

tasks.check { dependsOn(searchCompatibilityContractTest) }
