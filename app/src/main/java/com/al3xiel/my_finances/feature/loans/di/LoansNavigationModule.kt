package com.al3xiel.my_finances.feature.loans.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.core.navigation.LoanDetail
import com.al3xiel.my_finances.core.navigation.LoanEditor
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.loans.ui.LoanDetailScreen
import com.al3xiel.my_finances.feature.loans.ui.LoanEditorScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object LoansNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<LoanEditor> { key ->
                val navigator = LocalNavigator.current
                LoanEditorScreen(personId = key.personId?.let(::PersonId), onFinished = navigator::back)
            }
            entry<LoanDetail> { key -> LoanDetailScreen(loanId = LoanId(key.id)) }
        }
    }
}
