package eu.humle.socialpilot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Project(val id: Int, val name: String, val color: String, val taskCount: Int, val doneCount: Int, val totalSeconds: Long)
data class Task(val id: Int, val projectId: Int, val title: String, val status: String, val dueDate: String?)
data class ActiveTimer(val projectId: Int, val projectName: String, val taskTitle: String?, val startedAt: String)
data class Summary(val todaySeconds: Long, val weekSeconds: Long, val openTasks: Int)

class ApiException(val code: Int, message: String) : Exception(message)

/** Tynd klient mod FastAPI-backenden. Kun Android-indbyggede klasser — ingen ekstra libs. */
class ApiClient(var baseUrl: String, var token: String? = null) {

    private suspend fun call(method: String, path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) { doOutput = true; outputStream.use { it.write(body.toString().toByteArray()) } }
        }
        try {
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code >= 400) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                throw ApiException(code, detail?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            text
        } finally { conn.disconnect() }
    }

    suspend fun login(user: String, pass: String, register: Boolean = false): String {
        val res = JSONObject(call("POST", if (register) "/api/auth/register" else "/api/auth/login",
            JSONObject().put("username", user).put("password", pass)))
        return res.getString("token").also { token = it }
    }

    suspend fun needsSetup(): Boolean = JSONObject(call("GET", "/api/auth/setup")).getBoolean("needs_setup")

    suspend fun logout() { runCatching { call("POST", "/api/auth/logout") }; token = null }

    suspend fun projects(): List<Project> = JSONArray(call("GET", "/api/projects")).map {
        Project(it.getInt("id"), it.getString("name"), it.optString("color", "#6366f1"),
            it.optInt("task_count"), it.optInt("done_count"), it.optLong("total_seconds"))
    }

    suspend fun createProject(name: String) { call("POST", "/api/projects", JSONObject().put("name", name)) }

    suspend fun tasks(projectId: Int): List<Task> = JSONArray(call("GET", "/api/projects/$projectId/tasks")).map {
        Task(it.getInt("id"), it.getInt("project_id"), it.getString("title"), it.getString("status"),
            it.optString("due_date").takeIf { d -> d.isNotBlank() && d != "null" })
    }

    suspend fun createTask(projectId: Int, title: String) {
        call("POST", "/api/projects/$projectId/tasks", JSONObject().put("title", title))
    }

    suspend fun setTaskStatus(taskId: Int, status: String) {
        call("PATCH", "/api/tasks/$taskId", JSONObject().put("status", status))
    }

    /** Returnerer aktiv timer og serverens ur (til korrektion af tidsforskel). */
    suspend fun activeTimer(): Pair<ActiveTimer?, String> {
        val res = JSONObject(call("GET", "/api/timers/active"))
        return parseActive(res.optJSONObject("active")) to res.getString("server_time")
    }

    suspend fun startTimer(projectId: Int, taskId: Int? = null): Pair<ActiveTimer?, String> {
        val body = JSONObject().put("project_id", projectId).apply { if (taskId != null) put("task_id", taskId) }
        val res = JSONObject(call("POST", "/api/timers/start", body))
        return parseActive(res.optJSONObject("active")) to res.getString("server_time")
    }

    suspend fun stopTimer() { call("POST", "/api/timers/stop") }

    suspend fun summary(): Summary = JSONObject(call("GET", "/api/stats/summary")).let {
        Summary(it.getLong("today_seconds"), it.getLong("week_seconds"), it.getInt("open_tasks"))
    }

    private fun parseActive(o: JSONObject?) = o?.let {
        ActiveTimer(it.getInt("project_id"), it.getString("project_name"),
            it.optString("task_title").takeIf { t -> t.isNotBlank() && t != "null" }, it.getString("started_at"))
    }

    private inline fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
}
