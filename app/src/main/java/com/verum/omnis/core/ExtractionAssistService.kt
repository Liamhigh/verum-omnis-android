package com.verum.omnis.core

class ExtractionAssistService(
    private val gemma4: LocalModelEngine
) {

    fun extract(input: ExtractionAssistInput): ExtractionAssistOutput {
        val response = gemma4.run(
            ModelRequest(
                role = ModelRole.EXTRACTION_ASSIST,
                prompt = Prompts.EXTRACTION_ASSIST_PROMPT,
                inputJson = input.toJson(),
                maxTokens = 1200,
                temperature = 0f
            )
        )
        if (!response.ok) {
            error(response.error ?: "Extraction assist failed")
        }
        return ExtractionAssistOutput.fromJson(response.rawText)
    }
}
