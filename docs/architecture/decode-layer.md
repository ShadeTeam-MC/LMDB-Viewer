---
type: Architecture Layer
title: Decode Layer (decode/)
description: The decode/ package turns opaque byte arrays into human-readable views, UI-independent and extensible.
tags: [architecture, decode-layer, decoders]
resource: src/main/kotlin/team/shade/lmdbviewer/decode
---

# Decode Layer (`decode/`)

Pure functions over `ByteArray`, **no Swing imports**.

* `ByteDecoder` interface: `id`, `displayName`, `priority`, `canDecode(bytes)`, `decode(bytes)`.
* Built-ins: `HexDumpDecoder` (always-true fallback, lowest priority), `Utf8Decoder`,
  `AsciiDecoder`, `JsonDecoder` (auto-detect + pretty-print), `IntegerDecoder` (int32/int64 ×
  little/big endian, signed + unsigned).
* **Extension point** `team.shade.lmdbviewer.byteDecoder` — register new decoders
  (protobuf, msgpack, …) without touching core. `DecoderRegistry` reads the EP and the
  auto-detector picks the highest-priority decoder whose `canDecode` returns true.

## Related

* The full contract for writing and registering a decoder:
  [Decoder Extension Point](/architecture/decoder-extension-point.md).
* Where decoded views are shown to the user: [UI Layer](/architecture/ui-layer.md).
