plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.openapi.generator)
    jacoco
    java
}
group = project.property("packageGroup") as String
version = project.property("projectVersion") as String

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
    testLogging { events("passed", "skipped", "failed") }
    jvmArgs("--sun-misc-unsafe-memory-access=allow", "-Xshare:off")
}
tasks.build { finalizedBy(tasks.jacocoTestReport) }
extra["prometheus-client.version"] = "1.8.0"
val javaFormatter = configurations.create("javaFormatter")
dependencies {
    javaFormatter("com.google.googlejavaformat:google-java-format:1.28.0:all-deps")
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.github.mweirauch:micrometer-jvm-extras:0.2.2")
    implementation(libs.sqlite.jdbc)
    implementation(libs.liquibase.core)
    implementation(libs.jackson.databind)
    implementation(libs.swagger.annotations)
    implementation(libs.commons.io)
    implementation(libs.org.mapstruct.mapstruct)
    annotationProcessor(libs.org.mapstruct.mapstruct.processor)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
    compileOnly(libs.org.projectlombok.lombok)
    annotationProcessor(libs.org.projectlombok.lombok)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(enforcedPlatform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
tasks.bootJar { archiveFileName.set("amnezia-control-panel.jar") }
tasks.jar { enabled = false }
tasks.test { useJUnitPlatform(); jvmArgs("--enable-native-access=ALL-UNNAMED") }
tasks.jacocoTestReport { dependsOn(tasks.test); reports { html.required.set(true); xml.required.set(true) } }
tasks.register<JavaExec>("formatJava") {
    group = "formatting"
    description = "Format backend implementation and tests using Google Java Format"
    classpath = javaFormatter
    mainClass.set("com.google.googlejavaformat.java.Main")
    args("--replace")
    args(fileTree("src") {
        include("**/*.java")
        exclude("**/ControlPanelApi.java", "**/JacksonConfig.java",
                "**/Result.java", "**/Message.java", "**/AppLogicException.java", "**/SqlQueryException.java")
    }.files.map { it.absolutePath })
    listOf("api", "file", "parser", "tree", "util", "code").forEach {
        jvmArgs("--add-exports=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED")
    }
}
openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$rootDir/docs-java/openapi/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("org.example.amnezia.controlpanel.api.rest.api")
    modelPackage.set("org.example.amnezia.controlpanel.api.rest.model")
    configOptions.set(mapOf("interfaceOnly" to "true", "useJakartaEe" to "true", "openApiNullable" to "false", "skipDefaultInterface" to "true", "useTags" to "true", "useResponseEntity" to "true"))
}
sourceSets.main { java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java")) }
tasks.compileJava { dependsOn(tasks.openApiGenerate) }
