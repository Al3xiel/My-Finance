package com.al3xiel.my_finances

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.security.ForegroundActivityHolder
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import com.al3xiel.my_finances.feature.privacy.domain.repository.ThemeRepository
import com.al3xiel.my_finances.feature.privacy.ui.AppLockGate
import com.al3xiel.my_finances.feature.privacy.ui.PrivacyScreen
import com.al3xiel.my_finances.feature.privacy.ui.SettingsScreen
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
                    AppLockGate { TemporaryHost() }
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

/** Anfitrión temporal hasta que llegue `core/navigation`: dos entradas, Privacidad y Ajustes. */
@Composable
private fun TemporaryHost() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.host_tab_privacy, R.string.host_tab_settings)
    Scaffold(modifier = Modifier.fillMaxSize().testTag(APP_CONTENT_TAG)) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = selected) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selected == index, onClick = { selected = index }, text = { Text(stringResource(title)) })
                }
            }
            if (selected == 0) PrivacyScreen() else SettingsScreen()
        }
    }
}
