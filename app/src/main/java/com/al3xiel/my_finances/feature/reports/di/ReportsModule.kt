package com.al3xiel.my_finances.feature.reports.di

import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.projection.origin.OriginQueryDao
import com.al3xiel.my_finances.data.projection.report.ReportQueryDao
import com.al3xiel.my_finances.feature.reports.data.OriginRepositoryImpl
import com.al3xiel.my_finances.feature.reports.data.ReportFileRepositoryImpl
import com.al3xiel.my_finances.feature.reports.data.ReportRepositoryImpl
import com.al3xiel.my_finances.feature.reports.domain.repository.OriginRepository
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportFileRepository
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface ReportsModule {
    @Binds
    fun reportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds
    fun originRepository(impl: OriginRepositoryImpl): OriginRepository

    @Binds
    fun reportFileRepository(impl: ReportFileRepositoryImpl): ReportFileRepository

    companion object {
        @Provides
        fun provideReportQueryDao(database: AppDatabase): ReportQueryDao = database.reportQueryDao()

        @Provides
        fun provideOriginQueryDao(database: AppDatabase): OriginQueryDao = database.originQueryDao()
    }
}
