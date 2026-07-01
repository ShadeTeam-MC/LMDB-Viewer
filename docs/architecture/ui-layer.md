---
type: Architecture Layer
title: UI Layer (ui/)
description: The ui/ package builds the IntelliJ tool window, tree, paged table and detail panel, never blocking the EDT.
tags: [architecture, ui-layer, swing]
resource: src/main/kotlin/team/shade/lmdbviewer/ui
---

# UI Layer (`ui/`)

IntelliJ Platform Swing.

* `LmdbViewerToolWindowFactory` builds the "LMDB Viewer" tool window (right dock).
* `LmdbViewerPanel`: env/DBI tree (left) + entries table (center, paged via "Load more") +
  `DetailPanel` (bottom, per-decoder view of selected key & value) + key-prefix search.
* **Edit mode** (opt-in): the *Edit mode* toggle reopens the selected env writable via
  `LmdbEnvironmentService.open(path, writable = true)`; the writable env node is marked `[RW]`. Add /
  Edit value / Delete (toolbar buttons + table context menu) collect bytes through
  `EntryEditorDialog` (UTF-8/Hex via `ByteCodec`), confirm with `Messages`, then route to
  `connection.mutations` on the pooled thread and refresh the page + DBI counts.
* **Export / import**: a tree right-click menu offers *Export DBI…*, *Export environment…* and
  *Import into DBI…*; an *Import…* button sits in the table actions bar (enabled only in edit mode).
  Export streams `connection.forEachEntry` into a `transfer/` `EntryExporter` via a `FileSaverDialog`;
  import reads a JSON/NDJSON file through `EntryImporter` and calls `mutations.putBatch` in batches,
  then refreshes. See [Transfer Layer](/architecture/transfer-layer.md).
* Never block the EDT: env open, page fetches, mutations, and export/import all run on a pooled thread
  (`Application.executeOnPooledThread`), results applied via `invokeLater` (the `runBg` helper).

## Related

* What feeds the tree, table and stats: [Access Layer](/architecture/access-layer.md).
* How the detail panel renders keys/values: [Decode Layer](/architecture/decode-layer.md).
* The user-facing capabilities these components provide: [Features](/features.md).
