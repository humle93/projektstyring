package eu.humle.socialpilot

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

data class UiState(
    val loggedIn: Boolean = false,
    val needsSetup: Boolean = false,
    val serverUrl: String = "http://192.168.1.10:8000",
    val loading: Boolean = false,
    val error: String? = null,
    val projects: List<Project> = emptyList(),
    val selected: Project? = null,
    val tasks: List<Task> = emptyList(),
    val active: ActiveTimer? = null,
    val clockSkewMs: Long = 0,
    val summary: Summary? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("socialpilot", Context.MODE_PRIVATE)
    private val api = ApiClient(prefs.getString("server", UiState().serverUrl)!!, prefs.getString("token", null))

    private val _state = MutableStateFlow(UiState(serverUrl = api.baseUrl, loggedIn = api.token != null))
    val state: StateFlow<UiState> = _state

    init { if (api.token != null) refresh() else checkSetup() }

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        try { block() } catch (e: ApiException) {
            if (e.code == 401) { api.token = null; prefs.edit().remove("token").apply(); _state.update { it.copy(loggedIn = false) } }
            _state.update { it.copy(error = e.message) }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Kan ikke nå serveren: ${e.message}") }
        } finally { _state.update { it.copy(loading = false) } }
    }

    fun setServer(url: String) { _state.update { it.copy(serverUrl = url) } }

    fun checkSetup() = launchSafe {
        api.baseUrl = _state.value.serverUrl
        _state.update { it.copy(needsSetup = api.needsSetup()) }
    }

    fun login(user: String, pass: String) = launchSafe {
        api.baseUrl = _state.value.serverUrl
        val token = api.login(user, pass, register = _state.value.needsSetup)
        prefs.edit().putString("server", api.baseUrl).putString("token", token).apply()
        _state.update { it.copy(loggedIn = true, needsSetup = false) }
        loadAll()
    }

    fun logout() = launchSafe {
        api.logout(); prefs.edit().remove("token").apply()
        _state.update { UiState(serverUrl = it.serverUrl) }
    }

    fun refresh() = launchSafe { loadAll() }

    private suspend fun loadAll() {
        val projects = api.projects()
        val (active, serverTime) = api.activeTimer()
        val selected = _state.value.selected?.let { s -> projects.find { it.id == s.id } } ?: projects.firstOrNull()
        val tasks = selected?.let { api.tasks(it.id) } ?: emptyList()
        _state.update {
            it.copy(projects = projects, selected = selected, tasks = tasks, active = active,
                clockSkewMs = skew(serverTime), summary = api.summary())
        }
    }

    fun select(p: Project) = launchSafe {
        _state.update { it.copy(selected = p, tasks = api.tasks(p.id)) }
    }

    fun addProject(name: String) = launchSafe { api.createProject(name); loadAll() }

    fun addTask(title: String) = launchSafe {
        val p = _state.value.selected ?: return@launchSafe
        api.createTask(p.id, title); loadAll()
    }

    fun advanceTask(t: Task) = launchSafe {
        val next = when (t.status) { "todo" -> "doing"; "doing" -> "done"; else -> "todo" }
        api.setTaskStatus(t.id, next); loadAll()
    }

    fun toggleTimer(projectId: Int, taskId: Int? = null) = launchSafe {
        val a = _state.value.active
        if (a != null && a.projectId == projectId && taskId == null) api.stopTimer()
        else { val (active, serverTime) = api.startTimer(projectId, taskId); _state.update { it.copy(active = active, clockSkewMs = skew(serverTime)) } }
        loadAll()
    }

    fun stopTimer() = launchSafe { api.stopTimer(); loadAll() }

    private fun skew(serverTime: String) = System.currentTimeMillis() - OffsetDateTime.parse(serverTime).toInstant().toEpochMilli()

    fun elapsedSeconds(a: ActiveTimer, nowMs: Long): Long =
        ((nowMs - _state.value.clockSkewMs - OffsetDateTime.parse(a.startedAt).toInstant().toEpochMilli()) / 1000).coerceAtLeast(0)

    companion object {
        fun fmt(s: Long) = "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
        fun fmtHm(s: Long) = "%d:%02d".format(s / 3600, s % 3600 / 60)
    }
}
