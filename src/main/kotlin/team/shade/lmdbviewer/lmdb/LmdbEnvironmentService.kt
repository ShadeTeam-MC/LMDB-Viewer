package team.shade.lmdbviewer.lmdb

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import org.lmdbjava.ByteArrayProxy
import org.lmdbjava.Env
import org.lmdbjava.EnvFlags
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-level service that opens LMDB environments **read-only** and caches one
 * [LmdbConnection] per absolute path. The only entry point the UI uses to obtain a connection.
 */
class LmdbEnvironmentService : Disposable {

    private val log = logger<LmdbEnvironmentService>()
    private val connections = ConcurrentHashMap<String, LmdbConnection>()

    /**
     * Opens (or returns the cached) connection for [path]. [path] may be:
     *  - a directory environment (contains `data.mdb` + `lock.mdb`),
     *  - a `data.mdb` file (its parent directory is used),
     *  - any other `*.mdb` single-file environment (`MDB_NOSUBDIR`).
     *
     * @throws LmdbOpenException if the environment cannot be opened
     */
    fun open(path: String): LmdbConnection {
        val target = resolveTarget(File(path))
        val key = target.canonical.absolutePath
        connections[key]?.let { return it }

        return try {
            // lmdbjava resolves its native lib via the thread context classloader; force it to ours.
            val env = ClassLoaderGuard.runWithPluginClassLoader { openEnv(target) }
            val connection = LmdbConnection(key, env)
            connections[key] = connection
            log.info("Opened LMDB environment (read-only): $key")
            connection
        } catch (t: Throwable) {
            log.warn("Failed to open LMDB environment at '$path'", t)
            throw LmdbOpenException(describeFailure(path, t), t)
        }
    }

    fun openOrNull(path: String): LmdbConnection? = runCatching { open(path) }.getOrNull()

    /** Currently open connections, keyed by canonical path. */
    fun openConnections(): Map<String, LmdbConnection> = connections.toMap()

    fun close(path: String) {
        val key = File(path).canonicalFile.absolutePath
        connections.remove(key)?.let {
            runCatching { it.close() }
            log.info("Closed LMDB environment: $key")
        }
    }

    private fun openEnv(target: ResolvedTarget): Env<ByteArray> {
        val builder = Env.create(ByteArrayProxy.PROXY_BA)
            .setMaxDbs(MAX_DBS)
            .setMapSize(mapSizeFor(target))

        val flags = buildList {
            add(EnvFlags.MDB_RDONLY_ENV)
            // Tie reader-lock slots to transactions, not threads: lets us open nested/concurrent read
            // txns from the UI thread pool (e.g. a read txn that opens a DBI, which opens its own).
            add(EnvFlags.MDB_NOTLS)
            if (target.noSubDir) add(EnvFlags.MDB_NOSUBDIR)
        }.toTypedArray()

        return builder.open(target.canonical, *flags)
    }

    /** For read-only opens LMDB needs a map size at least as large as the data on disk. */
    private fun mapSizeFor(target: ResolvedTarget): Long {
        val dataFile = if (target.noSubDir) target.canonical else File(target.canonical, "data.mdb")
        val len = dataFile.takeIf { it.isFile }?.length() ?: 0L
        return maxOf(len, MIN_MAP_SIZE)
    }

    /** Builds a human-readable failure message from the root cause (lmdbjava often has a null message). */
    private fun describeFailure(path: String, t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        val detail = root.message?.takeIf { it.isNotBlank() } ?: root.javaClass.simpleName
        return "Failed to open LMDB environment at '$path': $detail (${root.javaClass.name})"
    }

    private fun resolveTarget(input: File): ResolvedTarget {
        val canonical = input.canonicalFile
        return when {
            canonical.isDirectory -> ResolvedTarget(canonical, noSubDir = false)
            canonical.name == "data.mdb" -> ResolvedTarget(canonical.parentFile.canonicalFile, noSubDir = false)
            else -> ResolvedTarget(canonical, noSubDir = true) // single-file env
        }
    }

    override fun dispose() {
        connections.values.forEach { runCatching { it.close() } }
        connections.clear()
    }

    private data class ResolvedTarget(val canonical: File, val noSubDir: Boolean)

    private companion object {
        const val MAX_DBS = 512
        const val MIN_MAP_SIZE = 1L shl 20 // 1 MiB floor
    }
}

class LmdbOpenException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
