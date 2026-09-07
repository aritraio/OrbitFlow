plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.orbitflow"
version = "0.0.1-SNAPSHOT"
description = "OrbitFlow - Multi-User Collaborative Project Hub (modular monolith)"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val springdocVersion = "2.6.0"
val jjwtVersion = "0.12.6"
val jsoupVersion = "1.18.1"

dependencies {
    // Core web + persistence + security
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Scheduling (reminders / digests / outbox)
    implementation("org.springframework.boot:spring-boot-starter-quartz") {
        // Quartz tables are optional; scheduler used with in-memory store by default config
        exclude(group = "com.zaxxer", module = "HikariCP")
    }

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Postgres + H2 (H2 for tests / local fallback)
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // HTML sanitization for Markdown comments / docs
    implementation("org.jsoup:jsoup:$jsoupVersion")

    // AWS S3 SDK v2 (works against MinIO with path-style + endpoint override)
    implementation("software.amazon.awssdk:s3:2.26.12")
    implementation("software.amazon.awssdk:url-connection-client:2.26.12")

    // Jackson JSR310 etc. comes via Boot; explicit datatype for safety
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis") {
        // already present; keeps test classpath explicit
    }
    testImplementation("org.testcontainers:testcontainers:1.20.3")
    testImplementation("org.testcontainers:junit-jupiter:1.20.3")
    testImplementation("org.testcontainers:postgresql:1.20.3")
    testImplementation("org.awaitility:awaitility:4.2.2")
    testRuntimeOnly("com.h2database:h2")

    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // H2-based tests must run sequentially where they share ports; default is fine.
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
