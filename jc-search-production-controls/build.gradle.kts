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

val productionShadowTechnicalContractTest = tasks.register<JavaExec>("productionShadowTechnicalContractTest") {
    group = "verification"
    description = "Runs IP-11.5 projection, eligibility, kill-switch, cohort, resource, observability, and drill assertions."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.intelligence.production.search.ProductionShadowTechnicalContractTest")
    dependsOn(tasks.testClasses)
}

tasks.check { dependsOn(productionShadowTechnicalContractTest) }
