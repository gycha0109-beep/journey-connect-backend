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

val searchRuntimeContractTest = tasks.register<JavaExec>("searchRuntimeContractTest") {
    group = "verification"
    description = "Runs IP-5 in-memory Search runtime foundation contract assertions."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.intelligence.runtime.search.SearchRuntimeContractTest")
    dependsOn(tasks.testClasses)
}

tasks.check { dependsOn(searchRuntimeContractTest) }
