package com.udl.smartrain.data.local

import android.content.Context
import com.udl.smartrain.ml.RagGenerationSettings

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

    fun loadRagSettings(): RagGenerationSettings {
        val defaults = RagGenerationSettings()
        return RagGenerationSettings(
            useOllama = preferences.getBoolean(KEY_RAG_USE_OLLAMA, defaults.useOllama),
            ollamaBaseUrl = preferences.getString(KEY_RAG_BASE_URL, defaults.ollamaBaseUrl).orEmpty()
                .ifBlank { defaults.ollamaBaseUrl },
            ollamaModel = preferences.getString(KEY_RAG_MODEL, defaults.ollamaModel).orEmpty()
                .ifBlank { defaults.ollamaModel },
            ollamaApiKey = preferences.getString(KEY_RAG_API_KEY, defaults.ollamaApiKey).orEmpty()
        )
    }

    fun saveRagSettings(settings: RagGenerationSettings) {
        preferences.edit()
            .putBoolean(KEY_RAG_USE_OLLAMA, settings.useOllama)
            .putString(KEY_RAG_BASE_URL, settings.ollamaBaseUrl)
            .putString(KEY_RAG_MODEL, settings.ollamaModel)
            .putString(KEY_RAG_API_KEY, settings.ollamaApiKey)
            .apply()
    }

    fun clearRagSettings() {
        preferences.edit()
            .remove(KEY_RAG_USE_OLLAMA)
            .remove(KEY_RAG_BASE_URL)
            .remove(KEY_RAG_MODEL)
            .remove(KEY_RAG_API_KEY)
            .apply()
    }

    fun loadLanguage(): AppLanguage {
        return preferences.getString(KEY_LANGUAGE, AppLanguage.CATALAN.name)
            ?.let { stored -> AppLanguage.entries.firstOrNull { it.name == stored } }
            ?: AppLanguage.CATALAN
    }

    fun saveLanguage(language: AppLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, language.name).apply()
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
        const val KEY_RAG_USE_OLLAMA = "rag_use_ollama"
        const val KEY_RAG_BASE_URL = "rag_base_url"
        const val KEY_RAG_MODEL = "rag_model"
        const val KEY_RAG_API_KEY = "rag_api_key"
        const val KEY_LANGUAGE = "app_language"
        const val USER_SEPARATOR = "|"
        const val FIELD_SEPARATOR = ":"
        const val MAX_REMEMBERED_USERS = 5
    }
}
