package com.udl.smartrain.ml

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RagGenerationSettings(
    val useOllama: Boolean = false,
    val ollamaBaseUrl: String = "http://10.0.2.2:11434",
    val ollamaModel: String = "gemma3:1b",
    val ollamaApiKey: String = ""
)

object DebugRagGenerationSettings {
    private val _settings = MutableStateFlow(RagGenerationSettings())
    val settings: StateFlow<RagGenerationSettings> = _settings.asStateFlow()

    fun update(useOllama: Boolean, ollamaBaseUrl: String, ollamaModel: String, ollamaApiKey: String) {
        _settings.update {
            RagGenerationSettings(
                useOllama = useOllama,
                ollamaBaseUrl = ollamaBaseUrl.trim().ifBlank { RagGenerationSettings().ollamaBaseUrl },
                ollamaModel = ollamaModel.trim().ifBlank { RagGenerationSettings().ollamaModel },
                ollamaApiKey = ollamaApiKey.trim()
            )
        }
    }
}
