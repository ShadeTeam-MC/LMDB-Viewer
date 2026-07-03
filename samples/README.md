# Sample LMDB environment

`showcase-lmdb/` is a ready-made LMDB environment that exercises **every** feature of the LMDB
Viewer plugin, so you can try the whole plugin on one database.

Open it in the IDE via **Open Environment…** and pick the `showcase-lmdb` folder (or right-click it in
the Project view ▸ *Open LMDB Environment…*).

Regenerate it any time with:

```
./gradlew generateSampleDb            # writes to samples/showcase-lmdb
./gradlew generateSampleDb -PsampleOut=some/other/dir
```

(The generator is `SampleDbGenerator` in the test sources.)

## What's inside

| Database | Contents | Try |
| --- | --- | --- |
| `users` | 300 JSON records | JSON auto-decode; **paging** ("Load more" after 200); key-prefix search `user:0`; value search `Value contains` → `Alice` |
| `settings` | UTF-8 (incl. Cyrillic + emoji) and ASCII text | UTF-8 / ASCII decoders; `Value contains` → `fox` |
| `counters` | integer values, various widths & endianness | **Integer** decoder (switch signed/endian in the value pane) |
| `events` | 8-byte integer **keys**, JSON values | integer **key** decode; JSON value |
| `blobs` | binary values + one binary key | **hex dump** fallback; hex search `Key contains` → `0x00ff10`, `Value contains` → `0xdead` |
| `cbor` | CBOR-encoded values | **CBOR** decoder (rendered as JSON) |
| `tags` | **DUPSORT** — keys with several values | duplicates panel: select `photo:001`, then **Add / Edit / Remove** values |
| `empty` | no entries | empty-database edge case |
| `(main)` | LMDB's directory of the databases above | shows how the unnamed database looks |

## Feature checklist

- **Browse tree / counts / markers** — every database with its entry count; `tags` shows `[DUPSORT]`.
- **Decoders** — JSON (`users`), UTF-8/ASCII (`settings`), Integer (`counters`, keys of `events`),
  CBOR (`cbor`), hex dump (`blobs`); use the per-pane decoder dropdown, or "Auto".
- **Search** — Key prefix (`user:`), Key contains, Value contains; text or `0x…` hex.
- **Paging** — `users` needs "Load more".
- **Copy** — select a row, `Ctrl+C`, or right-click ▸ Copy key / Copy value.
- **Diagnostics** — *Stats…* shows env + per-database B-tree statistics.
- **Export / import** — right-click a database ▸ Export (JSON/NDJSON/CSV); import in edit mode.
- **Edit mode + Undo** — toggle **Edit mode**, then Add / Edit / Delete; `Ctrl+Z` (or the Undo
  button) reverts the last change.
- **DUPSORT editing** — on `tags`, the detail panel lists all values of a key with Add / Edit /
  Remove; editing replaces just that value; all undoable.
