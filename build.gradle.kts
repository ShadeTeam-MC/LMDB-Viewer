import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // lmdbjava bundles native LMDB for linux/macos/windows x86_64; provides read-only env access.
    implementation("org.lmdbjava:lmdbjava:0.9.3")

    intellijPlatform {
        create(
            IntelliJPlatformType.IntellijIdeaCommunity,
            providers.gradleProperty("platformVersion").get(),
        )

        pluginVerifier()
        zipSigner()
    }

    // Plain JUnit4 unit/integration tests (no IntelliJ platform test fixtures needed).
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    // Pure-Kotlin plugin: no Java bytecode/form instrumentation needed.
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            // Leave untilBuild open so the plugin keeps working on newer IDEs.
            untilBuild = provider { null }
        }
    }

    // Note: `verifyPlugin` needs IDEs resolved against the JetBrains product-releases feed.
    // Configure `pluginVerification { ides { recommended() } }` when running it with network access.
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    test {
        useJUnit()
        // lmdbjava (JNR-FFI) reflectively accesses java.nio internals on JDK 21.
        jvmArgs(
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        )
    }

    // lmdbjava uses JNR-FFI which reflectively accesses java.nio internals on JDK 21.
    runIde {
        jvmArgs(
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        )
        // The bundled Gradle plugin's JVM-support matrix can't parse "Java 25" on IDE 2024.2 and
        // throws a (non-fatal) error at sandbox startup. We don't need Gradle integration to test the
        // plugin, so disable it in the sandbox by listing it in the config's disabled_plugins.txt.
        doFirst {
            val type = providers.gradleProperty("platformType").get()
            val version = providers.gradleProperty("platformVersion").get()
            val configDir = layout.buildDirectory.dir("idea-sandbox/$type-$version/config").get().asFile
            configDir.mkdirs()
            configDir.resolve("disabled_plugins.txt").writeText("com.intellij.gradle\n")
        }
    }
}
