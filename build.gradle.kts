plugins {
    java
    jacoco
}

val packageGroup: String by project
val projectVersion: String by project
val projectJavaVersion: String by project

allprojects {
    group  = packageGroup
    version = projectVersion

    repositories {
        mavenLocal()
        maven(url = "https://maven.aliyun.com/repository/public")
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.isIncremental = true
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        testLogging {
            events("passed", "skipped", "failed")
        }
        jvmArgs("--sun-misc-unsafe-memory-access=allow", "-Xshare:off")
    }
}

subprojects {
    apply(plugin = "java")
    java {
        sourceCompatibility = JavaVersion.toVersion(projectJavaVersion)
        targetCompatibility = JavaVersion.toVersion(projectJavaVersion)
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(projectJavaVersion))
        }
    }
}

// Агрегированный отчёт JaCoCo по всем подмодулям
tasks.jacocoTestReport {
    dependsOn(
        subprojects.map { it.tasks.named("test") }
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/test/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
    }

    val excludedClasses = listOf(
        "**/application/**/*",
        "**/entity/**/*",
        "**/qa/**/*",
        "**/exception/**/*",
        "**/config/**/*",
        "**/*Config*.*",
        "**/*Client*.*",
        "**/*Controller.*",
        "**/*ExceptionHandler.*",
        "**/*Queries.*",
        "**/*Consumer.*",
        "**/*Job.*",
        "**/*Utils.*",
        "**/*QueryHolder.*",
        "**/rest/**/*"
    )

    classDirectories.setFrom(
        files(
            subprojects.map { project ->
                fileTree(project.layout.buildDirectory.dir("classes/java/main")) {
                    exclude(excludedClasses)
                }
            }
        )
    )

    sourceDirectories.setFrom(
        files(
            subprojects.map { proj ->
                proj.fileTree("src/main/java")
            }
        )
    )

    executionData.setFrom(
        files(
            subprojects.map { project ->
                fileTree(project.layout.buildDirectory) {
                    include("**/jacoco/*.exec")
                }
            }
        )
    )
}

tasks.build {
    finalizedBy("jacocoTestReport")
}

