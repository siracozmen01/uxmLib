import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("java-library")
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

// The compiler and the bytecode target are deliberately different versions. Paper 26.x ships Java 25
// class files, so nothing older than a Java 25 compiler can even read paper-api; the emitted bytecode
// stays at Java 21 so a single published jar keeps running on the 1.21.x line, which is still Java 21.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.toolchain.get().toInt())
        vendor = JvmVendorSpec.ADOPTIUM
    }
    // A published library ships sources and javadoc alongside the binary.
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    "compileOnly"(libs.jspecify)
    "testCompileOnly"(libs.jspecify)
    "errorprone"(libs.errorprone.core)
    "errorprone"(libs.nullaway)

    "testImplementation"(platform(libs.junit.bom))
    "testImplementation"(libs.bundles.testing)
    // Gradle 9 no longer bundles junit-platform-launcher in the test runtime; declare it explicitly.
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

// Gradle resolves dependencies by variant, and paper-api publishes metadata declaring it needs a Java 25
// runtime. Matched against `options.release = 21` that looks like an incompatibility and the dependency is
// rejected outright. The declared level constrains the bytecode this project *emits*; it says nothing about
// what the compiler can *read*, and a JDK 25 javac reads Java 25 class files while still emitting Java 21.
// Widen what these classpaths accept, and leave the target alone.
listOf(
    configurations.compileClasspath,
    configurations.runtimeClasspath,
    configurations.testCompileClasspath,
    configurations.testRuntimeClasspath,
).forEach { classpath ->
    classpath.configure {
        attributes {
            attribute(
                org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                libs.versions.java.toolchain.get().toInt(),
            )
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = libs.versions.java.release.get().toInt()
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all",
            "-Xlint:-processing",
            "-Xlint:-serial",
            "-Werror",
            "-parameters",
        ),
    )
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
    }
}

extensions.configure<net.ltgt.gradle.nullaway.NullAwayExtension> {
    onlyNullMarked.set(true)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.nullaway {
        // CheckSeverity is reused from the errorprone plugin: the nullaway plugin defines no enum.
        severity.set(net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    }
}

// Doclint off: the public API is documented for humans, not for the strict javadoc tool, and missing
// @param/@return on internal helpers must never fail the build of a library jar.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        palantirJavaFormat(libs.versions.palantir.fmt.get())
        removeUnusedImports()
        formatAnnotations()
        importOrder("java", "javax", "org.bukkit", "io.papermc", "net.kyori", "")
        trimTrailingWhitespace()
        endWithNewline()
        toggleOffOn()
    }
    kotlinGradle { ktlint("1.5.0") }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        // The HTML report a failure points at lives on the CI runner and is gone when the job ends, so a
        // failing assertion has to say what it actually saw in the console log or nobody can read it.
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}

// MockBukkit answers a method it has not implemented with UnimplementedOperationException, which extends
// JUnit's TestAbortedException. A test that reaches one is therefore recorded as skipped rather than failed:
// the build stays green, and every assertion after that call goes unrun without anybody being told. A skip
// somebody wrote on purpose is a decision and stays allowed, an @Disabled with a reason or an integration
// test with nothing to connect to; this one is a test that quietly stopped testing, so it fails the build.
val verifyNoAbortedTests =
    tasks.register("verifyNoAbortedTests") {
        description = "Fails when a test was aborted by a MockBukkit method that is not implemented."
        val results = layout.buildDirectory.dir("test-results/test")
        doLast {
            val abortedCase =
                Regex(
                    "<testcase name=\"([^\"]*)\" classname=\"([^\"]*)\"[^>]*>\\s*" +
                        "<skipped[^>]*type=\"org\\.mockbukkit\\.mockbukkit\\.exception\\." +
                        "UnimplementedOperationException"
                )
            val files = results.get().asFile.listFiles { file -> file.name.endsWith(".xml") } ?: emptyArray()
            val aborted =
                files.flatMap { file ->
                    abortedCase.findAll(file.readText()).map { "${it.groupValues[2]}.${it.groupValues[1]}" }
                }
                    .sorted()
            if (aborted.isNotEmpty()) {
                throw GradleException(
                    aborted.joinToString(
                        prefix = "Aborted by an unimplemented MockBukkit method, so nothing the test " +
                            "claims to check was checked:\n",
                        separator = "\n",
                        postfix = "\nAssert against what the mock implements, or mark the test @Disabled " +
                            "with the reason, so the gap is one a reader can see.",
                    ) { "  $it" }
                )
            }
        }
    }

tasks.named<Test>("test") { finalizedBy(verifyNoAbortedTests) }
