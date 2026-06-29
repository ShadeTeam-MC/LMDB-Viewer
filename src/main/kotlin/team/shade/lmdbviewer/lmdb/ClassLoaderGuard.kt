package team.shade.lmdbviewer.lmdb

/**
 * lmdbjava's `Library` loads its bundled native binary via
 * `Thread.currentThread().getContextClassLoader().getResourceAsStream(...)`. Inside the IDE the
 * context classloader of pooled/EDT threads is the platform loader, which does NOT contain the
 * plugin's jars — so the native resource is not found and `ByteArrayProxy`/`Library` fail to
 * initialize (permanently, for the JVM's lifetime). Running every lmdbjava call with the plugin
 * classloader as the context classloader fixes this. See CLAUDE.md.
 */
internal object ClassLoaderGuard {

    /** The classloader that loaded this plugin (and therefore the lmdbjava + native jars). */
    private val pluginClassLoader: ClassLoader = ClassLoaderGuard::class.java.classLoader

    /** Runs [block] with the plugin classloader installed as the thread context classloader. */
    fun <T> runWithPluginClassLoader(block: () -> T): T {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = pluginClassLoader
        return try {
            block()
        } finally {
            thread.contextClassLoader = previous
        }
    }
}
