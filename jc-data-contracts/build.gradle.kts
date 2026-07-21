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

tasks.check {
    dependsOn(dataContractTest)
}
