package team.shade.lmdbviewer.lmdb

/**
 * A single reversible write against a DBI. Used both to describe an applied edit and its inverse.
 * [Put] writes a value; [Delete] removes a key (or one duplicate of it on a DUPSORT DBI).
 */
sealed interface Mutation {
    val dbiName: String?
    val key: ByteArray

    data class Put(override val dbiName: String?, override val key: ByteArray, val value: ByteArray) : Mutation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Put) return false
            return dbiName == other.dbiName && key.contentEquals(other.key) && value.contentEquals(other.value)
        }

        override fun hashCode(): Int =
            31 * (31 * (dbiName?.hashCode() ?: 0) + key.contentHashCode()) + value.contentHashCode()
    }

    /** A delete. [value] targets a specific duplicate on a DUPSORT DBI; null removes every duplicate. */
    data class Delete(override val dbiName: String?, override val key: ByteArray, val value: ByteArray?) : Mutation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Delete) return false
            return dbiName == other.dbiName && key.contentEquals(other.key) &&
                (value?.contentEquals(other.value ?: ByteArray(0)) ?: (other.value == null))
        }

        override fun hashCode(): Int =
            31 * (31 * (dbiName?.hashCode() ?: 0) + key.contentHashCode()) + (value?.contentHashCode() ?: 0)
    }

    /** Replaces the pair (key, [from]) with (key, [to]) — a single-duplicate edit on a DUPSORT DBI. */
    data class Replace(override val dbiName: String?, override val key: ByteArray, val from: ByteArray, val to: ByteArray) : Mutation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Replace) return false
            return dbiName == other.dbiName && key.contentEquals(other.key) &&
                from.contentEquals(other.from) && to.contentEquals(other.to)
        }

        override fun hashCode(): Int {
            var h = dbiName?.hashCode() ?: 0
            h = 31 * h + key.contentHashCode()
            h = 31 * h + from.contentHashCode()
            return 31 * h + to.contentHashCode()
        }
    }
}

/** Applies this mutation through the write seam. */
fun Mutation.applyTo(ops: MutationOps) = when (this) {
    is Mutation.Put -> ops.put(dbiName, key, value)
    is Mutation.Delete -> ops.delete(dbiName, key, value)
    is Mutation.Replace -> ops.replace(dbiName, key, from, to)
}

/**
 * Computes the inverse of an applied write, so it can be undone. Pure — no LMDB access; callers pass
 * the prior state they observed at write time.
 */
object Inverses {

    /**
     * Inverse of `put(key, newValue)`.
     *
     * - On a DUPSORT DBI a put *adds* the pair, so the inverse just removes that specific pair.
     * - On a normal DBI a put overwrites: if the key existed, restore [priorValue]; if it did not
     *   ([priorValue] == null), delete the key.
     */
    fun forPut(dbiName: String?, key: ByteArray, newValue: ByteArray, isDupSort: Boolean, priorValue: ByteArray?): Mutation =
        when {
            isDupSort -> Mutation.Delete(dbiName, key, newValue)
            priorValue == null -> Mutation.Delete(dbiName, key, null)
            else -> Mutation.Put(dbiName, key, priorValue)
        }

    /** Inverse of `delete(key, value)` — re-add the exact pair that was removed. */
    fun forDelete(dbiName: String?, key: ByteArray, value: ByteArray): Mutation =
        Mutation.Put(dbiName, key, value)

    /** Inverse of `replace(key, from, to)` — replace back, i.e. swap the direction. */
    fun forReplace(dbiName: String?, key: ByteArray, from: ByteArray, to: ByteArray): Mutation =
        Mutation.Replace(dbiName, key, to, from)
}

/**
 * A bounded LIFO stack of inverse [Mutation]s for the current edit session, so the most recent
 * write can be undone. Not thread-safe: mutate only from the EDT. When more than [limit] edits are
 * recorded, the oldest is discarded (undo reaches back at most [limit] steps).
 */
class EditHistory(private val limit: Int = 100) {

    private val undo = ArrayDeque<Mutation>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val size: Int get() = undo.size

    /** Records the inverse of a just-applied edit as the next thing an undo would run. */
    fun record(inverse: Mutation) {
        undo.addLast(inverse)
        while (undo.size > limit) undo.removeFirst()
    }

    /** Removes and returns the most recent inverse, or null if the history is empty. */
    fun pop(): Mutation? = undo.removeLastOrNull()

    fun clear() = undo.clear()
}
