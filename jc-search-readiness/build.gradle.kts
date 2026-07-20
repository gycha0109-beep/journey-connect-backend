plugins { `java-library` }

group = "com.journeyconnect"
version = "1.0.0"

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
repositories { mavenCentral() }

dependencies {
    api(project(":jc-search-shadow-wiring"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val searchReadinessRegressionContractTest = tasks.register<JavaExec>("searchReadinessRegressionContractTest") {
    group = "verification"
    description = "Runs IP-8 Search shadow activation readiness and disabled-mode regression closure assertions."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.intelligence.readiness.search.SearchShadowReadinessContractTest")
    dependsOn(tasks.testClasses)
}

tasks.check { dependsOn(searchReadinessRegressionContractTest) }
