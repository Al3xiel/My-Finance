package com.al3xiel.my_finances.feature.people.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.LoanDetail
import com.al3xiel.my_finances.core.navigation.LoanEditor
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.MoreMenuContributor
import com.al3xiel.my_finances.core.navigation.MoreMenuEntry
import com.al3xiel.my_finances.core.navigation.People
import com.al3xiel.my_finances.core.navigation.PersonDetail
import com.al3xiel.my_finances.core.navigation.PoolDetail
import com.al3xiel.my_finances.core.navigation.ReimbursementEditor
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.people.ui.PeopleScreen
import com.al3xiel.my_finances.feature.people.ui.PersonDetailScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object PeopleNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<People> { PeopleScreen() }
            entry<PersonDetail> { key ->
                val navigator = LocalNavigator.current
                PersonDetailScreen(
                    personId = PersonId(key.id),
                    onOpenPool = { navigator.push(PoolDetail(it.value)) },
                    onOpenLoan = { navigator.push(LoanDetail(it.value)) },
                    onReceiveReimbursement = { navigator.push(ReimbursementEditor(personId = it.value)) },
                    onLend = { navigator.push(LoanEditor(it.value)) },
                )
            }
        }
    }

    @Provides
    @IntoSet
    fun moreMenu(): MoreMenuContributor = MoreMenuContributor {
        listOf(MoreMenuEntry("people", R.string.more_entry_people, People, order = 40))
    }
}
