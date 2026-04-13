package com.verum.omnis.core

import android.content.Context

class Gemma2AuditEngine(
    private val context: Context
) : LocalModelEngine {

    override fun warmUp() {
        // Audit model uses the existing local Phi-3 / Gemma bridge when available.
    }

    override fun isAvailable(): Boolean {
        return GemmaRuntime.getInstance().isPhi3MiniRunnable(context.applicationContext)
    }

    override fun run(request: ModelRequest): ModelResponse {
        val started = System.currentTimeMillis()
        return try {
            val response = GemmaRuntime.getInstance().generateResponseBlockingWithPhi3Mini(
                context.applicationContext,
                request.prompt + "\n\nINPUT JSON:\n" + request.inputJson
            )
            ModelResponse(
                ok = true,
                modelName = "gemma-2-audit",
                rawText = response,
                latencyMs = System.currentTimeMillis() - started
            )
        } catch (t: Throwable) {
            ModelResponse(
                ok = false,
                modelName = "gemma-2-audit",
                rawText = "",
                latencyMs = System.currentTimeMillis() - started,
                error = t.message
            )
        }
    }
}
