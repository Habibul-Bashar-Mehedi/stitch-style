plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
}

group = "com.stitchstyle"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    implementation(platform(libs.spring.boot.bom))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.spring.aspects)
    implementation(libs.aspectjweaver)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.mail)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.restclient)
    implementation(libs.spring.boot.starter.session.jdbc)
    implementation(libs.ehcache)

    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.postgresql)

    implementation(libs.bucket4j.core)
    implementation(libs.springdoc.webmvc.ui)

    runtimeOnly(libs.postgresql)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jacoco {
    // 0.8.16 is the first line with Java 25 class-file support.
    toolVersion = libs.versions.jacoco.get()
}

tasks.test {
    // A coverage report is produced after every test run, including a failed one
    // (the agent still writes partial execution data).
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
        csv.required = false
    }
}

/**
 * Optional gate, deliberately not wired into `check` yet: the suite is
 * integration-heavy and the PRD's targets (>= 70% overall, >= 80% on services)
 * are tracked rather than enforced. Run with `./gradlew jacocoTestCoverageVerification`.
 */
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.named<Jar>("jar") {
    enabled = false
}
