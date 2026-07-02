---
type: Roadmap
title: Roadmap — Read-Only to Read-Write
description: How to add editing later behind MutationOps without reworking the decode and UI layers.
tags: [roadmap, read-write, mutationops]
---

# Roadmap: read-only -> read-write

## Done — optional edit mode (0.9.0)

Editing is implemented behind `MutationOps`, exactly along the planned seam:
* `WritableMutationOps` (in `lmdb/MutationOps.kt`) performs `put`/`delete` in a short
  `Env.txnWrite()` and commits; `LmdbConnection.mutations` exposes it only on a writable env.
* `LmdbEnvironmentService.open(path, writable)` drops `MDB_RDONLY_ENV` **only** when the user opts
  into edit mode, reopening the environment in place; writable opens add map-size head-room.
* The `ui/` layer adds an **Edit mode** toggle (with a warning), and Add / Edit value / Delete via
  toolbar buttons and a table context menu, each confirmed through `Messages` dialogs.
* All mutation stays inside the `lmdb/` package; `decode/` is untouched.

This first slice shipped **confirmation** and edits bytes via a UTF-8 / Hex toggle; undo followed in
0.19.0 (see below).

## Done — automatic map growth

A write that exhausts the environment's map size no longer fails: `WritableMutationOps` catches
`MDB_MAP_FULL`, grows the map via `Env.setMapSize` (doubling, up to a 16 GiB ceiling) and retries
the operation. `LmdbConnection.onMapResized` reports the new size, and the `ui/` layer shows a
warning balloon (`LMDB map size expanded`). The grow happens after the failed write txn has closed,
so no transaction is active when `setMapSize` runs.

## Done — export / import

DBI and environment contents can be exported to a file (JSON, NDJSON, or CSV) and JSON/NDJSON
dumps re-imported in edit mode. The serialization lives in a new platform-free `transfer/` package
(lossless UTF-8/base64 tagging); the access layer gained `LmdbConnection.forEachEntry` (streaming
reads) and `MutationOps.putBatch` (one write txn per batch, on the existing seam). See
[Transfer Layer](/architecture/transfer-layer.md).

## Done — undo (0.19.0)

Single edits (add / edit value / delete) are reversible within the current edit session. Each write
captures its **inverse** as a `Mutation` (`lmdb/EditHistory.kt`): a put on a normal DBI restores the
prior value read via the new `LmdbConnection.get` (or deletes the key if it was an insert); a put on
a DUPSORT DBI and a delete invert to removing/re-adding the exact pair. Inverses stack in a bounded
`EditHistory` bound to the connection, so toggling edit mode starts a fresh history. A toolbar
**Undo** button and **Ctrl+Z** pop and apply the latest inverse. Import is not recorded (it warns it
cannot be undone). Redo and a visible history list remain future work.

## Next horizons

* **DUPSORT-aware editing UI** (add/remove individual duplicates from the detail panel).
* **Rename key** as a first-class action (currently delete + add).
* **Redo** and a visible change-history list on top of `EditHistory`.

## Related

* The `MutationOps` seam and read-only env flags: [Access Layer](/architecture/access-layer.md).
