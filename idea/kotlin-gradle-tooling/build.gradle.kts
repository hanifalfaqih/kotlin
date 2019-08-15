
description = "Kotlin Gradle Tooling support"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compile(kotlinStdlib())
    compile(intellijPluginDep("gradle"))
    compileOnly(intellijDep()) { includeJars("slf4j-api-1.7.25") }
//    compile(files("/Users/sergey.rostov/gradle/subprojects/kotlin-dsl-tooling-models/build/libs/gradle-kotlin-dsl-tooling-models-5.7.jar"))

    compileOnly(project(":kotlin-reflect-api"))
    runtime(project(":kotlin-reflect"))
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

runtimeJar()
