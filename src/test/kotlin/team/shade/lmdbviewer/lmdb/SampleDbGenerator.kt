package team.shade.lmdbviewer.lmdb

import org.lmdbjava.ByteArrayProxy
import org.lmdbjava.DbiFlags
import org.lmdbjava.Env
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Generates a sample LMDB environment that exercises every feature of the LMDB Viewer plugin, so the
 * whole plugin can be tried on one database. Not a test — run it via the Gradle task:
 *
 *     ./gradlew generateSampleDb                 # writes to samples/showcase-lmdb
 *     ./gradlew generateSampleDb -PsampleOut=... # custom output directory
 *
 * Open the resulting folder in the IDE via *Open Environment…*.
 *
 * What each database demonstrates:
 * - `users`     — 300 JSON values (JSON auto-decode, paging / "Load more", key-prefix & value search)
 * - `settings`  — UTF-8 (incl. non-ASCII + emoji) and ASCII text values
 * - `counters`  — integer values of several widths and endianness (Integer decoder)
 * - `events`    — 8-byte integer KEYS with JSON values (integer-key decode)
 * - `blobs`     — binary values → hex dump; a binary key; a `0xDEADBEEF` needle for hex search
 * - `cbor`      — CBOR values rendered as JSON (CBOR decoder)
 * - `tags`      — DUPSORT: keys with multiple values (duplicates panel: add / edit / remove)
 * - `empty`     — an empty database (edge case)
 *
 * The unnamed `(main)` database is LMDB's directory of sub-databases, so it lists the names above —
 * we deliberately do not add regular keys there (a plain key in main would look like a phantom DBI).
 */
object SampleDbGenerator {

    // Small on purpose: the whole sample is only tens of KB, and on Windows LMDB pre-allocates the
    // map to full size on disk. 8 MiB keeps the committed file small while leaving room to edit.
    private const val MAP_SIZE = 8L shl 20 // 8 MiB
    private val UTF8 = StandardCharsets.UTF_8

    @JvmStatic
    fun main(args: Array<String>) {
        val outDir = File(args.firstOrNull() ?: "samples/showcase-lmdb").absoluteFile
        if (outDir.exists()) outDir.deleteRecursively()
        outDir.mkdirs()

        Env.create(ByteArrayProxy.PROXY_BA).setMaxDbs(16).setMapSize(MAP_SIZE).open(outDir).use { env ->
            writeUsers(env)
            writeSettings(env)
            writeCounters(env)
            writeEvents(env)
            writeBlobs(env)
            writeCbor(env)
            writeTags(env)
            env.openDbi("empty", DbiFlags.MDB_CREATE) // empty database, edge case
        }

        println("Sample LMDB environment written to: ${outDir.path}")
        println("Open this folder in the IDE via 'Open Environment…'.")

        // Read the databases back through the plugin's own access layer as a sanity check.
        println("Databases:")
        TestEnvs.openReadOnly(outDir).use { conn ->
            conn.listDatabases().forEach { dbi ->
                val dup = if (dbi.isDupSort) " [DUPSORT]" else ""
                println("  - ${dbi.displayName}: ${dbi.entryCount} entries$dup")
            }
        }
    }

    /** 300 JSON records — enough to page ("Load more" at 200); names/cities repeat for value search. */
    private fun writeUsers(env: Env<ByteArray>) {
        val names = listOf("Alice", "Bob", "Carol", "Dave", "Eve", "Frank", "Grace", "Heidi")
        val cities = listOf("Berlin", "Paris", "Tokyo", "Cairo", "Lima")
        val roles = listOf("admin", "editor", "viewer")
        putAll(env, "users", (1..300).associate { i ->
            val name = names[i % names.size]
            val city = cities[i % cities.size]
            val role = roles[i % roles.size]
            val json = """{"id":$i,"name":"$name","city":"$city","role":"$role","active":${i % 2 == 0}}"""
            "user:%04d".format(i).b() to json.b()
        })
    }

    /** Text values: UTF-8 with multibyte + emoji, and plain ASCII. */
    private fun writeSettings(env: Env<ByteArray>) = putAll(env, "settings", linkedMapOf(
        "settings:app.title".b() to "LMDB Viewer — Showcase".b(),
        "settings:welcome".b() to "Привет, мир! 🌍 UTF-8 текст with emoji ✅".b(),
        "settings:ascii".b() to "plain 7-bit ascii value".b(),
        "settings:path".b() to "/var/lib/app/data".b(),
        "settings:note".b() to "search me: the quick brown fox".b(),
    ))

    /** Raw integer values: several widths and both endiannesses for the Integer decoder. */
    private fun writeCounters(env: Env<ByteArray>) = putAll(env, "counters", linkedMapOf(
        "counter:u8-255".b() to be(255, 1),
        "counter:i16-le-513".b() to le(513, 2),
        "counter:i32-be-1000".b() to be(1000, 4),
        "counter:u32-le-max".b() to le(4_294_967_295L, 4),
        "counter:i64-be-1".b() to be(1, 8),
        "counter:i64-le-neg1".b() to le(-1, 8),
    ))

    /** 8-byte big-endian integer keys (epoch millis) with JSON values — integer-key decoding. */
    private fun writeEvents(env: Env<ByteArray>) = putAll(env, "events", linkedMapOf(
        be(1_700_000_000_000L, 8) to """{"event":"login","user":"alice"}""".b(),
        be(1_700_000_060_000L, 8) to """{"event":"logout","user":"alice"}""".b(),
        be(1_700_000_120_000L, 8) to """{"event":"login","user":"bob"}""".b(),
    ))

    /** Binary values (hex-dump fallback), a binary key, and a 0xDEADBEEF needle for hex search. */
    private fun writeBlobs(env: Env<ByteArray>) = putAll(env, "blobs", linkedMapOf(
        "blob:png-header".b() to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
        "blob:deadbeef".b() to byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(), 0x00, 0x01, 0x02),
        "blob:bytes".b() to ByteArray(16) { it.toByte() },
        // A binary (non-UTF-8) key, to try hex key search like 0x00ff10.
        byteArrayOf(0x00, 0xFF.toByte(), 0x10) to "value under a binary key".b(),
    ))

    /** CBOR-encoded values (RFC 8949), rendered as JSON by the CBOR decoder. */
    private fun writeCbor(env: Env<ByteArray>) = putAll(env, "cbor", linkedMapOf(
        // {"a":1,"b":[1,2,3]}
        "cbor:map".b() to byteArrayOf(0xA2.toByte(), 0x61, 0x61, 0x01, 0x61, 0x62, 0x83.toByte(), 0x01, 0x02, 0x03),
        // {"ok":true,"n":42}
        "cbor:flags".b() to byteArrayOf(0xA2.toByte(), 0x62, 0x6F, 0x6B, 0xF5.toByte(), 0x61, 0x6E, 0x18, 0x2A),
        // [1,2,3,"x"]
        "cbor:array".b() to byteArrayOf(0x84.toByte(), 0x01, 0x02, 0x03, 0x61, 0x78),
    ))

    /** DUPSORT database: keys with several sorted values — for the duplicates panel. */
    private fun writeTags(env: Env<ByteArray>) {
        val dbi = env.openDbi("tags", DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT)
        val data = linkedMapOf(
            "photo:001" to listOf("beach", "sunset", "2024", "favorite"),
            "photo:002" to listOf("portrait", "bw"),
            "photo:003" to listOf("food"),
        )
        env.txnWrite().use { txn ->
            data.forEach { (k, values) -> values.forEach { v -> dbi.put(txn, k.b(), v.b()) } }
            txn.commit()
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private fun putAll(env: Env<ByteArray>, dbiName: String, entries: Map<ByteArray, ByteArray>) {
        val dbi = env.openDbi(dbiName, DbiFlags.MDB_CREATE)
        env.txnWrite().use { txn ->
            entries.forEach { (k, v) -> dbi.put(txn, k, v) }
            txn.commit()
        }
    }

    private fun String.b(): ByteArray = toByteArray(UTF8)

    private fun be(value: Long, size: Int): ByteArray =
        ByteArray(size) { i -> ((value shr (8 * (size - 1 - i))) and 0xFF).toByte() }

    private fun le(value: Long, size: Int): ByteArray =
        ByteArray(size) { i -> ((value shr (8 * i)) and 0xFF).toByte() }
}
