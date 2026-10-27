package com.al3xiel.my_finances

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.navigation.MainScaffold
import com.al3xiel.my_finances.core.security.ForegroundActivityHolder
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import com.al3xiel.my_finances.feature.privacy.domain.repository.ThemeRepository
import com.al3xiel.my_finances.feature.privacy.ui.AppLockGate
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

const val APP_CONTENT_TAG = "app_content"

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var securitySettings: SecuritySettingsRepository

    @Inject
    lateinit var themeRepository: ThemeRepository

    @Inject
    lateinit var activityHolder: ForegroundActivityHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Seguro desde el primer fotograma; el ajuste lo relaja después si el usuario lo desactivó.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        activityHolder.attach(this)
        enableEdgeToEdge()
        setContent {
            // Falla del lado seguro mientras se leen los ajustes.
            val settings by securitySettings.settings.collectAsStateWithLifecycle(
                initialValue = SecuritySettings(hideAmounts = true),
            )
            val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            LaunchedEffect(settings.secureScreen) { applySecureScreen(settings.secureScreen) }
            MyfinancesTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalHideAmounts provides settings.hideAmounts) {
                    AppLockGate {
                        Box(Modifier.fillMaxSize().testTag(APP_CONTENT_TAG)) { MainScaffold() }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        activityHolder.detach(this)
        super.onDestroy()
    }

    private fun applySecureScreen(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
