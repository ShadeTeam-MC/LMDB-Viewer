---
type: Project Overview
title: LMDB Viewer
description: A JetBrains/IntelliJ plugin to browse and edit LMDB data stores directly inside the IDE, read-only by default.
tags: [overview, plugin]
---

# LMDB Viewer

A JetBrains/IntelliJ plugin that lets developers **browse LMDB (Lightning Memory-Mapped
Database) data stores** directly inside the IDE, with no separate CLI tooling.

Environments open **read-only by default**. An opt-in **edit mode** (shipped in 0.9.0) reopens
an environment for writing and enables add/edit/delete behind the `MutationOps` seam — the
data-access layer was intentionally shaped so write transactions could be layered on without
rework. See the [Roadmap](/roadmap.md) for what is still planned (undo, DUPSORT editing, rename).

## Where to go next

* The LMDB data model the plugin relies on: [LMDB Concepts](/lmdb-concepts.md).
* What a user can do with it: [Features](/features.md).
* How the code is organized: [Architecture Overview](/architecture/overview.md).
* How to build and run it: [Build, Run & Test](/operations/build-run-test.md).
