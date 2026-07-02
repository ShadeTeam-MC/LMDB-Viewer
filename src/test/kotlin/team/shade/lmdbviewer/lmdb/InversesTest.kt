package team.shade.lmdbviewer.lmdb

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure inverse-computation logic ([Inverses]) and mutation dispatch ([applyTo]). */
class InversesTest {

    @Test
    fun putOnNormalDbiInsertInvertsToDelete() {
        // Key did not exist (priorValue == null) -> undo removes the key.
        val inv = Inverses.forPut("db", "k".b(), "new".b(), isDupSort = false, priorValue = null)
        assertEquals(Mutation.Delete("db", "k".b(), null), inv)
    }

    @Test
    fun putOnNormalDbiOverwriteInvertsToRestore() {
        val inv = Inverses.forPut("db", "k".b(), "new".b(), isDupSort = false, priorValue = "old".b())
        assertEquals(Mutation.Put("db", "k".b(), "old".b()), inv)
    }

    @Test
    fun putOnDupSortInvertsToDeleteThatPair() {
        // On DUPSORT a put adds a pair; undo removes exactly that pair (priorValue is irrelevant).
        val inv = Inverses.forPut("db", "k".b(), "new".b(), isDupSort = true, priorValue = "whatever".b())
        assertEquals(Mutation.Delete("db", "k".b(), "new".b()), inv)
    }

    @Test
    fun deleteInvertsToPutSamePair() {
        val inv = Inverses.forDelete("db", "k".b(), "v".b())
        assertEquals(Mutation.Put("db", "k".b(), "v".b()), inv)
    }

    @Test
    fun applyToDispatchesToOps() {
        val ops = RecordingOps()
        Mutation.Put("db", "k".b(), "v".b()).applyTo(ops)
        Mutation.Delete("db", "k".b(), null).applyTo(ops)
        Mutation.Delete("db", "k".b(), "dup".b()).applyTo(ops)
        assertEquals(
            listOf(
                "put db k v",
                "delete db k null",
                "delete db k dup",
            ),
            ops.calls,
        )
    }

    /** Captures the calls made through the write seam. */
    private class RecordingOps : MutationOps {
        val calls = mutableListOf<String>()
        override fun put(dbiName: String?, key: ByteArray, value: ByteArray) {
            calls += "put $dbiName ${String(key)} ${String(value)}"
        }

        override fun delete(dbiName: String?, key: ByteArray, value: ByteArray?) {
            calls += "delete $dbiName ${String(key)} ${value?.let { String(it) } ?: "null"}"
        }
    }
}
