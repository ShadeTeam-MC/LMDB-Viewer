---
type: Feature List
title: Features
description: User-facing capabilities of the LMDB Viewer plugin and how to open an environment.
tags: [features, ui, usage]
---

# Features

* Open an LMDB environment: a directory (`data.mdb` + `lock.mdb`), a `data.mdb` file, or a
  single-file `*.mdb` store (`MDB_NOSUBDIR`). Opened read-only (`MDB_RDONLY_ENV`).
* Browse named sub-databases (DBIs) with entry counts.
* Paged entries table with lazy cursor paging ("Load more") — handles very large DBIs without
  loading everything into memory.
* Decode opaque byte keys/values: **hex dump**, **UTF-8/ASCII**, auto-detected **JSON**
  (pretty-printed), and **integers** (int8/16/32/64 × little/big endian, signed + unsigned).
* Per-pane decoder override, plus a pluggable decoder **extension point**
  (`team.shade.lmdbviewer.byteDecoder`) for binary formats like protobuf / msgpack.
* Key-prefix search (UTF-8 text, or `0x…` hex) using cursor seek.
* Environment stats (map size, page size, readers, transaction id, DBI count).

# Examples

Open an environment in any of these ways:

* **File ▸ Open LMDB Environment…**
* the LMDB Viewer tool window's *Open Environment* button, or
* right-click a `.mdb` file in the Project view ▸ *Open LMDB Environment…*.

## Related

* The decoding model behind keys/values: [Decode Layer](/architecture/decode-layer.md).
* Adding your own decoder: [Decoder Extension Point](/architecture/decoder-extension-point.md).
* What backs the tree, table and detail panel: [UI Layer](/architecture/ui-layer.md).
