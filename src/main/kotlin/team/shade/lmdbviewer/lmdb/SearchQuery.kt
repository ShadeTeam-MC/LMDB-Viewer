package team.shade.lmdbviewer.lmdb

/** Where a [SearchQuery] looks for its needle. */
enum class SearchScope {
    /** Keys that start with the needle. Backed by a fast cursor seek (see `LmdbConnection.readPage`). */
    KEY_PREFIX,

    /** Keys that contain the needle anywhere. Requires a full scan. */
    KEY_CONTAINS,

    /** Values that contain the needle anywhere. Requires a full scan. */
    VALUE_CONTAINS,
}

/**
 * A parsed search request: a [scope] plus the raw [needle] bytes to look for. The needle is compared
 * byte-for-byte, so it matches equally whether the user typed UTF-8 text or `0x…` hex (the UI parses
 * both to bytes before constructing this).
 */
data class SearchQuery(val scope: SearchScope, val needle: ByteArray) {

    fun matches(entry: LmdbEntry): Boolean = when (scope) {
        SearchScope.KEY_PREFIX -> ByteSearch.startsWith(entry.key, needle)
        SearchScope.KEY_CONTAINS -> ByteSearch.indexOf(entry.key, needle) >= 0
        SearchScope.VALUE_CONTAINS -> ByteSearch.indexOf(entry.value, needle) >= 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SearchQuery) return false
        return scope == other.scope && needle.contentEquals(other.needle)
    }

    override fun hashCode(): Int = 31 * scope.hashCode() + needle.contentHashCode()
}

/** Byte-array matching primitives shared by the access layer and search queries. */
object ByteSearch {

    /** True when [value] begins with [prefix] (an empty prefix always matches). */
    fun startsWith(value: ByteArray, prefix: ByteArray): Boolean {
        if (value.size < prefix.size) return false
        for (i in prefix.indices) if (value[i] != prefix[i]) return false
        return true
    }

    /**
     * Index of the first occurrence of [needle] in [haystack], or -1 if absent. An empty needle
     * matches at 0. Naive O(n·m) scan — needles here are short and DBIs are streamed once.
     */
    fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        if (needle.size > haystack.size) return -1
        val last = haystack.size - needle.size
        outer@ for (i in 0..last) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
