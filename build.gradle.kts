import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    val kotlinVersion = "2.3.10"

    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
}

group = "no.nav.amt-arrangor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
    maven { setUrl("https://packages.confluent.io/maven/") }
}

val logstashEncoderVersion = "9.0"
val kafkaClientsVersion = "4.2.0"
val shedlockVersion = "7.7.0"
val okHttpVersion = "5.3.2"
val tokenSupportVersion = "6.0.5"
val kotestVersion = "6.1.10"
val testcontainersVersion = "2.0.3"
val mockkVersion = "1.14.9"
val mockOauth2ServerVersion = "3.0.1"
val ktlintVersion = "1.4.1"
val springmockkVersion = "5.0.1"
val commonVersion = "3.2026.04.08_08.37-229807cc181a"
val jacksonModuleKotlinVersion = "3.1.1"

dependencyManagement {
    dependencies {
        dependency("com.squareup.okhttp3:okhttp:$okHttpVersion")
        dependency("com.squareup.okhttp3:mockwebserver:$okHttpVersion")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.springframework.boot:spring-boot-kafka")

    implementation("no.nav.security:token-validation-spring:$tokenSupportVersion")
    implementation("net.javacrumbs.shedlock:shedlock-spring:$shedlockVersion")

    implementation("org.apache.kafka:kafka-clients:$kafkaClientsVersion") {
        exclude("org.xerial.snappy", "snappy-java")
    }

    implementation("tools.jackson.module:jackson-module-kotlin:$jacksonModuleKotlinVersion")

    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("no.nav.common:log:$commonVersion")
    implementation("no.nav.common:token-client:$commonVersion")
    implementation("no.nav.common:rest:$commonVersion")
    implementation("no.nav.common:job:$commonVersion")

    implementation("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")

    testImplementation("no.nav.security:token-validation-spring-test:$tokenSupportVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOauth2ServerVersion")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")

    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("com.ninja-squad:springmockk:$springmockkVersion")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

ktlint {
    version = ktlintVersion
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
    )
}
