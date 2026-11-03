package com.al3xiel.my_finances.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricAuthenticatorMappingTest {

    @Test
    fun successMeansReady() {
        assertEquals(AuthAvailability.READY, mapAvailability(BiometricManager.BIOMETRIC_SUCCESS))
    }

    @Test
    fun noneEnrolledMeansNoCredential() {
        assertEquals(
            AuthAvailability.NO_CREDENTIAL_ENROLLED,
            mapAvailability(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
        )
    }

    @Test
    fun missingHardwareOrUnknownMeansUnsupported() {
        listOf(
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
        ).forEach { assertEquals(AuthAvailability.UNSUPPORTED, mapAvailability(it)) }
    }

    @Test
    fun userDismissalsAreCancelled() {
        listOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED,
        ).forEach { assertEquals(AuthResult.Cancelled, mapError(it)) }
    }

    @Test
    fun lockoutsAreReported() {
        listOf(BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT)
            .forEach { assertEquals(AuthResult.LockedOut, mapError(it)) }
    }

    @Test
    fun otherErrorsAreFailures() {
        assertEquals(AuthResult.Failed, mapError(BiometricPrompt.ERROR_HW_UNAVAILABLE))
        assertEquals(AuthResult.Failed, mapError(BiometricPrompt.ERROR_NO_SPACE))
    }
}
