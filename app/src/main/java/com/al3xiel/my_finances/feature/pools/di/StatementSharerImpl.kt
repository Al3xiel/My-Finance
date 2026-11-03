package com.al3xiel.my_finances.feature.pools.di

import android.content.Context
import android.content.Intent
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.di.IoDispatcher
import com.al3xiel.my_finances.feature.pools.data.StatementRenderer
import com.al3xiel.my_finances.feature.pools.ui.ShareRequest
import com.al3xiel.my_finances.feature.pools.ui.StatementSharer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class StatementSharerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: StatementRenderer,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : StatementSharer {
    override suspend fun shareIntent(request: ShareRequest): Intent? {
        val rendered = withContext(dispatcher) {
            try {
                renderer.render(request.statementId, request.content, request.format)
            } catch (_: IOException) {
                null
            }
        } ?: return null
        return renderer.shareIntent(rendered, context.getString(R.string.statement_share_title))
    }
}
