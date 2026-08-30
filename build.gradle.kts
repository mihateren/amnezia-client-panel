plugins {
    java
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

