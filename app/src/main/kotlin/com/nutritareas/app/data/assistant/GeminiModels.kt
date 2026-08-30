package com.nutritareas.app.data.assistant

import kotlinx.serialization.Serializable

/** Wire models for the Gemini REST API (`generativelanguage.googleapis.com/v1beta`). */
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

/** Only used for image generation - asks the model to return an image part, not just text. */
@Serializable
data class GeminiGenerationConfig(
    val responseModalities: List<String>? = null,
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart> = emptyList(),
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null,
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String,
)

@Serializable
data class GeminiStreamChunk(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

/** Shape of a non-2xx response body, e.g. `{"error": {"code": 429, "message": "...", "status": "RESOURCE_EXHAUSTED"}}`. */
@Serializable
data class GeminiErrorResponse(val error: GeminiErrorDetail? = null)

@Serializable
data class GeminiErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)
