# Log

## 2026-07-01 (feat)

**Automatic map growth.** A write that hits `MDB_MAP_FULL` now grows the environment's map size
(`Env.setMapSize`, doubling up to 16 GiB) and retries, instead of failing. `LmdbConnection` gained an
`onMapResized` hook; the UI shows a `LMDB map size expanded` warning balloon (new `notificationGroup`
in `plugin.xml`). All growth/retry stays in `WritableMutationOps` (the `MutationOps` seam), and runs
only after the failed write txn has closed. Tests grew to 70 (`writesGrowMapOnMapFull`, plus a
service-path writable-write test). Verified on platform 2024.2.

> Note: an earlier attempt concluded live `setMapSize` "crashes on Windows" — that was a false
> signal from the unrelated 2025.2 + Gradle-plugin-2.1.0 test-worker crash (`Index: 1, Size: 1`).
> On the stable 2024.2 toolchain, live growth works correctly.

## 2026-07-01 (build)

**Stayed on platform 2024.2.** Briefly bumped `platformVersion` to 2025.2 to silence the non-fatal
Java-25 `GradleJvmSupportMatrix` warning in the `runIde` sandbox, but 2025.2 with IntelliJ Platform
Gradle Plugin 2.1.0 breaks `gradlew test` (test-worker crash) — proper support needs plugin 2.17.0 +
Gradle 9. Reverted to 2024.2, which is stable and green. The Java-25 sandbox warning is tolerated
(non-fatal; the IDE and plugin still load) — see [build-run-test](operations/build-run-test.md).

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
