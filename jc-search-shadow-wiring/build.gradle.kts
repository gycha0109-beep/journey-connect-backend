plugins { `java-library` }

group = "com.journeyconnect"
version = "1.0.0"

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
repositories { mavenCentral() }

dependencies {
    api(project(":jc-search-integration"))
    api(project(":jc-search-compatibility"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val searchShadowWiringContractTest = tasks.register<JavaExec>("searchShadowWiringContractTest") {
    group = "verification"
    description = "Runs IP-7 disabled-by-default Search shadow wiring and controlled comparison assertions."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jc.intelligence.wiring.search.SearchShadowWiringContractTest")
    dependsOn(tasks.testClasses)
}

tasks.check { dependsOn(searchShadowWiringContractTest) }
