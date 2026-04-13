package com.verum.omnis.core

interface LocalModelEngine {
    fun warmUp()
    fun isAvailable(): Boolean
    fun run(request: ModelRequest): ModelResponse
}

data class ModelRequest(
    val role: ModelRole,
    val prompt: String,
    val inputJson: String,
    val maxTokens: Int = 1200,
    val temperature: Float = 0.1f,
    val topK: Int = 20,
    val topP: Float = 0.9f
)

data class ModelResponse(
    val ok: Boolean,
    val modelName: String,
    val rawText: String,
    val latencyMs: Long,
    val error: String? = null
)

enum class ModelRole {
    EXTRACTION_ASSIST,
    REPORT_RENDER,
    REPORT_AUDIT
}
