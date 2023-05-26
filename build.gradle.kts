plugins {
    id("org.springframework.boot") version "3.0.5"
    id("io.spring.dependency-management") version "1.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.spring") version "1.7.22"
}

group = "no.nav.amt-arrangor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { setUrl("https://jitpack.io") }
    maven { setUrl("https://packages.confluent.io/maven/") }
}

val logstashEncoderVersion = "7.3"
val kafkaClientsVersion = "3.4.0"
val shedlockVersion = "5.2.0"
val okHttpVersion = "4.10.0"
val tokenSupportVersion = "3.0.10"
val arrowVersion = "1.2.0-RC"
val kotestVersion = "5.5.5"
val testcontainersVersion = "1.18.1"
val mockkVersion = "1.13.5"

val commonVersion = "3.2023.04.18_10.07-0576b4e09008"

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
    implementation("org.apache.kafka:kafka-clients:$kafkaClientsVersion")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.flywaydb:flyway-core")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.github.navikt.common-java-modules:log:$commonVersion")
    implementation("com.github.navikt.common-java-modules:token-client:$commonVersion")
    implementation("com.github.navikt.common-java-modules:rest:$commonVersion")
    implementation("com.github.navikt.common-java-modules:job:$commonVersion")

    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")

    implementation("io.arrow-kt:arrow-core:$arrowVersion")
    implementation("io.arrow-kt:arrow-fx-coroutines:$arrowVersion")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("com.vaadin.external.google", "android-json")
    }
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("no.nav.security:token-validation-spring-test:$tokenSupportVersion")
    testImplementation("no.nav.security:mock-oauth2-server:0.5.8")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("org.awaitility:awaitility")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
