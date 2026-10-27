package com.al3xiel.my_finances.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

internal fun mapAvailability(canAuthenticate: Int): AuthAvailability = when (canAuthenticate) {
    BiometricManager.BIOMETRIC_SUCCESS -> AuthAvailability.READY
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthAvailability.NO_CREDENTIAL_ENROLLED
    else -> AuthAvailability.UNSUPPORTED
}

internal fun mapError(errorCode: Int): AuthResult = when (errorCode) {
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED,
    -> AuthResult.Cancelled
    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
    -> AuthResult.LockedOut
    else -> AuthResult.Failed
}

/** La Activity en primer plano; `BiometricPrompt` necesita una `FragmentActivity`. */
@Singleton
class ForegroundActivityHolder @Inject constructor() {
    private var current: WeakReference<FragmentActivity>? = null

    fun attach(activity: FragmentActivity) {
        current = WeakReference(activity)
    }

    fun detach(activity: FragmentActivity) {
        if (current?.get() === activity) current = null
    }

    fun get(): FragmentActivity? = current?.get()
}

@Singleton
class BiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityHolder: ForegroundActivityHolder,
) : Authenticator {

    override fun availability(): AuthAvailability =
        mapAvailability(BiometricManager.from(context).canAuthenticate(AUTHENTICATORS))

    override suspend fun authenticate(prompt: AuthPrompt): AuthResult {
        val activity = activityHolder.get() ?: return AuthResult.Failed
        return suspendCancellableCoroutine { continuation ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(AuthResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(mapError(errorCode))
                }
            }
            val biometricPrompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(prompt.title)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .apply { prompt.subtitle?.let(::setSubtitle) }
                .build()
            continuation.invokeOnCancellation { biometricPrompt.cancelAuthentication() }
            biometricPrompt.authenticate(info)
        }
    }
}
