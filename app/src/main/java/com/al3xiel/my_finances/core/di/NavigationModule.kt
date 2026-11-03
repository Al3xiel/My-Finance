package com.al3xiel.my_finances.core.di

import com.al3xiel.my_finances.core.navigation.MoreMenuContributor
import com.al3xiel.my_finances.core.navigation.RegisterActionContributor
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.core.navigation.SettingsSectionContributor
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Declares the contribution sets so they exist (empty) even when no feature contributes. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {
    @Multibinds abstract fun routeContributors(): Set<@JvmSuppressWildcards RouteContributor>

    @Multibinds abstract fun registerActionContributors(): Set<@JvmSuppressWildcards RegisterActionContributor>

    @Multibinds abstract fun moreMenuContributors(): Set<@JvmSuppressWildcards MoreMenuContributor>

    @Multibinds abstract fun settingsSectionContributors(): Set<@JvmSuppressWildcards SettingsSectionContributor>
}
