---
type: Conventions
title: Conventions
description: Package naming and the import boundaries that keep the three layers separate.
tags: [conventions, packages, boundaries]
---

# Conventions

* Base package: `team.shade.lmdbviewer`. Plugin id: `team.shade.lmdbviewer`.
* Only `lmdb/` may import `org.lmdbjava.*`. Only `ui/` may import Swing / `com.intellij.ui.*`.
* Keep `decode/` and `transfer/` free of platform and lmdbjava imports so they stay unit-testable in
  isolation (`transfer/` may reuse `decode/`'s strict UTF-8 check, but nothing platform-specific).

## Related

* The layers these boundaries protect: [Architecture Overview](/architecture/overview.md).
