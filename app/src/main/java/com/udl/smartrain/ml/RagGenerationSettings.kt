package com.udl.smartrain.ml

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RagGenerationMode {
    LOCAL_FALLBACK,
    CLOUD_QWEN,
    REMOTE_BACKEND,
    CUSTOM
}

data class RagGenerationSettings(
    val mode: RagGenerationMode = RagGenerationMode.LOCAL_FALLBACK,
    val useRemoteRag: Boolean = false,
    val remoteRagBaseUrl: String = DEFAULT_REMOTE_RAG_BASE_URL,
    val useOllama: Boolean = false,
    val ollamaBaseUrl: String = "https://ollama.com",
    val ollamaModel: String = "qwen3-coder-next",
    val ollamaApiKey: String = ""
)

const val DEFAULT_REMOTE_RAG_BASE_URL = "http://192.168.1.75:8000"

fun RagGenerationSettings.resolved(): RagGenerationSettings {
    return when (mode) {
        RagGenerationMode.LOCAL_FALLBACK -> copy(
            useRemoteRag = false,
            useOllama = false
        )
        RagGenerationMode.CLOUD_QWEN -> copy(
            useRemoteRag = false,
            useOllama = true,
            ollamaBaseUrl = "https://ollama.com",
            ollamaModel = "qwen3-coder-next"
        )
        RagGenerationMode.REMOTE_BACKEND -> copy(
            useRemoteRag = true,
            useOllama = false,
            remoteRagBaseUrl = remoteRagBaseUrl.trim().ifBlank { RagGenerationSettings().remoteRagBaseUrl }
        )
        RagGenerationMode.CUSTOM -> copy(
            remoteRagBaseUrl = remoteRagBaseUrl.trim().ifBlank { RagGenerationSettings().remoteRagBaseUrl },
            ollamaBaseUrl = ollamaBaseUrl.trim().ifBlank { RagGenerationSettings().ollamaBaseUrl },
            ollamaModel = ollamaModel.trim().ifBlank { RagGenerationSettings().ollamaModel }
        )
    }
}

object DebugRagGenerationSettings {
    private val _settings = MutableStateFlow(RagGenerationSettings())
    val settings: StateFlow<RagGenerationSettings> = _settings.asStateFlow()

    fun replace(settings: RagGenerationSettings) {
        _settings.value = settings
    }

    fun reset() {
        _settings.value = RagGenerationSettings()
    }

    fun update(
        mode: RagGenerationMode,
        useRemoteRag: Boolean,
        remoteRagBaseUrl: String,
        useOllama: Boolean,
        ollamaBaseUrl: String,
        ollamaModel: String,
        ollamaApiKey: String
    ) {
        _settings.update {
            RagGenerationSettings(
                mode = mode,
                useRemoteRag = useRemoteRag,
                remoteRagBaseUrl = remoteRagBaseUrl.trim().ifBlank { RagGenerationSettings().remoteRagBaseUrl },
                useOllama = useOllama,
                ollamaBaseUrl = ollamaBaseUrl.trim().ifBlank { RagGenerationSettings().ollamaBaseUrl },
                ollamaModel = ollamaModel.trim().ifBlank { RagGenerationSettings().ollamaModel },
                ollamaApiKey = ollamaApiKey.trim()
            ).resolved()
        }
    }
}
