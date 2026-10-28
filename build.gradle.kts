plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "1.9.25"
    id("com.google.cloud.tools.jib") version "3.4.4"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "Wiki_Project"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    // Reactor와 Coroutine을 연결해주는 핵심 라이브러리 (awaitSingle 등 포함)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    // 스프링 데이터 Reactive Redis (Non-blocking Redis 사용 시 필요)
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
    // WebClient 사용을 위한 WebFlux 의존성 추가
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    // 스프링 부트 3.x (하이버네이트 6 버전 대응) 기준
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.8.2")
    // JWT 가공용 라이브러리 (jjwt 최신 스펙)
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
    // 업로드 문서 텍스트 추출 (PDF / DOCX)
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    compileOnly("org.projectlombok:lombok")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // mockito-kotlin 의존성 (부트 기본 test 스위트 외에 코틀린 전용으로 필요)
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.batch:spring-batch-test")
    testImplementation("org.springframework.security:spring-security-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testAnnotationProcessor("org.projectlombok:lombok")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 로컬 전용 비밀값 파일은 컨테이너 이미지에 포함하지 않음 (CI에는 파일 자체가 없지만 로컬 jib 실행 대비)
tasks.processResources {
    if (gradle.startParameter.taskNames.any { it.startsWith("jib") }) {
        exclude("application-local.properties")
    }
}

// 컨테이너 이미지 빌드 (./gradlew jib → GHCR 푸시)
jib {
    from {
        image = "eclipse-temurin:17-jre"
    }
    to {
        image = "ghcr.io/well0924/wiki-backend"
        tags = setOf("latest", System.getenv("GITHUB_SHA") ?: "local")
    }
    container {
        ports = listOf("89")
        // 비밀값은 이미지가 아닌 실행 환경(.env)에서 주입
        environment = mapOf("SPRING_PROFILES_ACTIVE" to "prod")
        jvmFlags = listOf("-Dfile.encoding=UTF-8", "-Duser.timezone=Asia/Seoul")
        user = "1000:1000"
    }
}
