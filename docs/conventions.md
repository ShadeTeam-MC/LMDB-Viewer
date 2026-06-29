---
type: Conventions
title: Conventions
description: Package naming and the import boundaries that keep the three layers separate.
tags: [conventions, packages, boundaries]
---

# Conventions

* Base package: `team.shade.lmdbviewer`. Plugin id: `team.shade.lmdbviewer`.
* Only `lmdb/` may import `org.lmdbjava.*`. Only `ui/` may import Swing / `com.intellij.ui.*`.
* Keep `decode/` free of platform and lmdbjava imports so it stays unit-testable in isolation.

## Related

* The layers these boundaries protect: [Architecture Overview](/architecture/overview.md).
