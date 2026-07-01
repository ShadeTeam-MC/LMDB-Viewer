---
okf_version: "0.1"
---

# LMDB Viewer — Knowledge Bundle

Open Knowledge Format (OKF v0.1) bundle for the **LMDB Viewer** IntelliJ IDEA plugin.
Load only the document relevant to the part of the code you are working on.

## Start here

* [Project Overview](/overview.md) - what the plugin is and its read-only-first design.
* [Features](/features.md) - user-facing capabilities and how to open an environment.
* [LMDB Concepts](/lmdb-concepts.md) - the LMDB data model this plugin depends on.

## Architecture

* [Architecture Overview](/architecture/overview.md) - the three layers and their separation rules.
* [Access Layer](/architecture/access-layer.md) - `lmdb/` package: read-only env access, paging, invariants.
* [Decode Layer](/architecture/decode-layer.md) - `decode/` package: byte[] to human view.
* [Transfer Layer](/architecture/transfer-layer.md) - `transfer/` package: export/import file formats.
* [UI Layer](/architecture/ui-layer.md) - `ui/` package: tool window, tree, table, detail panel.
* [Native Loading Gotcha](/architecture/native-loading.md) - lmdbjava JNR-FFI / classloader integration risk and its fix.
* [Decoder Extension Point](/architecture/decoder-extension-point.md) - contract for adding a `ByteDecoder`.

## Operations

* [Build, Run & Test](/operations/build-run-test.md) - JDK, Gradle tasks, and build settings that matter.

## Project

* [Conventions](/conventions.md) - package and import-boundary rules.
* [Roadmap](/roadmap.md) - read-only to read-write evolution.

## Playbooks (how the agent works in this repo)

* [Workflow Orchestration](/playbooks/workflow-orchestration.md) - planning, subagents, verification, elegance.
* [Task Management](/playbooks/task-management.md) - the task lifecycle and lesson capture.
