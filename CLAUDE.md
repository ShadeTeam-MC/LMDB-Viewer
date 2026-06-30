# CLAUDE.md — LMDB Viewer (IntelliJ IDEA plugin)

A JetBrains/IntelliJ plugin that lets developers **browse LMDB (Lightning Memory-Mapped
Database) data stores** directly inside the IDE. Environments open **read-only by default**; an
opt-in **edit mode** reopens an environment for writing and allows add/edit/delete, all behind the
`MutationOps` seam in the access layer.

## Documentation lives in OKF — this file is only an index

The full project documentation is an **Open Knowledge Format (OKF v0.1)** bundle under
[`docs/`](docs/index.md). The bundle root [`docs/index.md`](docs/index.md) is the manifest
(declares `okf_version`) and lists every document.

**Rules:**
- **Load only the relevant OKF document** under `docs/` before working on that part of the code.
  Do **not** load the whole bundle at once — open the one concept that matches your task (use the
  table below to choose).
- **Keep the docs in sync.** When you change or add functionality, update the matching OKF
  document (or add a new one), and keep [`docs/index.md`](docs/index.md) — and this table —
  current. Record notable changes in [`docs/log.md`](docs/log.md).

## When to read which document

| Document | Read it when… |
|---|---|
| [docs/overview.md](docs/overview.md) | You need the big picture / what the plugin is. |
| [docs/features.md](docs/features.md) | Working on user-facing capabilities or how to open an environment. |
| [docs/lmdb-concepts.md](docs/lmdb-concepts.md) | You need the LMDB data model (env, DBIs, DUPSORT, MVCC reads). |
| [docs/architecture/overview.md](docs/architecture/overview.md) | Orienting across the three layers and their boundaries. |
| [docs/architecture/access-layer.md](docs/architecture/access-layer.md) | Touching `lmdb/`: env open, paging, the two LMDB invariants. |
| [docs/architecture/decode-layer.md](docs/architecture/decode-layer.md) | Touching `decode/`: decoders, registry, auto-detect. |
| [docs/architecture/ui-layer.md](docs/architecture/ui-layer.md) | Touching `ui/`: tool window, tree, table, detail panel, EDT rules. |
| [docs/architecture/native-loading.md](docs/architecture/native-loading.md) | Debugging lmdbjava native loading / classloader errors. |
| [docs/architecture/decoder-extension-point.md](docs/architecture/decoder-extension-point.md) | Writing or registering a new `ByteDecoder`. |
| [docs/operations/build-run-test.md](docs/operations/build-run-test.md) | Building, running the sandbox IDE, or running tests. |
| [docs/conventions.md](docs/conventions.md) | You need the package / import-boundary rules. |
| [docs/roadmap.md](docs/roadmap.md) | Adding editing (read-only → read-write). |
| [docs/playbooks/workflow-orchestration.md](docs/playbooks/workflow-orchestration.md) | You want the agent working rules (plan, subagents, verify). |
| [docs/playbooks/task-management.md](docs/playbooks/task-management.md) | You need the task lifecycle for this repo. |
