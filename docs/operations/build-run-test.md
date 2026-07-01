---
type: Runbook
title: Build, Run & Test
description: JDK and platform requirements, the Gradle tasks, and the build settings that matter.
tags: [build, gradle, test, operations]
resource: build.gradle.kts
---

# Build / run / test

JDK **21** required. Target platform: IntelliJ **2024.2+** (`since-build 242`).

> Note: IDE 2024.2's bundled Gradle plugin can't parse a "Java 25" entry in its JVM-support matrix
> and throws a non-fatal `GradleJvmSupportMatrix … IllegalArgumentException: 25` at startup. The
> `runIde` task disables that plugin in the sandbox (`disabled_plugins.txt` — we don't need Gradle
> integration to test the viewer), which removes the error. Moving to platform 2025.2 would also fix
> it but currently breaks `gradlew test` with IntelliJ Platform Gradle Plugin 2.1.0 (needs plugin
> 2.17.0 + Gradle 9), so we stay on 2024.2. If you hit the same error in your **main** IDE (not the
> sandbox), update IntelliJ IDEA to 2025.x — it's a non-fatal IDE-side issue, unrelated to the plugin.

```bash
./gradlew runIde         # launch a sandbox IDE with the plugin (jvmArgs add --add-opens, see below)
./gradlew buildPlugin     # produce build/distributions/*.zip for install-from-disk
./gradlew test            # unit tests: decoders, access layer, settings, UI logic
./gradlew verifyPlugin     # JetBrains Plugin Verifier — run before release
```

The Gradle wrapper is checked in, so `./gradlew` works out of the box (Gradle 8.10.2).

## Build settings that matter (set deliberately in `build.gradle.kts` / `gradle.properties`)

* `instrumentCode = false` — pure-Kotlin plugin; no Java bytecode/form instrumentation, which would
  otherwise require the `instrumentationTools()` dependency.
* No `testFramework(...)` dependency — tests are plain JUnit4 and don't use platform fixtures, so we
  avoid pulling `com.jetbrains.intellij.platform:test-framework`.
* `org.gradle.configuration-cache = false` — the Kotlin compile classpath snapshot isn't
  config-cache serializable with this plugin combo yet.
* `verifyPlugin` needs network access to the JetBrains product-releases feed to resolve target IDEs;
  add `pluginVerification { ides { recommended() } }` before running it. The status of
  `data.services.jetbrains.com` / `www.jetbrains.com` reachability decides whether it can run.
* Status: `./gradlew test` (68 tests) and `./gradlew buildPlugin` both pass; `buildPlugin` produces
  `build/distributions/lmdb-viewer-<version>.zip` with bundled native LMDB libs.

## Related

* Why the `--add-opens` JVM args are wired into `runIde`/`test`:
  [Native Loading Gotcha](/architecture/native-loading.md).
