package com.nutritareas.app.data.assistant

/** UI-agnostic classification of what can go wrong talking to Claude, for the ViewModel to map to copy. */
sealed class AssistantError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingApiKey : AssistantError("Falta configurar la clave de API.")
    class InvalidApiKey(cause: Throwable) : AssistantError("Clave de API inválida o sin permisos.", cause)
    class RateLimited(cause: Throwable) : AssistantError("Límite de uso de la API alcanzado.", cause)
    class ModelNotFound(cause: Throwable) : AssistantError("Modelo no encontrado.", cause)
    class ServerError(cause: Throwable) : AssistantError("Error del servicio de IA.", cause)
    class Network(cause: Throwable) : AssistantError("No hay conexión de red.", cause)
    class Unknown(cause: Throwable) : AssistantError(cause.message ?: "Error desconocido.", cause)
}
