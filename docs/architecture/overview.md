---
type: Architecture Overview
title: Architecture Overview
description: The three layers of the plugin and the rule to keep them separate.
tags: [architecture, layers]
---

# Architecture (3 layers — keep them separate)

```
src/main/kotlin/team/shade/lmdbviewer/
  lmdb/      access layer  — wraps lmdbjava, hides JNR/native details
  decode/    decode layer  — pure byte[] -> human view, UI-independent, extensible
  ui/        ui layer      — ToolWindow, tree, table, detail panel, actions
  settings/  recent-environments persistence
```

The three layers are deliberately separated and each has a single responsibility:

1. **[Access Layer](/architecture/access-layer.md)** (`lmdb/`) — the only place that imports
   `org.lmdbjava.*`. Opens environments read-only and pages entries.
2. **[Decode Layer](/architecture/decode-layer.md)** (`decode/`) — pure functions over
   `ByteArray`, no Swing imports, extensible via an extension point.
3. **[UI Layer](/architecture/ui-layer.md)** (`ui/`) — IntelliJ Platform Swing components.

The `settings/` package holds recent-environments persistence.

The import boundaries that enforce this separation are listed in [Conventions](/conventions.md).
