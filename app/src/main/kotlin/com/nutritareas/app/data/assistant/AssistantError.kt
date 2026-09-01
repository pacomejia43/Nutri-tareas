package com.nutritareas.app.data.assistant

import java.net.SocketTimeoutException

/** UI-agnostic classification of what can go wrong talking to Claude, for the ViewModel to map to copy. */
sealed class AssistantError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingApiKey : AssistantError("Falta configurar la clave de API.")
    class InvalidApiKey(cause: Throwable) : AssistantError("Clave de API inválida o sin permisos.", cause)
    class RateLimited(cause: Throwable) : AssistantError("Límite de uso de la API alcanzado.", cause)
    class ModelNotFound(cause: Throwable) : AssistantError("Modelo no encontrado.", cause)
    class ServerError(cause: Throwable) : AssistantError("Error del servicio de IA.", cause)
    class Network(cause: Throwable) : AssistantError("No hay conexión de red.", cause)
    class Timeout(cause: Throwable) : AssistantError("La solicitud tardó demasiado en responder.", cause)
    class Unknown(cause: Throwable) : AssistantError(cause.message ?: "Error desconocido.", cause)
}

/**
 * True when [this] or any exception in its cause chain is a socket timeout - both Gemini's raw
 * OkHttp client and Claude's Anthropic SDK client surface a slow-but-otherwise-fine request as the
 * same generic IOException-family type as a real "no connection" failure, so this is how the two
 * get told apart before picking [AssistantError.Timeout] vs [AssistantError.Network].
 */
fun Throwable.hasTimeoutCause(): Boolean = generateSequence(this) { it.cause }.any { it is SocketTimeoutException }
