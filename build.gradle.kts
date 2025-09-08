plugins {
    val kotlinVersion = "2.2.10"

    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
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

val logstashEncoderVersion = "8.1"
val kafkaClientsVersion = "4.0.0"
val shedlockVersion = "6.10.0"
val okHttpVersion = "5.1.0"
val tokenSupportVersion = "5.0.34"
val arrowVersion = "2.1.2"
val kotestVersion = "6.0.3"
val testcontainersVersion = "1.21.3"
val mockkVersion = "1.14.5"
val mockOauth2ServerVersion = "2.2.1"
val ktlintVersion = "1.4.1"
val springmockkVersion = "4.0.2"

val commonVersion = "3.2025.08.18_11.44-04fe318bd185"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
    }

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
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("net.javacrumbs.shedlock:shedlock-spring:$shedlockVersion")

    implementation("no.nav.security:token-validation-spring:$tokenSupportVersion")

    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-clients:$kafkaClientsVersion") {
        exclude("org.xerial.snappy", "snappy-java")
    }

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("no.nav.common:log:$commonVersion")
    implementation("no.nav.common:token-client:$commonVersion")
    implementation("no.nav.common:rest:$commonVersion")
    implementation("no.nav.common:job:$commonVersion")

    implementation("io.arrow-kt:arrow-core:$arrowVersion")
    implementation("io.arrow-kt:arrow-fx-coroutines:$arrowVersion")

    implementation("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("com.vaadin.external.google", "android-json")
    }
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("no.nav.security:token-validation-spring-test:$tokenSupportVersion")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOauth2ServerVersion")
    testImplementation("com.ninja-squad:springmockk:$springmockkVersion")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

ktlint {
    version = ktlintVersion
}

tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
    )
}
