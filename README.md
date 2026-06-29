# LMDB Viewer

An IntelliJ IDEA plugin to **browse LMDB (Lightning Memory-Mapped Database)** data stores directly
inside the IDE. Read-only and safe; the access layer is built so editing can be added later.

## Features

- Open an LMDB environment: a directory (`data.mdb` + `lock.mdb`), a `data.mdb` file, or a
  single-file `*.mdb` store (`MDB_NOSUBDIR`). Opened read-only (`MDB_RDONLY_ENV`).
- Browse named sub-databases (DBIs) with entry counts.
- Paged entries table with lazy cursor paging ("Load more") — handles very large DBIs without
  loading everything into memory.
- Decode opaque byte keys/values: **hex dump**, **UTF-8/ASCII**, auto-detected **JSON**
  (pretty-printed), and **integers** (int8/16/32/64 × little/big endian, signed + unsigned).
- Per-pane decoder override, plus a pluggable decoder **extension point**
  (`team.shade.lmdbviewer.byteDecoder`) for binary formats like protobuf / msgpack.
- Key-prefix search (UTF-8 text, or `0x…` hex) using cursor seek.
- Environment stats (map size, page size, readers, transaction id, DBI count).

## Build & run

Requires **JDK 21**. Targets IntelliJ **2024.2+**.

```bash
./gradlew runIde                          # launch a sandbox IDE with the plugin
./gradlew test                            # decoder + access-layer tests (9 tests)
./gradlew buildPlugin                     # build/distributions/*.zip for install-from-disk
./gradlew verifyPlugin                    # JetBrains Plugin Verifier (needs network; run before release)
```

`test` and `buildPlugin` are verified green. The Gradle wrapper is checked in, so `./gradlew` works
directly.

Open via **File ▸ Open LMDB Environment…**, the tool window's *Open Environment* button, or
right-click a `.mdb` file in the Project view ▸ *Open LMDB Environment…*.

See [CLAUDE.md](CLAUDE.md) for architecture and the read-only → read-write roadmap.
