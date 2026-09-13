plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.openapi.generator)
    jacoco
    java
}

description = "amnezia-control-panel-api"

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    // Spring Boot
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web) {
        exclude(mapOf("group" to "org.springframework.boot", "module" to "spring-boot-starter-tomcat"))
    }
    implementation(libs.spring.boot.starter.jetty)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.cloud.starter.openfeign)
    implementation(libs.spring.boot.starter.validation)

    // Lombok + Mapstruct
    compileOnly(libs.org.projectlombok.lombok)
    annotationProcessor(libs.org.projectlombok.lombok)
    implementation(libs.org.mapstruct.mapstruct)
    annotationProcessor(libs.org.mapstruct.mapstruct.processor)

    // Logbook
    implementation(libs.logbook.spring.boot.starter) {
        exclude(mapOf("group" to "org.jetbrains", "module" to "annotations"))
    }

    // Jackson 2 (Logbook 3.x is compiled against Jackson 2; Boot 4 defaults to Jackson 3)
    implementation(libs.jackson.databind)

    // Swagger
    implementation(libs.swagger.annotations)

    // Monitoring
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.logstash.logback.encoder)

    // Database
    implementation(libs.sqlite.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.liquibase.core)

    // Commons
    implementation(libs.commons.collections4)
    implementation(libs.commons.io)

    // Test
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.instancio.junit)
    mockitoAgent(libs.org.mockito.mockito.core) { isTransitive = false }
    testImplementation(libs.org.mockito.mockito.core)
    testImplementation(libs.spring.boot.starter.test)

    testAnnotationProcessor(libs.org.projectlombok.lombok)
    testImplementation(libs.org.projectlombok.lombok)
}

tasks {
    bootJar {
        enabled = true
        archiveBaseName.set("example-api") // TODO: change when using this template
    }

    jar {
        enabled = false
    }

    openApiGenerate {
        val baseOpenApiPackage = "org.example.amnezia.controlpanel.api.rest"
        val generatedOpenApiClasses = "${layout.buildDirectory.get().asFile.absolutePath}/generated/openapi"

        generatorName.set("spring")
        inputSpec.set("$rootDir/docs-java/openapi/openapi.yaml")
        outputDir.set(generatedOpenApiClasses)
        apiPackage.set("$baseOpenApiPackage.api")
        modelPackage.set("$baseOpenApiPackage.model")
        invokerPackage.set("$baseOpenApiPackage.invoker")
        configOptions.set(
            mapOf(
                "library" to "spring-boot",
                "interfaceOnly" to "true",
                "artifactId" to "rest-client",
                "hideGenerationTimestamp" to "true",
                "implicitHeadersRegex" to "X-Trace-Id",
                "useJakartaEe" to "true",
                "useTags" to "true",
                "useResponseEntity" to "false",
                "openApiNullable" to "false",
                "skipDefaultInterface" to "true"
            )
        )
        sourceSets {
            main {
                java {
                    srcDir("$generatedOpenApiClasses/src/main/java")
                }
            }
        }
    }

    compileJava {
        dependsOn(openApiGenerate)
    }

    afterEvaluate {
        register<Test>("unitTest") {
            group = "verification"
            testClassesDirs = tasks.test.get().testClassesDirs
            classpath = tasks.test.get().classpath

            useJUnitPlatform()
            jvmArgs.add("-javaagent:${mockitoAgent.asPath}")

            filter {
                excludeTestsMatching("org.example.amnezia.controlpanel.api.component.**")
            }

            extensions.configure<JacocoTaskExtension> {
                isEnabled = true
                includes = listOf("org.example.amnezia.controlpanel.api.**")
            }
        }

        register<Test>("componentTest") {
            group = "verification"
            testClassesDirs = tasks.test.get().testClassesDirs
            classpath = tasks.test.get().classpath

            useJUnitPlatform()
            outputs.upToDateWhen { false }
            jvmArgs.add("-javaagent:${mockitoAgent.asPath}")

            filter {
                includeTestsMatching("org.example.amnezia.controlpanel.api.component.**")
            }

            extensions.configure<JacocoTaskExtension> {
                isEnabled = true
                includes = listOf("org.example.amnezia.controlpanel.api.**")
            }
        }

        named<Test>("test") {
            dependsOn("unitTest", "componentTest")
            onlyIf { false }
        }
    }
}