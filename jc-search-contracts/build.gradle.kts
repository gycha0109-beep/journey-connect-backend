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
    api(project(":jc-intelligence-contracts"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val searchDomainContractTest = tasks.register<JavaExec>("searchDomainContractTest") {
    group = "verification"
    description = "Runs IP-3 Search domain type, validation, canonicalization, cursor, and fixture contracts."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.intelligence.contract.search.SearchDomainContractsContractTest")
    dependsOn(tasks.testClasses)
}

tasks.check {
    dependsOn(searchDomainContractTest)
}
