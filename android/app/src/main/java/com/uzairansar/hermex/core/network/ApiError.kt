package com.uzairansar.hermex.core.network

sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : ApiError(cause.message ?: "Network request failed.", cause)
    class Http(val statusCode: Int, val body: String?) : ApiError(httpErrorMessage(statusCode, body))
    data object Unauthorized : ApiError("Unauthorized.")
    class Decoding(cause: Throwable) : ApiError(cause.message ?: "Failed to decode response.", cause)
    class InvalidResponse(message: String) : ApiError(message)
    class ResponseTooLarge(val limitBytes: Long) : ApiError("Response exceeded the ${limitBytes / (1024 * 1024)} MB safety limit.")
    class InsecureTransport(host: String) : ApiError("Plain HTTP is only allowed for local or private-network servers, not $host.")
}

private fun httpErrorMessage(statusCode: Int, body: String?): String {
    val normalizedBody = body
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return "HTTP $statusCode request failed."
    if (normalizedBody.startsWith("<", ignoreCase = true)) return "HTTP $statusCode request failed."
    return "HTTP $statusCode: ${normalizedBody.take(MAXIMUM_HTTP_ERROR_MESSAGE_CHARACTERS)}"
}

private const val MAXIMUM_HTTP_ERROR_MESSAGE_CHARACTERS = 500
