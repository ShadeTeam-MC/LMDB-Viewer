---
type: Integration Gotcha
title: lmdbjava Native-Library Loading
description: The main integration risk — lmdbjava's JNR-FFI native loading under the IDE classloader, and the fix to keep.
tags: [lmdbjava, native, jnr-ffi, classloader, gotcha]
resource: src/main/kotlin/team/shade/lmdbviewer/lmdb/ClassLoaderGuard.kt
---

# lmdbjava native-library gotcha (the main integration risk)

`lmdbjava` loads native LMDB through **JNR-FFI**, which reflectively touches `java.nio` internals.
On JDK 21 the sandbox IDE needs:

```
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
```

These are wired into the `runIde`/`test` tasks in `build.gradle.kts` (see
[Build, Run & Test](/operations/build-run-test.md)). lmdbjava bundles native binaries for
linux/macos/windows (x86_64 + aarch64) in a separate `native` jar.

## The real blocker: thread-context-classloader native loading (FIXED — keep the fix)

lmdbjava's `Library.extract()` loads its native binary with
`Thread.currentThread().getContextClassLoader().getResourceAsStream("org/lmdbjava/native/<target>")`
(verified in the 0.9.3 bytecode). Inside the IDE the context classloader of pooled/EDT threads is
the **platform** classloader, which does not contain the plugin's jars, so the resource is null →
`Library`/`ByteArrayProxy` throw `ExceptionInInitializerError` (with a **null** message) on first
touch, and every later touch throws `NoClassDefFoundError: Could not initialize class
org.lmdbjava.ByteArrayProxy`. Once this happens the class is dead for the whole JVM session — an IDE
**restart** is required.

Fix: every lmdbjava call runs inside `ClassLoaderGuard.runWithPluginClassLoader { … }`, which installs
the plugin classloader as the thread context classloader for the duration of the call (see
`lmdb/ClassLoaderGuard.kt`, applied in `LmdbEnvironmentService.open` and all `LmdbConnection`
operations). Do not remove these wrappers. Note this is *not* the `--add-opens` issue — opening works
under JDK 21 / JBR without those flags; they remain only as belt-and-suspenders for JNR reflection.

## Related

* The layer that wraps every native call in the guard: [Access Layer](/architecture/access-layer.md).
