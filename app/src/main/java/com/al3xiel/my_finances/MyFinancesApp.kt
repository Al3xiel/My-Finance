package com.al3xiel.my_finances

import android.app.Application
import com.al3xiel.my_finances.core.share.SharedFilesCleaner
import com.al3xiel.my_finances.core.startup.SeedInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyFinancesApp : Application() {
    @Inject lateinit var seedInitializer: SeedInitializer
    @Inject lateinit var sharedFilesCleaner: SharedFilesCleaner

    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        seedInitializer.start(appScope)
        // Rendiciones que quedaron en la caché de una sesión anterior (docs/02 S7).
        appScope.launch { sharedFilesCleaner.clear() }
    }
}
