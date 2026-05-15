package com.udl.smartrain.ml

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RagGenerationSettings(
    val useRemoteRag: Boolean = false,
    val remoteRagBaseUrl: String = "http://10.0.2.2:8000",
    val useOllama: Boolean = false,
    val ollamaBaseUrl: String = "http://10.0.2.2:11434",
    val ollamaModel: String = "qwen3-coder-next",
    val ollamaApiKey: String = ""
)

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
        useRemoteRag: Boolean,
        remoteRagBaseUrl: String,
        useOllama: Boolean,
        ollamaBaseUrl: String,
        ollamaModel: String,
        ollamaApiKey: String
    ) {
        _settings.update {
            RagGenerationSettings(
                useRemoteRag = useRemoteRag,
                remoteRagBaseUrl = remoteRagBaseUrl.trim().ifBlank { RagGenerationSettings().remoteRagBaseUrl },
                useOllama = useOllama,
                ollamaBaseUrl = ollamaBaseUrl.trim().ifBlank { RagGenerationSettings().ollamaBaseUrl },
                ollamaModel = ollamaModel.trim().ifBlank { RagGenerationSettings().ollamaModel },
                ollamaApiKey = ollamaApiKey.trim()
            )
        }
    }
}
