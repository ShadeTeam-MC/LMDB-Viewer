package team.shade.lmdbviewer.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Remembers recently opened LMDB environment paths across IDE restarts. Most-recent first,
 * capped at [MAX_RECENT].
 */
@State(
    name = "LmdbViewerRecentEnvironments",
    storages = [Storage("lmdbViewer.xml")],
)
class RecentEnvironmentsService : PersistentStateComponent<RecentEnvironmentsService.State> {

    class State {
        var recentPaths: MutableList<String> = ArrayList()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        XmlSerializerUtil.copyBean(loaded, state)
    }

    val recentPaths: List<String> get() = state.recentPaths.toList()

    fun add(path: String) {
        state.recentPaths.remove(path)
        state.recentPaths.add(0, path)
        while (state.recentPaths.size > MAX_RECENT) {
            state.recentPaths.removeAt(state.recentPaths.size - 1)
        }
    }

    fun remove(path: String) {
        state.recentPaths.remove(path)
    }

    private companion object {
        const val MAX_RECENT = 15
    }
}
