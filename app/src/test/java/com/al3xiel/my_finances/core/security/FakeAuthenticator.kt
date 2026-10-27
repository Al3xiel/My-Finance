package com.al3xiel.my_finances.core.security

class FakeAuthenticator(
    var availability: AuthAvailability = AuthAvailability.READY,
    var result: AuthResult = AuthResult.Success,
) : Authenticator {
    var calls = 0
        private set

    override fun availability() = availability

    override suspend fun authenticate(prompt: AuthPrompt): AuthResult {
        calls++
        return result
    }
}
