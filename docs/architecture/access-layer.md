---
type: Architecture Layer
title: Access Layer (lmdb/)
description: The lmdb/ package wraps lmdbjava for read-only environment access and cursor paging.
tags: [architecture, lmdb, access-layer]
resource: src/main/kotlin/team/shade/lmdbviewer/lmdb
---

# Access Layer (`lmdb/`)

The **only** place that imports `org.lmdbjava.*`.

* `LmdbEnvironmentService` (application service): opens/closes envs and caches one open
  `LmdbConnection` per absolute path. `open(path, writable)` selects the mode: read-only (default)
  adds `MDB_RDONLY_ENV`; writable (edit mode) drops it. A cached connection is reused only when its
  mode matches — toggling edit mode **closes and reopens** the env in place. Writable opens use a
  larger map size (head-room) so a small edit doesn't immediately hit `MDB_MAP_FULL`.
* `LmdbConnection`: lists DBIs, returns env stats, and pages entries via a cursor. Paging uses a
  **continuation token = last key seen** (`KeyRange.greaterThan(lastKey)`), so we never load a
  whole DBI into memory and never hold a long-lived read txn across UI interactions — each page
  fetch opens and closes its own short read txn. Reads behave identically on read-only and writable
  envs. `connection.writable` reports the mode; `connection.mutations` is the write seam.
  `forEachEntry(dbiName, block)` streams **every** entry of a DBI in one read txn (no limit, nothing
  materialised) — used by export.
* Search: two strategies. **Key prefix** is a *seek* — `readPage(prefix = …)` starts at
  `KeyRange.atLeast(prefix)` and stops when the prefix no longer matches (cheap). **Content search**
  (key/value substring) is a *scan* — `scanPage(dbiName, query, afterKey, limit)` iterates in key
  order, keeps entries that `SearchQuery.matches`, and sets its continuation token to the last
  **matched** key so *Load more* never repeats or skips a match. `SearchQuery`/`SearchScope` and the
  byte primitives (`ByteSearch.startsWith` / `indexOf`) live in the platform-free `lmdb/` layer.
* Undo: `LmdbConnection.get(dbiName, key)` reads a single value (prior state for capturing an
  inverse). `EditHistory` (in `lmdb/EditHistory.kt`) is a bounded LIFO of inverse `Mutation`s bound
  to the connection, so it resets when edit mode reopens the env. `Inverses.forPut/forDelete` compute
  the inverse purely (a normal-DBI put restores the prior value or deletes an inserted key; a DUPSORT
  put and any delete invert to removing/re-adding the exact pair). Applying an inverse goes back
  through `MutationOps`.
* `MutationOps`: the single write seam. `ReadOnlyMutationOps` rejects every call;
  `WritableMutationOps` (used only on a writable env) runs `put`/`delete` in a short
  `Env.txnWrite()` and commits. `putBatch(dbiName, entries)` writes a whole batch in **one** write
  txn (used by import). On `MDB_MAP_FULL` all writes grow the map (`Env.setMapSize`, doubling up to
  16 GiB) and retry, reporting the new size via `LmdbConnection.onMapResized`. Do not scatter write
  logic elsewhere. See the [Roadmap](/roadmap.md).
* Diagnostics: `listDatabases` populates each `DbiInfo` with the full per-DBI B-tree `Stat`
  (`depth`, `branchPages`, `leafPages`, `overflowPages`, `entries`) and its persistent flags via
  `Dbi.listFlags` — so `DbiInfo.isDupSort` (and the tree's `[DUPSORT]` marker) now work. `EnvStats`
  exposes derived `usedBytes` / `utilizationPercent`. `checkStaleReaders()` wraps `Env.readerCheck`
  (lmdbjava does not expose the per-reader lock table). Reads run in the same short read txn.
* Models: `LmdbEntry(key, value, valueSize)`, `EntryPage(entries, nextKey)`, `DbiInfo`,
  `EnvStats`.

## Two LMDB invariants the access layer depends on (learned the hard way — keep them)

* Environments are opened with **`MDB_NOTLS`** (in addition to `MDB_RDONLY_ENV`). Reader-lock
  slots are then tied to transactions, not threads, so the UI thread pool can open concurrent and
  *nested* read txns. Without it, `lmdbjava`'s `openDbi` (which opens its own internal read txn on
  a read-only env) inside an already-open read txn throws `Txn$BadReaderLockException`.
* **Open a DBI handle before the transaction that reads it begins.** A DBI opened *after* a txn
  started is not visible to that txn, so `dbi.stat(txn)` returns wrong counts. `listDatabases`
  opens all handles first, then begins one read txn to stat them; `readPage` opens the DBI before
  its iteration txn. `WritableMutationOps` opens the DBI before its write txn too. The unnamed/main
  DBI is exempt (always known to every txn).
* **Single writer.** LMDB allows only one write txn at a time per env. Each `WritableMutationOps`
  call opens, commits, and closes its own write txn, so mutations are effectively serialized; never
  hold a write txn open across UI interactions.
* **Grow the map with no transaction open.** `Env.setMapSize` requires that no transaction is active
  in the process. `WritableMutationOps` only calls it from the `MDB_MAP_FULL` catch block — *after*
  the failed write txn's `use {}` has closed it — then retries in a fresh txn.

## Related

* The LMDB data model these operations expose: [LMDB Concepts](/lmdb-concepts.md).
* The native-loading risk every lmdbjava call must guard against:
  [Native Loading Gotcha](/architecture/native-loading.md).
* How the access layer becomes read-write later: [Roadmap](/roadmap.md).
