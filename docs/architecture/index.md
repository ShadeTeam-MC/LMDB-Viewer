# Architecture

* [Architecture Overview](/architecture/overview.md) - the three layers and their separation rules.
* [Access Layer](/architecture/access-layer.md) - `lmdb/` package: read-only env access, cursor paging, LMDB invariants.
* [Decode Layer](/architecture/decode-layer.md) - `decode/` package: `byte[]` to human view, UI-independent.
* [UI Layer](/architecture/ui-layer.md) - `ui/` package: tool window, tree, paged table, detail panel.
* [Native Loading Gotcha](/architecture/native-loading.md) - lmdbjava JNR-FFI / classloader risk and its fix.
* [Decoder Extension Point](/architecture/decoder-extension-point.md) - contract for adding a `ByteDecoder`.
