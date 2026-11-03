package com.al3xiel.my_finances.feature.onboarding.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.Onboarding
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

internal const val ONBOARDING_STEPS = 4

/** Número de paso (S02 a S05) de una ruta del flujo; la restauración (S70) no es un paso. */
internal fun onboardingStepNumber(route: Route): Int? = when (route) {
    Onboarding.Welcome -> 1
    Onboarding.Accounts -> 2
    Onboarding.Security -> 3
    Onboarding.Backup -> 4
    else -> null
}

/** Marco del flujo de primer uso: «Paso n de 4» como texto y una barra. La barra es decorativa; el texto ya lo dice. */
@Composable
fun OnboardingProgress(step: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_step, step, ONBOARDING_STEPS),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { step / ONBOARDING_STEPS.toFloat() },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingProgressPreview() {
    MyfinancesTheme { OnboardingProgress(step = 2) }
}
