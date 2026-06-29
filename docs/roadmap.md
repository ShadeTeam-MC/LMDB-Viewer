---
type: Roadmap
title: Roadmap — Read-Only to Read-Write
description: How to add editing later behind MutationOps without reworking the decode and UI layers.
tags: [roadmap, read-write, mutationops]
---

# Roadmap: read-only -> read-write

When adding editing: implement the write methods behind `MutationOps` (open a write txn via
`Env.txnWrite()`, `Dbi.put`/`Dbi.delete`, commit), gate the env open flags (drop `MDB_RDONLY_ENV`
only when the user opts into edit mode), add undo + confirmation in the UI layer, and keep all
mutation inside the `lmdb` package. Everything in `decode/` and most of `ui/` stays unchanged.

## Related

* The `MutationOps` seam and read-only env flags: [Access Layer](/architecture/access-layer.md).
