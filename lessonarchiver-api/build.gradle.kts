import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktlint by configurations.creating

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ksp)
}

group = "com.lessonarchiver"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

ksp {
    arg("KOIN_DEFAULT_MODULE", "true")
}

kotlin {
    jvmToolchain(20)
}

dependencies {
    implementation(libs.hikaricp)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.migration)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.h2)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.flyway)
    implementation(libs.flyway.postgres)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.postgres)
    implementation(libs.b2.sdk.core)
    implementation(libs.b2.sdk.httpclient)
    implementation(libs.classgraph)
    implementation(kotlin("stdlib-jdk8"))

    api(libs.koin.annotations)
    implementation("io.ktor:ktor-serialization-jackson:3.2.3")
    ksp(libs.koin.ksp.compiler)

    ktlint(libs.ktlint) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()
}
tasks.register<JavaExec>("ktlintFormat") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Check Kotlin code style and format"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args(
        "-F",
        "**/src/**/*.kt",
        "**.kts",
        "!**/build/**",
    )
}
