package com.al3xiel.my_finances

import android.app.Application
import com.al3xiel.my_finances.core.startup.SeedInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class MyFinancesApp : Application() {
    @Inject lateinit var seedInitializer: SeedInitializer

    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        seedInitializer.start(appScope)
    }
}
