---
type: Architecture
title: Transfer Layer
description: The transfer/ package — export/import file formats and lossless byte encoding.
tags: [architecture, transfer, export, import, formats]
---

# Transfer Layer

The `transfer/` package turns DBI contents into files and back. Like `decode/`, it is
**platform-free and lmdbjava-free** (only imports the JDK and the decode layer's strict UTF-8
check), so it is unit-testable in isolation. The `ui/` layer wires it to file dialogs and background
threads; the `lmdb/` layer feeds and consumes records.

## Files

* `TransferFormat.kt` — the `TransferFormat` enum (`JSON`, `NDJSON`, `CSV`) with each format's file
  `extension` and an `importable` flag, plus `fromFileName`. Also `TransferRecord(db, key, value)`,
  the unit passed in and out (`db` is non-null only for environment-wide dumps).
* `ByteText.kt` — the lossless byte↔text encoding (see below) and a human rendering for CSV.
* `Json.kt` — a minimal dependency-free JSON string escaper (`appendString`) and value parser
  (`parse` → tree of `Map`/`List`/`String`/`Double`/`Boolean`/null). The decode layer's JSON code
  only *re-emits* JSON, so import needs its own parser.
* `EntryExporter.kt` — push-based streaming writer: `write(record)` per record, `close()` emits
  trailing syntax. `AutoCloseable`, so use it in a `use { }` block.
* `EntryImporter.kt` — `read(reader, format)` → lazy `Sequence<TransferRecord>` (NDJSON is parsed a
  line at a time; JSON parses the whole array). CSV is export-only and rejected.

## Lossless byte encoding — the core invariant

Keys and values are arbitrary bytes, so export→import must be **byte-for-byte** reversible.
Decoders in `decode/` are **not** used here — they are lossy previews. Instead each field is tagged:

* valid UTF-8 (per the decode layer's strict check) → `{"enc":"utf8","v":"…"}`, kept readable;
* anything else → `{"enc":"base64","v":"…"}`.

This favours readable dumps for the common textual case while round-tripping binary exactly. CSV
uses a one-column human rendering instead (`utf8` text or `0x…` hex) and is therefore **not**
importable.

## How the layers cooperate

* **Export** — `ui/` calls `LmdbConnection.forEachEntry(dbiName)` (one short read txn, streams every
  entry with no size limit) and pushes each into an `EntryExporter`. Environment export loops every
  DBI from `listDatabases()`, tagging records with their DBI name.
* **Import** (edit mode only) — `ui/` reads the file through `EntryImporter`, chunks the sequence,
  and calls `MutationOps.putBatch(dbiName, entries)`. `WritableMutationOps.putBatch` commits a whole
  batch in **one** write txn (wrapped in the existing `withGrowth` retry); `ReadOnlyMutationOps`
  rejects it, like every other write.

## Related

* Streaming reads and the `MutationOps` seam: [Access Layer](/architecture/access-layer.md).
* Why decoders are lossy previews (and must not back export): [Decode Layer](/architecture/decode-layer.md).
* The tree menu / import button and background threading: [UI Layer](/architecture/ui-layer.md).
* Package/import boundaries: [Conventions](/conventions.md).
