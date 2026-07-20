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
    api(project(":jc-search-runtime"))
    api(project(":jc-search-compatibility"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val searchIntegrationContractTest = tasks.register<JavaExec>("searchIntegrationContractTest") {
    group = "verification"
    description = "Runs IP-6 disabled-by-default Search shadow integration and comparison contract assertions."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.intelligence.integration.search.SearchIntegrationContractTest")
    dependsOn(tasks.testClasses)
}

tasks.check { dependsOn(searchIntegrationContractTest) }
