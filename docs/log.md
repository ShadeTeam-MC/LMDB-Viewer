# Log

## 2026-06-30 (build)

**Dev platform → 2025.2.** Bumped `platformVersion` 2024.2 → 2025.2 so the `runIde` sandbox can
parse the Java 25 entry in the Gradle JVM-support matrix (2024.2 crashed at startup with
`GradleJvmSupportMatrix … IllegalArgumentException: 25`). `since-build` stays 242 — supported range
unchanged. IntelliJ Platform Gradle Plugin left at 2.1.0 (2.17.0 needs Gradle 9). Deleted the stale
`build/idea-sandbox/IC-2024.2`; refreshed README + build-run-test docs.

## 2026-06-30 (later)

**Optional edit mode (read-write).** Implemented the roadmap's read-only → read-write step behind
the `MutationOps` seam. `WritableMutationOps` (in `lmdb/MutationOps.kt`) performs `put`/`delete` in
short write transactions; `LmdbConnection` gained `writable` + `mutations`; `LmdbEnvironmentService.open(path, writable)`
drops `MDB_RDONLY_ENV` only in edit mode (reopening in place, with map-size head-room). The UI added
an *Edit mode* toggle (warning + `[RW]` node marker) and Add / Edit value / Delete via toolbar
buttons and a table context menu, each confirmed with `Messages`; byte entry uses a new
`ByteCodec` (UTF-8/Hex) and `EntryEditorDialog`. No undo in this slice. Tests grew to 68
(`WritableMutationOpsTest`, `ByteCodecTest`, writable/reopen cases in `LmdbEnvironmentServiceTest`).
Docs (`roadmap`, `architecture/access-layer`, `architecture/ui-layer`, `features`) and `plugin.xml`
updated; `pluginVersion` bumped 0.8.0 → 0.9.0 (feat → minor).

## 2026-06-30

**Test expansion** — Grew the test suite from 9 to 54 tests, covering previously-untested logic
without adding dependencies (still JUnit 4). New: a shared `TestEnvs` fixture helper
(`src/test/.../lmdb/TestEnvs.kt`) for building/reopening throwaway environments; `LmdbConnection`
edge cases (stats, empty DBI, exact-limit paging, exhausted continuation, binary prefix,
missing-DBI error); full `LmdbEnvironmentService` coverage (cache, dir/`data.mdb`/single-file path
resolution, failure wrapping, `dispose`); `ClassLoaderGuard` restore semantics; `Models` computed
properties; `RecentEnvironmentsService` MRU/cap/persistence; decoder edge cases (ASCII, empty input,
JSON boundaries, integer widths); and the `Previews` + `EntriesTableModel` UI logic. The pre-existing
two test files were left untouched.

## 2026-06-29

**Creation** — Migrated the project documentation into this Open Knowledge Format (OKF v0.1)
bundle. Every section previously held in `CLAUDE.md` and `README.md` is now a concept document:
overview, features, LMDB concepts, the three architecture layers, the native-loading gotcha, the
decoder extension-point contract, build/run/test, conventions, the read-write roadmap, and the
agent playbooks. `CLAUDE.md` became a thin index pointer and `README.md` a short public landing
page.
