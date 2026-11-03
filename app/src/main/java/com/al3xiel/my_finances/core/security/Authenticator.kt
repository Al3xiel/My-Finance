package com.al3xiel.my_finances.core.security

enum class AuthAvailability { READY, NO_CREDENTIAL_ENROLLED, UNSUPPORTED }

sealed interface AuthResult {
    data object Success : AuthResult
    data object Cancelled : AuthResult
    data object LockedOut : AuthResult
    data object Failed : AuthResult
}

/** Textos ya resueltos por quien llama (strings.xml); el dominio no conoce `Context`. */
data class AuthPrompt(val title: String, val subtitle: String? = null)

interface Authenticator {
    fun availability(): AuthAvailability
    suspend fun authenticate(prompt: AuthPrompt): AuthResult
}
