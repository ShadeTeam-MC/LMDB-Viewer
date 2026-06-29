---
type: Architecture Layer
title: Access Layer (lmdb/)
description: The lmdb/ package wraps lmdbjava for read-only environment access and cursor paging.
tags: [architecture, lmdb, access-layer]
resource: src/main/kotlin/team/shade/lmdbviewer/lmdb
---

# Access Layer (`lmdb/`)

The **only** place that imports `org.lmdbjava.*`.

* `LmdbEnvironmentService` (application service): opens/closes envs read-only, caches one open
  `LmdbConnection` per absolute path.
* `LmdbConnection`: lists DBIs, returns env stats, and pages entries via a cursor. Paging uses a
  **continuation token = last key seen** (`KeyRange.greaterThan(lastKey)`), so we never load a
  whole DBI into memory and never hold a long-lived read txn across UI interactions — each page
  fetch opens and closes its own short read txn.
* `MutationOps`: a narrow no-op interface in v1. This is the seam where write transactions slot
  in later — do not scatter write logic elsewhere. See the [Roadmap](/roadmap.md).
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
  its iteration txn. The unnamed/main DBI is exempt (always known to every txn).

## Related

* The LMDB data model these operations expose: [LMDB Concepts](/lmdb-concepts.md).
* The native-loading risk every lmdbjava call must guard against:
  [Native Loading Gotcha](/architecture/native-loading.md).
* How the access layer becomes read-write later: [Roadmap](/roadmap.md).
