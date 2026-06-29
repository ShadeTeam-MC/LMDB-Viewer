---
type: Domain Background
title: LMDB Concepts
description: The parts of the LMDB data model that this plugin cares about.
tags: [lmdb, domain, background]
resource: https://www.symas.com/lmdb
---

# LMDB Concepts (the bits this plugin cares about)

* An **environment** is a directory containing `data.mdb` + `lock.mdb`, or a single `*.mdb` file
  when created with `MDB_NOSUBDIR`.
* An environment holds one or more **named sub-databases (DBIs)**. Keys and values are **opaque
  byte arrays**, stored sorted by key (byte order, or integer order for `MDB_INTEGERKEY` DBIs).
* `MDB_DUPSORT` DBIs allow **multiple sorted values per key**.
* Reads happen inside a read transaction, which is an MVCC snapshot. We open the env with
  `MDB_RDONLY_ENV` and never write.

## Related

* How these concepts are accessed in code: [Access Layer](/architecture/access-layer.md).
* Two LMDB invariants the access layer depends on are documented with the access layer.
