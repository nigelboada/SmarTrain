package com.udl.smartrain.data.local

import android.content.Context
import com.udl.smartrain.ml.RagGenerationSettings
import com.udl.smartrain.ml.RagGenerationMode
import com.udl.smartrain.ml.resolved

data class RememberedUser(
    val email: String,
    val password: String
)

enum class AppLanguage(val label: String) {
    ENGLISH("English"),
    CATALAN("Catala"),
    SPANISH("Castellano"),
    CHINESE("\u4e2d\u6587")
}

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getRememberedUsers(): List<RememberedUser> {
        return preferences.getString(KEY_REMEMBERED_USERS, "")
            .orEmpty()
            .split(USER_SEPARATOR)
            .mapNotNull { rawUser ->
                val parts = rawUser.split(FIELD_SEPARATOR)
                if (parts.size != 2 || parts[0].isBlank()) {
                    null
                } else {
                    RememberedUser(
                        email = parts[0].decodePreferenceValue(),
                        password = parts[1].decodePreferenceValue()
                    )
                }
            }
    }

    fun saveRememberedUser(email: String, password: String) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            return
        }
        val updatedUsers = (getRememberedUsers()
            .filterNot { it.email.equals(normalizedEmail, ignoreCase = true) } +
            RememberedUser(normalizedEmail, password))
            .takeLast(MAX_REMEMBERED_USERS)

        preferences.edit()
            .putString(
                KEY_REMEMBERED_USERS,
                updatedUsers.joinToString(USER_SEPARATOR) { user ->
                    "${user.email.encodePreferenceValue()}$FIELD_SEPARATOR${user.password.encodePreferenceValue()}"
                }
            )
            .apply()
    }

    fun loadUserName(): String {
        return preferences.getString(KEY_USER_NAME, "").orEmpty()
    }

    fun saveUserName(name: String) {
        preferences.edit().putString(KEY_USER_NAME, name.trim()).apply()
    }

    fun loadRagSettings(): RagGenerationSettings {
        val defaults = RagGenerationSettings()
        val storedMode = preferences.getString(KEY_RAG_MODE, null)
            ?.let { raw -> RagGenerationMode.entries.firstOrNull { it.name == raw } }
            ?: legacyRagMode(defaults)
        return RagGenerationSettings(
            mode = storedMode,
            useRemoteRag = preferences.getBoolean(KEY_RAG_USE_REMOTE, defaults.useRemoteRag),
            remoteRagBaseUrl = preferences.getString(KEY_RAG_REMOTE_BASE_URL, defaults.remoteRagBaseUrl).orEmpty()
                .ifBlank { defaults.remoteRagBaseUrl },
            useOllama = preferences.getBoolean(KEY_RAG_USE_OLLAMA, defaults.useOllama),
            ollamaBaseUrl = preferences.getString(KEY_RAG_BASE_URL, defaults.ollamaBaseUrl).orEmpty()
                .ifBlank { defaults.ollamaBaseUrl },
            ollamaModel = preferences.getString(KEY_RAG_MODEL, defaults.ollamaModel).orEmpty()
                .ifBlank { defaults.ollamaModel },
            ollamaApiKey = preferences.getString(KEY_RAG_API_KEY, defaults.ollamaApiKey).orEmpty()
        ).resolved()
    }

    fun saveRagSettings(settings: RagGenerationSettings) {
        val resolved = settings.resolved()
        preferences.edit()
            .putString(KEY_RAG_MODE, resolved.mode.name)
            .putBoolean(KEY_RAG_USE_REMOTE, resolved.useRemoteRag)
            .putString(KEY_RAG_REMOTE_BASE_URL, resolved.remoteRagBaseUrl)
            .putBoolean(KEY_RAG_USE_OLLAMA, resolved.useOllama)
            .putString(KEY_RAG_BASE_URL, resolved.ollamaBaseUrl)
            .putString(KEY_RAG_MODEL, resolved.ollamaModel)
            .putString(KEY_RAG_API_KEY, resolved.ollamaApiKey)
            .apply()
    }

    fun clearRagSettings() {
        preferences.edit()
            .remove(KEY_RAG_USE_REMOTE)
            .remove(KEY_RAG_MODE)
            .remove(KEY_RAG_REMOTE_BASE_URL)
            .remove(KEY_RAG_USE_OLLAMA)
            .remove(KEY_RAG_BASE_URL)
            .remove(KEY_RAG_MODEL)
            .remove(KEY_RAG_API_KEY)
            .apply()
    }

    private fun legacyRagMode(defaults: RagGenerationSettings): RagGenerationMode {
        val useRemote = preferences.getBoolean(KEY_RAG_USE_REMOTE, defaults.useRemoteRag)
        val useOllama = preferences.getBoolean(KEY_RAG_USE_OLLAMA, defaults.useOllama)
        val baseUrl = preferences.getString(KEY_RAG_BASE_URL, defaults.ollamaBaseUrl).orEmpty()
        val model = preferences.getString(KEY_RAG_MODEL, defaults.ollamaModel).orEmpty()
        return when {
            useRemote -> RagGenerationMode.REMOTE_BACKEND
            useOllama && baseUrl == "https://ollama.com" && model == "qwen3-coder-next" -> RagGenerationMode.CLOUD_QWEN
            useOllama -> RagGenerationMode.CUSTOM
            else -> RagGenerationMode.LOCAL_FALLBACK
        }
    }

    fun loadLanguage(): AppLanguage {
        return preferences.getString(KEY_LANGUAGE, AppLanguage.CATALAN.name)
            ?.let { stored -> AppLanguage.entries.firstOrNull { it.name == stored } }
            ?: AppLanguage.CATALAN
    }

    fun saveLanguage(language: AppLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    fun loadDarkMode(): Boolean {
        return preferences.getBoolean(KEY_DARK_MODE, false)
    }

    fun saveDarkMode(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    private fun String.encodePreferenceValue(): String {
        return replace("%", "%25")
            .replace("|", "%7C")
            .replace(":", "%3A")
    }

    private fun String.decodePreferenceValue(): String {
        return replace("%3A", ":")
            .replace("%7C", "|")
            .replace("%25", "%")
    }

    private companion object {
        const val PREFERENCES_NAME = "smartrain_preferences"
        const val KEY_REMEMBERED_USERS = "remembered_users"
        const val KEY_USER_NAME = "user_name"
        const val KEY_RAG_MODE = "rag_mode"
        const val KEY_RAG_USE_REMOTE = "rag_use_remote"
        const val KEY_RAG_REMOTE_BASE_URL = "rag_remote_base_url"
        const val KEY_RAG_USE_OLLAMA = "rag_use_ollama"
        const val KEY_RAG_BASE_URL = "rag_base_url"
        const val KEY_RAG_MODEL = "rag_model"
        const val KEY_RAG_API_KEY = "rag_api_key"
        const val KEY_LANGUAGE = "app_language"
        const val KEY_DARK_MODE = "dark_mode"
        const val USER_SEPARATOR = "|"
        const val FIELD_SEPARATOR = ":"
        const val MAX_REMEMBERED_USERS = 5
    }
}
