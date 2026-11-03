package com.al3xiel.my_finances

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.id.UuidIdGenerator
import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.Authenticator
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.data.projection.ProjectorSetFactory
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.data.AccountRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateInitialAccountsUseCase
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderScheduler
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import com.al3xiel.my_finances.feature.onboarding.data.OnboardingPreferences
import com.al3xiel.my_finances.feature.onboarding.domain.model.OnboardingStep
import com.al3xiel.my_finances.feature.onboarding.domain.usecase.ResolveOnboardingStepUseCase
import com.al3xiel.my_finances.feature.onboarding.ui.AccountsSetupUiAction
import com.al3xiel.my_finances.feature.onboarding.ui.AccountsSetupViewModel
import com.al3xiel.my_finances.feature.onboarding.ui.BackupSetupUiAction
import com.al3xiel.my_finances.feature.onboarding.ui.BackupSetupViewModel
import com.al3xiel.my_finances.feature.onboarding.ui.OnboardingGateState
import com.al3xiel.my_finances.feature.onboarding.ui.OnboardingGateViewModel
import com.al3xiel.my_finances.feature.onboarding.ui.SecuritySetupUiAction
import com.al3xiel.my_finances.feature.onboarding.ui.SecuritySetupViewModel
import com.al3xiel.my_finances.feature.privacy.data.DataStoreSecuritySettingsRepository
import com.al3xiel.my_finances.feature.privacy.data.PreferencesWipeStep
import java.io.File
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Primer uso de punta a punta con repositorios reales (base Room en memoria y DataStore de archivo), sin la capa de Hilt:
 * instalación limpia → S02 → S03 (tres cuentas con saldo) → S04 → S05 → app; cerrar a mitad retoma en el paso que toca;
 * un respaldo restaurado (cuentas sin bandera) cae en S04; borrar todos los datos vuelve a S02.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    private val ids = UuidIdGenerator()
    private val clock = Clock.systemUTC().withZone(ZoneId.of("America/Lima"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private lateinit var database: AppDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private val files = mutableListOf<File>()

    private val credential = object : Authenticator {
        override fun availability() = AuthAvailability.READY
        override suspend fun authenticate(prompt: AuthPrompt) = AuthResult.Success
    }

    private val backupSettings = object : BackupSettingsRepository {
        private val enabledState = MutableStateFlow(false)
        private val intervalState = MutableStateFlow(BackupSettingsRepository.DEFAULT_INTERVAL_DAYS)
        override val enabled = enabledState
        override val intervalDays = intervalState
        override suspend fun isEnabled() = enabledState.value
        override suspend fun setEnabled(enabled: Boolean) { enabledState.value = enabled }
        override suspend fun setIntervalDays(days: Int) { intervalState.value = days }
        override suspend fun lastRemindedAt() = null
        override suspend fun markReminded(at: java.time.Instant) = Unit
    }

    private val scheduler = object : BackupReminderScheduler {
        override fun ensureScheduled() = Unit
        override fun cancel() = Unit
    }

    private fun newDatabase() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .addCallback(EventTriggersCallback)
        .build()

    private fun newDataStore(): DataStore<Preferences> {
        val file = File(context.cacheDir, "onboarding-${ids.newId()}.preferences_pb").also(files::add)
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    @Before
    fun setUp() {
        database = newDatabase()
        dataStore = newDataStore()
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
        files.forEach(File::delete)
    }

    // «Abrir la app»: todo se arma de nuevo sobre la misma base y las mismas preferencias.
    private fun accounts() = AccountRepositoryImpl(database.catalogDao(), database.catalogQueryDao(), database.ledgerQueryDao())
    private fun onboarding() = OnboardingPreferences(dataStore)
    private fun resolve() = ResolveOnboardingStepUseCase(accounts(), onboarding())
    private fun gate() = OnboardingGateViewModel(resolve())

    private fun accountsSetup(): AccountsSetupViewModel {
        val store = EventStoreImpl(database, ProjectorSetFactory(database).create(), ids, clock, "1.0")
        return AccountsSetupViewModel(CreateInitialAccountsUseCase(accounts(), EventRecorder(store, ids, clock), ids))
    }

    private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T = withTimeout(10_000) { first(predicate) }

    private suspend fun gateState(vm: OnboardingGateViewModel) = vm.uiState.await { it != OnboardingGateState.Loading }

    private suspend fun createThreeAccounts() {
        val vm = accountsSetup()
        val keys = vm.uiState.value.rows.map { it.key }
        vm.onAction(AccountsSetupUiAction.EditBalance(keys[0], "1000"))
        vm.onAction(AccountsSetupUiAction.EditBalance(keys[1], "250.50"))
        vm.onAction(AccountsSetupUiAction.EditBalance(keys[2], "80"))
        vm.onAction(AccountsSetupUiAction.Submit)
        vm.uiState.await { it.created }
    }

    @Test
    fun cleanInstallWalksThroughToTheAppWithTheBalances() = runBlocking {
        val running = gate()
        assertEquals(OnboardingGateState.Flow(OnboardingStep.Welcome), gateState(running))

        createThreeAccounts()
        val balances = accounts().observeAccounts().first().associate { it.account.name to it.balanceMinor }
        assertEquals(mapOf("BBVA" to 100_000L, "BCP" to 25_050L, "Efectivo" to 8_000L), balances)
        // El paso resuelto cambió, pero el flujo en curso no se reinicia.
        assertEquals(OnboardingGateState.Flow(OnboardingStep.Welcome), running.uiState.value)

        val security = SecuritySetupViewModel(DataStoreSecuritySettingsRepository(dataStore), credential)
        security.onAction(SecuritySetupUiAction.Save)
        security.uiState.await { it.saved }
        assertEquals(true, DataStoreSecuritySettingsRepository(dataStore).settings.first().lockEnabled)

        val backup = BackupSetupViewModel(backupSettings, scheduler, onboarding())
        backup.onAction(BackupSetupUiAction.Finish)
        assertEquals(OnboardingGateState.App, running.uiState.await { it == OnboardingGateState.App })

        // Reabrir la app: directo a la app, con los saldos.
        assertEquals(OnboardingGateState.App, gateState(gate()))
    }

    @Test
    fun closingTheAppMidwayResumesAtTheStepThatCorresponds() = runBlocking {
        assertEquals(OnboardingGateState.Flow(OnboardingStep.Welcome), gateState(gate()))

        createThreeAccounts()

        // Con cuentas y sin bandera S03 no vuelve a mostrarse: se retoma en S04.
        assertEquals(OnboardingGateState.Flow(OnboardingStep.Security), gateState(gate()))
    }

    @Test
    fun restoredDataWithoutTheFlagLandsOnSecurity() = runBlocking {
        // Restaurar reinicia el proceso (D44) y deja cuentas en la base; la bandera vive en otro almacén y no viaja (BK4).
        createThreeAccounts()
        dataStore = newDataStore()

        assertEquals(OnboardingGateState.Flow(OnboardingStep.Security), gateState(gate()))
    }

    @Test
    fun wipingAllDataGoesBackToWelcome() = runBlocking {
        createThreeAccounts()
        onboarding().markCompleted()
        assertEquals(OnboardingGateState.App, gateState(gate()))

        // «Borrar todos mis datos»: preferencias (con la bandera) y base.
        PreferencesWipeStep(dataStore).wipe()
        database.close()
        database = newDatabase()

        assertEquals(OnboardingGateState.Flow(OnboardingStep.Welcome), gateState(gate()))
    }
}
