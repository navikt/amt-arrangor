import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
}

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

dependencyManagement {
    dependencies {
        dependency(libs.okhttp.get().toString())
        dependency(libs.mockwebserver.get().toString())
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

    implementation(libs.token.validation.spring)
    implementation(libs.shedlock.spring)

    implementation(libs.kafka.clients) {
        exclude("org.xerial.snappy", "snappy-java")
    }

    implementation(libs.tools.jackson.module.kotlin)

    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation(libs.logstash.encoder)

    implementation(libs.nav.common.log)
    implementation(libs.nav.common.token.client)
    implementation(libs.nav.common.rest)
    implementation(libs.nav.common.job)

    implementation("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation(libs.kotest.assertions.core)

    testImplementation(libs.mock.oauth2.server)

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")

    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
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
    version = libs.versions.ktlint.cli.version.get()
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
