# CLAUDE.md — LMDB Viewer (IntelliJ IDEA plugin)

A JetBrains/IntelliJ plugin that lets developers **browse LMDB (Lightning Memory-Mapped
Database) data stores** directly inside the IDE, with no separate CLI tooling. Version 1 is a
**read-only** viewer; the data-access layer is intentionally shaped so add/edit/delete (write
transactions) can be layered on later without rework.

## Workflow Orchestration

### 1. Plan Mode Default
- Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions)
- If something goes sideways, STOP and re-plan immediately – don't keep pushing
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

### 2. Subagent Strategy
- Use subagents liberally to keep main context window clean
- Offload research, exploration, and parallel analysis to subagents
- For complex problems, throw more compute at it via subagents
- One tack per subagent for focused execution

### 3. Self-Improvement Loop
- After ANY correction from the user: update `tasks/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until mistake rate drops
- Review lessons at session start for relevant project

### 4. Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

### 5. Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes – don't over-engineer
- Challenge your own work before presenting it

### 6. Autonomous Bug Fixing
- When given a bug report: just fix it. Don't ask for hand-holding
- Point at logs, errors, failing tests – then resolve them
- Zero context switching required from the user
- Go fix failing CI tests without being told how

## Task Management
1. **Plan First**: Write plan to `tasks/todo.md` with checkable items
2. **Verify Plan**: Check in before starting implementation
3. **Track Progress**: Mark items complete as you go
4. **Explain Changes**: High-level summary at each step
5. **Document Results**: Add review section to `tasks/todo.md`
6. **Capture Lessons**: Update `tasks/lessons.md` after corrections

## What is LMDB (the bits this plugin cares about)

- An **environment** is a directory containing `data.mdb` + `lock.mdb`, or a single `*.mdb` file
  when created with `MDB_NOSUBDIR`.
- An environment holds one or more **named sub-databases (DBIs)**. Keys and values are **opaque
  byte arrays**, stored sorted by key (byte order, or integer order for `MDB_INTEGERKEY` DBIs).
- `MDB_DUPSORT` DBIs allow **multiple sorted values per key**.
- Reads happen inside a read transaction, which is an MVCC snapshot. We open the env with
  `MDB_RDONLY_ENV` and never write.

## Architecture (3 layers — keep them separate)

```
src/main/kotlin/team/shade/lmdbviewer/
  lmdb/      access layer  — wraps lmdbjava, hides JNR/native details
  decode/    decode layer  — pure byte[] -> human view, UI-independent, extensible
  ui/        ui layer      — ToolWindow, tree, table, detail panel, actions
  settings/  recent-environments persistence
```

1. **`lmdb` (access layer)** — the *only* place that imports `org.lmdbjava.*`.
   - `LmdbEnvironmentService` (application service): opens/closes envs read-only, caches one open
     `LmdbConnection` per absolute path.
   - `LmdbConnection`: lists DBIs, returns env stats, and pages entries via a cursor. Paging uses a
     **continuation token = last key seen** (`KeyRange.greaterThan(lastKey)`), so we never load a
     whole DBI into memory and never hold a long-lived read txn across UI interactions — each page
     fetch opens and closes its own short read txn.
   - `MutationOps`: a narrow no-op interface in v1. This is the seam where write transactions slot
     in later — do not scatter write logic elsewhere.
   - Models: `LmdbEntry(key, value, valueSize)`, `EntryPage(entries, nextKey)`, `DbiInfo`,
     `EnvStats`.

   **Two LMDB invariants the access layer depends on (learned the hard way — keep them):**
   - Environments are opened with **`MDB_NOTLS`** (in addition to `MDB_RDONLY_ENV`). Reader-lock
     slots are then tied to transactions, not threads, so the UI thread pool can open concurrent and
     *nested* read txns. Without it, `lmdbjava`'s `openDbi` (which opens its own internal read txn on
     a read-only env) inside an already-open read txn throws `Txn$BadReaderLockException`.
   - **Open a DBI handle before the transaction that reads it begins.** A DBI opened *after* a txn
     started is not visible to that txn, so `dbi.stat(txn)` returns wrong counts. `listDatabases`
     opens all handles first, then begins one read txn to stat them; `readPage` opens the DBI before
     its iteration txn. The unnamed/main DBI is exempt (always known to every txn).

2. **`decode` (decode layer)** — pure functions over `ByteArray`, no Swing imports.
   - `ByteDecoder` interface: `id`, `displayName`, `priority`, `canDecode(bytes)`, `decode(bytes)`.
   - Built-ins: `HexDumpDecoder` (always-true fallback, lowest priority), `Utf8Decoder`,
     `AsciiDecoder`, `JsonDecoder` (auto-detect + pretty-print), `IntegerDecoder` (int32/int64 ×
     little/big endian, signed + unsigned).
   - **Extension point** `team.shade.lmdbviewer.byteDecoder` — register new decoders
     (protobuf, msgpack, …) without touching core. `DecoderRegistry` reads the EP and the
     auto-detector picks the highest-priority decoder whose `canDecode` returns true.

3. **`ui` (ui layer)** — IntelliJ Platform Swing.
   - `LmdbViewerToolWindowFactory` builds the "LMDB Viewer" tool window (right dock).
   - `LmdbViewerPanel`: env/DBI tree (left) + entries table (center, paged via "Load more") +
     `DetailPanel` (bottom, per-decoder view of selected key & value) + key-prefix search.
   - Never block the EDT: env open and page fetches run on a pooled thread
     (`Application.executeOnPooledThread`), results applied via `invokeLater`.

## Build / run / test

JDK **21** required. Target platform: IntelliJ **2024.2+** (`since-build 242`).

```bash
./gradlew runIde         # launch a sandbox IDE with the plugin (jvmArgs add --add-opens, see below)
./gradlew buildPlugin     # produce build/distributions/*.zip for install-from-disk
./gradlew test            # unit tests (decoders) + access-layer tests
./gradlew verifyPlugin     # JetBrains Plugin Verifier — run before release
```

The Gradle wrapper is checked in, so `./gradlew` works out of the box (Gradle 8.10.2).

**Build settings that matter (set deliberately in `build.gradle.kts` / `gradle.properties`):**
- `instrumentCode = false` — pure-Kotlin plugin; no Java bytecode/form instrumentation, which would
  otherwise require the `instrumentationTools()` dependency.
- No `testFramework(...)` dependency — tests are plain JUnit4 and don't use platform fixtures, so we
  avoid pulling `com.jetbrains.intellij.platform:test-framework`.
- `org.gradle.configuration-cache = false` — the Kotlin compile classpath snapshot isn't
  config-cache serializable with this plugin combo yet.
- `verifyPlugin` needs network access to the JetBrains product-releases feed to resolve target IDEs;
  add `pluginVerification { ides { recommended() } }` before running it. The status of
  `data.services.jetbrains.com` / `www.jetbrains.com` reachability decides whether it can run.
- Status: `./gradlew test` (9 tests) and `./gradlew buildPlugin` both pass; `buildPlugin` produces
  `build/distributions/lmdb-viewer-<version>.zip` with bundled native LMDB libs.

## lmdbjava native-library gotcha (the main integration risk)

`lmdbjava` loads native LMDB through **JNR-FFI**, which reflectively touches `java.nio` internals.
On JDK 21 the sandbox IDE needs:

```
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
```

These are wired into the `runIde`/`test` tasks in `build.gradle.kts`. lmdbjava bundles native
binaries for linux/macos/windows (x86_64 + aarch64) in a separate `native` jar.

### The real blocker: thread-context-classloader native loading (FIXED — keep the fix)

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

## Decoder extension-point contract

To add a decoder (in this plugin or a dependent plugin), implement `ByteDecoder` and register:

```xml
<extensions defaultExtensionNs="team.shade.lmdbviewer">
  <byteDecoder implementation="com.example.MsgpackDecoder"/>
</extensions>
```

Rules: `canDecode` must be cheap and side-effect free; return false rather than throwing on bad
input. `priority` orders auto-detect (higher wins); keep structured-format decoders above plain
text and text above hex. `decode` must never throw — return a best-effort `DecodedView`.

## Roadmap: read-only -> read-write

When adding editing: implement the write methods behind `MutationOps` (open a write txn via
`Env.txnWrite()`, `Dbi.put`/`Dbi.delete`, commit), gate the env open flags (drop `MDB_RDONLY_ENV`
only when the user opts into edit mode), add undo + confirmation in the UI layer, and keep all
mutation inside the `lmdb` package. Everything in `decode/` and most of `ui/` stays unchanged.

## Conventions

- Base package: `team.shade.lmdbviewer`. Plugin id: `team.shade.lmdbviewer`.
- Only `lmdb/` may import `org.lmdbjava.*`. Only `ui/` may import Swing / `com.intellij.ui.*`.
- Keep `decode/` free of platform and lmdbjava imports so it stays unit-testable in isolation.
