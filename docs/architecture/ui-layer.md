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
* Never block the EDT: env open and page fetches run on a pooled thread
  (`Application.executeOnPooledThread`), results applied via `invokeLater`.

## Related

* What feeds the tree, table and stats: [Access Layer](/architecture/access-layer.md).
* How the detail panel renders keys/values: [Decode Layer](/architecture/decode-layer.md).
* The user-facing capabilities these components provide: [Features](/features.md).
