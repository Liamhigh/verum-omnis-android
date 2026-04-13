package com.verum.omnis.core

import android.content.Context

class Gemma4Engine(
    private val context: Context
) : LocalModelEngine {

    override fun warmUp() {
        // Runtime is warmed elsewhere in the app. This wrapper keeps the interface stable.
    }

    override fun isAvailable(): Boolean {
        return GemmaRuntime.getInstance().getStatus().contains("ready", ignoreCase = true)
    }

    override fun run(request: ModelRequest): ModelResponse {
        val started = System.currentTimeMillis()
        return try {
            val response = GemmaRuntime.getInstance().generateResponseBlocking(
                context.applicationContext,
                request.prompt + "\n\nINPUT JSON:\n" + request.inputJson
            )
            ModelResponse(
                ok = true,
                modelName = "gemma-4-bounded",
                rawText = response,
                latencyMs = System.currentTimeMillis() - started
            )
        } catch (t: Throwable) {
            ModelResponse(
                ok = false,
                modelName = "gemma-4-bounded",
                rawText = "",
                latencyMs = System.currentTimeMillis() - started,
                error = t.message
            )
        }
    }
}
