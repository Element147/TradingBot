plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
}

import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.withType
import org.gradle.process.ExecOperations
import org.springframework.boot.gradle.tasks.run.BootRun
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import javax.inject.Inject

group = "com.algotrader"
version = "0.0.1-SNAPSHOT"

val targetJavaVersion = JavaLanguageVersion.of(25)
val reportsDir = layout.buildDirectory.dir("reports/java-migration")
val mainRuntimeClasspath = providers.provider { sourceSets["main"].runtimeClasspath.asPath }
val mainClassesPath = providers.provider { sourceSets["main"].output.classesDirs.asPath }

java {
    toolchain {
        languageVersion = targetJavaVersion
    }
}

fun osExecutable(name: String): String =
    if (System.getProperty("os.name").lowercase().contains("win")) "$name.exe" else name

val java25Launcher = javaToolchains.launcherFor {
    languageVersion = targetJavaVersion
}

fun jdkBinary(name: String) =
    java25Launcher.map { launcher ->
        launcher.metadata.installationPath.file("bin/${osExecutable(name)}").asFile.absolutePath
    }

abstract class ToolReportExecTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Input
    abstract val toolExecutable: Property<String>

    @get:Input
    abstract val staticArgs: ListProperty<String>

    @get:Classpath
    abstract val toolClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val analysisTargets: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:OutputFile
    abstract val errorFile: RegularFileProperty

    init {
        staticArgs.convention(emptyList())
    }

    @TaskAction
    fun runTool() {
        val output = outputFile.get().asFile
        val error = errorFile.get().asFile

        output.parentFile.mkdirs()
        error.parentFile.mkdirs()

        output.outputStream().use { stdout ->
            error.outputStream().use { stderr ->
                execOperations.exec {
                    executable = toolExecutable.get()
                    args(staticArgs.get())
                    args("--class-path", toolClasspath.asPath, analysisTargets.asPath)
                    standardOutput = stdout
                    errorOutput = stderr
                }
            }
        }
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

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    
    // JWT Authentication
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    
    // OpenAPI/Swagger Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    
    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    
    // Micrometer + Prometheus
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // Logging
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test>().configureEach {
    val jacocoExecFile = layout.buildDirectory.file("jacoco/${name}.exec")

    useJUnitPlatform()
    javaLauncher.set(java25Launcher)
    jvmArgs("-Xshare:off")
    // Enforce H2-backed test profile for all unit/integration tests
    systemProperty("spring.profiles.active", "test")

    extensions.configure(JacocoTaskExtension::class) {
        destinationFile = jacocoExecFile.get().asFile
    }

    doFirst {
        binaryResultsDirectory.get().asFile.mkdirs()
        jacocoExecFile.get().asFile.parentFile.mkdirs()
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("algotrading-bot.jar")
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(java25Launcher)
}

tasks.named<BootRun>("bootRun") {
    val backendDebug = providers.gradleProperty("backendDebug").map(String::toBoolean).orElse(false)
    val backendDebugPort = providers.gradleProperty("backendDebugPort").orElse("5005")
    val backendDebugSuspend = providers.gradleProperty("backendDebugSuspend").orElse("n")

    if (backendDebug.get()) {
        jvmArgs(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=${backendDebugSuspend.get()},address=*:${backendDebugPort.get()}"
        )
    }
}

// JaCoCo Configuration
jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

// Production Validation Task
tasks.register<JavaExec>("validateProduction") {
    group = "verification"
    description = "Run production readiness validation suite"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.algotrader.bot.validation.ValidationSuite")
}

tasks.register<Test>("exportOpenApiContract") {
    group = "verification"
    description = "Export the generated OpenAPI contract to build/openapi/openapi.json"

    useJUnitPlatform()
    javaLauncher.set(java25Launcher)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("spring.profiles.active", "test")
    systemProperty(
        "openapi.output",
        layout.buildDirectory.file("openapi/openapi.json").get().asFile.absolutePath
    )
    filter {
        includeTestsMatching("com.algotrader.bot.controller.OpenApiContractExportTest")
    }
}

tasks.register<JavaExec>("legacyMarketDataFlowAudit") {
    group = "verification"
    description = "Benchmark the legacy CSV-backed dataset flow and write a markdown audit report."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.algotrader.bot.analysis.LegacyMarketDataFlowAuditRunner")
    systemProperty(
        "legacyAudit.output",
        layout.buildDirectory.file("reports/legacy-market-data-flow-audit/report.md").get().asFile.absolutePath
    )
}

tasks.register<JavaExec>("backendWorkflowProfile") {
    group = "verification"
    description = "Profile backend read paths and workflow startup, then write a markdown report."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.algotrader.bot.analysis.BackendWorkflowProfileRunner")
    systemProperty(
        "backendWorkflowProfile.output",
        layout.buildDirectory.file("reports/backend-workflow-profile/report.md").get().asFile.absolutePath
    )
}

tasks.register<JavaExec>("strategyCatalogAudit") {
    group = "verification"
    description = "Rerun the built-in strategy catalog against the frozen audit dataset and write a markdown report."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.algotrader.bot.analysis.StrategyCatalogAuditRunner")
    systemProperty(
        "strategyCatalogAudit.output",
        layout.buildDirectory.file("reports/strategy-catalog-audit/report.md").get().asFile.absolutePath
    )
}

tasks.register<JavaExec>("phaseThreeStrategyAudit") {
    group = "verification"
    description = "Audit the six Phase 3 strategies against the frozen protocol and write a markdown report."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.algotrader.bot.analysis.PhaseThreeStrategyAuditRunner")
    systemProperty(
        "phaseThreeStrategyAudit.output",
        layout.buildDirectory.file("reports/phase-three-strategy-audit/report.md").get().asFile.absolutePath
    )
}

tasks.register<JavaExec>("migrateLegacyDatasets") {
    group = "migration"
    description = "Migrate legacy CSV-backed backtest datasets into the normalized market-data store."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.algotrader.bot.migration.LegacyMarketDataMigrationRunner")
    systemProperty("spring.main.web-application-type", "none")
    providers.gradleProperty("legacyMigrationSpringProfile").orNull?.let { profile ->
        systemProperty("spring.profiles.active", profile)
    }
    systemProperty(
        "legacyMigration.dryRun",
        providers.gradleProperty("legacyMigrationDryRun").orElse("true").get()
    )
    providers.gradleProperty("legacyMigrationDatasetIds").orNull?.let { datasetIds ->
        systemProperty("legacyMigration.datasetIds", datasetIds)
    }
    providers.gradleProperty("legacyMigrationLimit").orNull?.let { limit ->
        systemProperty("legacyMigration.limit", limit)
    }
}

tasks.register<JavaExec>("reconcileLegacyDatasets") {
    group = "migration"
    description = "Reconcile legacy CSV-backed backtest datasets against the normalized market-data store."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.algotrader.bot.migration.LegacyMarketDataReconciliationRunner")
    systemProperty("spring.main.web-application-type", "none")
    providers.gradleProperty("legacyMigrationSpringProfile").orNull?.let { profile ->
        systemProperty("spring.profiles.active", profile)
    }
    providers.gradleProperty("legacyMigrationDatasetIds").orNull?.let { datasetIds ->
        systemProperty("legacyMigration.datasetIds", datasetIds)
    }
    providers.gradleProperty("legacyMigrationLimit").orNull?.let { limit ->
        systemProperty("legacyMigration.limit", limit)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(targetJavaVersion.asInt())
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.register<ToolReportExecTask>("jdepsMain") {
    group = "verification"
    description = "Run jdeps against the compiled backend classes using the Java 25 toolchain."
    dependsOn(tasks.named("classes"))
    toolExecutable.set(jdkBinary("jdeps"))
    staticArgs.set(
        listOf(
            "--multi-release", targetJavaVersion.asInt().toString(),
            "--ignore-missing-deps",
            "--recursive",
        )
    )
    toolClasspath.from(sourceSets["main"].runtimeClasspath)
    analysisTargets.from(sourceSets["main"].output.classesDirs)
    outputFile.set(layout.buildDirectory.file("reports/java-migration/jdeps.txt"))
    errorFile.set(layout.buildDirectory.file("reports/java-migration/jdeps.stderr.txt"))
}

tasks.register<ToolReportExecTask>("jdeprscanMain") {
    group = "verification"
    description = "Run jdeprscan against the compiled backend classes using the Java 25 toolchain."
    dependsOn(tasks.named("classes"))
    toolExecutable.set(jdkBinary("jdeprscan"))
    staticArgs.set(
        listOf(
            "--release", targetJavaVersion.asInt().toString(),
        )
    )
    toolClasspath.from(sourceSets["main"].runtimeClasspath)
    analysisTargets.from(sourceSets["main"].output.classesDirs)
    outputFile.set(layout.buildDirectory.file("reports/java-migration/jdeprscan.txt"))
    errorFile.set(layout.buildDirectory.file("reports/java-migration/jdeprscan.stderr.txt"))
}

tasks.register("javaMigrationAudit") {
    group = "verification"
    description = "Run the Java 25 migration audit toolchain (jdeps + jdeprscan)."
    dependsOn("jdepsMain", "jdeprscanMain")
}
