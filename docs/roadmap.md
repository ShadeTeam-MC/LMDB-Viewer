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

This first slice ships **confirmation, not undo**, and edits bytes via a UTF-8 / Hex toggle.

## Done — automatic map growth

A write that exhausts the environment's map size no longer fails: `WritableMutationOps` catches
`MDB_MAP_FULL`, grows the map via `Env.setMapSize` (doubling, up to a 16 GiB ceiling) and retries
the operation. `LmdbConnection.onMapResized` reports the new size, and the `ui/` layer shows a
warning balloon (`LMDB map size expanded`). The grow happens after the failed write txn has closed,
so no transaction is active when `setMapSize` runs.

## Next horizons

* **Undo / change history** for the current edit session (capture inverse ops before each write).
* **DUPSORT-aware editing UI** (add/remove individual duplicates from the detail panel).
* **Rename key** as a first-class action (currently delete + add).

## Related

* The `MutationOps` seam and read-only env flags: [Access Layer](/architecture/access-layer.md).
