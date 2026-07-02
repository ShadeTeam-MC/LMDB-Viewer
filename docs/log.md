# Log

## 2026-07-02 (feat, search)

**Search by key/value content.** The prefix-only search grew a mode selector: **Key prefix**
(unchanged fast `KeyRange.atLeast` seek), **Key contains**, and **Value contains** (substring). New
platform-free `lmdb/SearchQuery.kt` — `SearchScope` enum, `SearchQuery.matches`, and a shared
`ByteSearch` (`startsWith` moved out of `LmdbConnection`, plus a naive byte `indexOf`). New
`LmdbConnection.scanPage(dbiName, query, afterKey, limit)` scans in key order and keeps matches up to
`limit`, with the continuation token set to the last **matched** key so *Load more* never repeats or
skips a match (same key-bound DUPSORT caveat as `readPage`). UI (`LmdbViewerPanel`): a
`JComboBox<SearchScope>` next to the field, `parsePrefix` → `parseNeedle` (distinguishes blank vs.
invalid hex, showing an error in the status bar), and `loadPage` routes prefix→`readPage` /
contains→`scanPage`; status reports "N match(es)" while scanning. New `SearchQueryTest` and
`ScanPageTest` (value/key substring, paginated continuity, binary needle, empty result). Docs
(`features`, `architecture/access-layer`, `architecture/ui-layer`) updated. `pluginVersion`
0.17.0 → 0.18.0 (feat → minor).

## 2026-07-02 (feat, usability/polish)

**Recent menu, icons & copy.** Surfaced the already-persisted recent-environments list: a *Recent*
toolbar button opens a menu of recently used environments (`RecentEnvironmentsService.recentPaths`),
each reopening via `openEnvironment`; a failed open now self-cleans the path (`recent.remove`), and a
*Clear recent* item wipes the list (new `RecentEnvironmentsService.clear()`). Added a custom
`pluginIcon.svg`/`pluginIcon_dark.svg` (database + lightning bolt) and switched the tool-window icon
off the borrowed hierarchy icon; the tree now uses a `ColoredTreeCellRenderer` with node icons and
greyed-out `(count)` / `[RW]` / `[DUPSORT]` markers. Table rows gained Ctrl+C and *Copy key* / *Copy
value* (decoded via the auto-detected decoder). Polish: the previously silent `onError {}` in
`reloadRecentlyOpen` and `refreshAfterMutation` now report to the status bar. New
`RecentEnvironmentsServiceTest.clearEmptiesTheList`. Docs (`overview`, `features`) updated;
`pluginVersion` 0.16.0 → 0.17.0 (feat → minor).

## 2026-07-02 (feat, shortcuts)

**Keyboard shortcuts & tooltips.** Bound tool-window shortcuts (Ctrl+F search, F5 refresh, Ctrl+O/W
open/close, Ctrl+E edit mode, Ctrl+Shift+Down load more, Insert/F2/Delete add/edit/delete) and gave
every toolbar button, menu item and the search field a descriptive tooltip. `pluginVersion`
0.14.0 → 0.15.0 (feat → minor).

## 2026-07-02 (feat, diagnostics)

**LMDB diagnostics.** New read-only `LmdbDiagnosticsDialog` (opened from a *Stats…* toolbar button
or the tree's *Diagnostics…* item): an environment summary (used bytes + map utilization %, page
size, readers used/max, last txn) and a per-DBI table of B-tree stats (entries, depth,
branch/leaf/overflow pages, approx. size, flags), plus a *Check stale readers* action wrapping
`Env.readerCheck`. Access layer: `listDatabases` now captures the full per-DBI `Stat` and reads
flags via `Dbi.listFlags`, which **fixes a latent bug** — `DbiInfo.flags` was always empty, so
`isDupSort` and the tree's `[DUPSORT]` marker never showed. `DbiInfo` gained `depth`/`branchPages`/
`leafPages`/`overflowPages` (+`totalPages`); `EnvStats` gained derived `usedBytes`/
`utilizationPercent`; new `LmdbConnection.checkStaleReaders()`. Note: lmdbjava 0.9.3 does not expose
the per-reader lock table (`mdb_reader_list`), so only aggregate readers + stale-reader cleanup are
available. New `DiagnosticsTest` (DUPSORT-flag regression, per-DBI stats, readerCheck, EnvStats
getters). Docs (`features`, `architecture/access-layer`, `architecture/ui-layer`) updated.
`pluginVersion` 0.15.0 → 0.16.0 (feat → minor).

## 2026-07-01 (feat, export/import)

**Export / import.** DBIs and whole environments can be exported to a file and JSON/NDJSON dumps
re-imported (edit mode). New platform-free `transfer/` package: `TransferFormat` (JSON / NDJSON /
CSV), `TransferRecord`, `ByteText` (lossless UTF-8/base64 tagging — reuses the decode layer's strict
UTF-8 check), a dependency-free `Json` escaper + value parser (the decode-layer JSON code only
re-emits, so import needed its own parser), `EntryExporter` (push-based streaming writer) and
`EntryImporter` (lazy `Sequence`; NDJSON parsed line-by-line, CSV export-only). Access layer gained
`LmdbConnection.forEachEntry` (streams a DBI in one read txn, no limit) and `MutationOps.putBatch`
(one write txn per batch inside the existing `withGrowth` retry; `ReadOnlyMutationOps` rejects it).
UI adds a tree right-click menu (Export DBI / Export environment / Import into DBI) and an *Import…*
button in the table actions bar (enabled only in edit mode), using `FileSaverDialog` / `FileChooser`
on background threads with a success balloon. New tests: `transfer/TransferRoundTripTest`
(round-trips, encoding choice, CSV escaping, format detection) and `lmdb/LmdbTransferTest`
(`forEachEntry`, `putBatch`, read-only rejection, end-to-end export→import). New OKF doc
`architecture/transfer-layer.md`; `features`, `roadmap`, `conventions`, `index`, and the `CLAUDE.md`
table updated. `pluginVersion` 0.13.0 → 0.14.0 (feat → minor).

## 2026-07-01 (feat, CBOR)

**CBOR decoder.** Added `decode/CborDecoder.kt` — a dependency-free RFC 8949 reader that renders
binary CBOR values as pretty-printed JSON, registered as a new **CBOR** format in the Value/Key
panel (`plugin.xml`). Covers the full type set: unsigned/negative integers (widening to `BigInteger`
past `Long` range), definite + indefinite byte/text strings, arrays, maps (insertion order),
tags (UUID tag 37 → canonical string, bignum tag 2/3 → number, unknown tags pass through), half/
single/double floats, booleans, null/undefined, simple values; byte strings shown as `0x…` hex.
`priority = 70` (below text JSON 80, above Integer 60); `canDecode` is strict (root must be
array/map/tag and consume every byte) so Auto never grabs plain ints/text. New `CborDecoderTest`
(round-trips the two real waypoint-marker dumps + per-type cases). Docs (`architecture/decode-layer`)
updated; `pluginVersion` 0.10.0 → 0.11.0 (feat → minor).

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
