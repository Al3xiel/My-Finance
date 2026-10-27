package com.al3xiel.my_finances.feature.accounts.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.AccountDetail
import com.al3xiel.my_finances.core.navigation.Accounts
import com.al3xiel.my_finances.core.navigation.IncomeSources
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.MoreMenuContributor
import com.al3xiel.my_finances.core.navigation.MoreMenuEntry
import com.al3xiel.my_finances.core.navigation.RegisterAction
import com.al3xiel.my_finances.core.navigation.RegisterActionContributor
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.core.navigation.TransferEditor
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.feature.accounts.ui.AccountDetailScreen
import com.al3xiel.my_finances.feature.accounts.ui.AccountsScreen
import com.al3xiel.my_finances.feature.accounts.ui.IncomeSourcesScreen
import com.al3xiel.my_finances.feature.accounts.ui.TransferEditorScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object AccountsNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<Accounts> {
                val navigator = LocalNavigator.current
                AccountsScreen(onOpenAccount = { navigator.push(AccountDetail(it.value)) })
            }
            entry<AccountDetail> { key -> AccountDetailScreen(accountId = AccountId(key.id)) }
            entry<TransferEditor> {
                val navigator = LocalNavigator.current
                TransferEditorScreen(onFinished = navigator::back)
            }
            entry<IncomeSources> { IncomeSourcesScreen() }
        }
    }

    @Provides
    @IntoSet
    fun registerActions(): RegisterActionContributor = RegisterActionContributor {
        listOf(RegisterAction("transfer", R.string.register_action_transfer, TransferEditor, order = 30))
    }

    @Provides
    @IntoSet
    fun moreMenu(): MoreMenuContributor = MoreMenuContributor {
        listOf(
            MoreMenuEntry("accounts", R.string.more_entry_accounts, Accounts, order = 10),
            MoreMenuEntry("income_sources", R.string.more_entry_income_sources, IncomeSources, order = 50),
        )
    }
}
