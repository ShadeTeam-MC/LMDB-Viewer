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
  `AsciiDecoder`, `JsonDecoder` (auto-detect + pretty-print), `CborDecoder` (RFC 8949 binary →
  pretty JSON, dependency-free; priority 70, between JSON and Integer), `IntegerDecoder` (int32/int64 ×
  little/big endian, signed + unsigned).
* `CborDecoder` keeps a **strict** `canDecode` (root must be array/map/tag and the whole buffer must
  parse as exactly one item with no trailing bytes) so it never hijacks plain ints/text in auto-detect.
  Renders UUIDs (tag 37) as canonical strings, bignums (tag 2/3) as numbers, byte strings as `0x…` hex.
* **Extension point** `team.shade.lmdbviewer.byteDecoder` — register new decoders
  (protobuf, msgpack, …) without touching core. `DecoderRegistry` reads the EP and the
  auto-detector picks the highest-priority decoder whose `canDecode` returns true.

## Related

* The full contract for writing and registering a decoder:
  [Decoder Extension Point](/architecture/decoder-extension-point.md).
* Where decoded views are shown to the user: [UI Layer](/architecture/ui-layer.md).
