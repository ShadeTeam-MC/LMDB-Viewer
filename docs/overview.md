---
type: Project Overview
title: LMDB Viewer
description: A JetBrains/IntelliJ plugin to browse LMDB data stores directly inside the IDE, read-only in v1.
tags: [overview, plugin]
---

# LMDB Viewer

A JetBrains/IntelliJ plugin that lets developers **browse LMDB (Lightning Memory-Mapped
Database) data stores** directly inside the IDE, with no separate CLI tooling.

Version 1 is a **read-only** viewer. The data-access layer is intentionally shaped so that
add/edit/delete (write transactions) can be layered on later without rework — see the
[Roadmap](/roadmap.md).

## Where to go next

* The LMDB data model the plugin relies on: [LMDB Concepts](/lmdb-concepts.md).
* What a user can do with it: [Features](/features.md).
* How the code is organized: [Architecture Overview](/architecture/overview.md).
* How to build and run it: [Build, Run & Test](/operations/build-run-test.md).
