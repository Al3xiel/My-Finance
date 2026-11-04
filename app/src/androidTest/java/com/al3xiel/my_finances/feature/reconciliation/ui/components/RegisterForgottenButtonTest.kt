package com.al3xiel.my_finances.feature.reconciliation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class RegisterForgottenButtonTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** El botón queda bajo otras cosas, como en S61: si el menú se ancla al lugar equivocado aparece lejos de él. */
    private fun show(onRegister: (MovementType) -> Unit = {}) {
        rule.setContent {
            MyfinancesTheme {
                // Un contenido más alto que lo visible y desplazable, como la pantalla real: el botón queda al fondo.
                Column(Modifier.height(500.dp).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(900.dp))
                    RegisterForgottenButton(onRegister)
                    Spacer(Modifier.height(200.dp))
                }
            }
        }
    }

    @Test fun theMenuOpensRightNextToItsButton() {
        show()
        val button = rule.onNodeWithText(string(R.string.reconciliation_register_forgotten))
        button.performScrollTo().performClick()
        val item = rule.onNodeWithText(string(R.string.movement_type_expense))
        item.assertIsDisplayed()

        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val buttonY = button.fetchSemanticsNode().positionOnScreen.y
        val itemY = item.fetchSemanticsNode().positionOnScreen.y
        assertTrue("el menú aparece a ${abs(itemY - buttonY) / density} dp de su botón", abs(itemY - buttonY) < 150 * density)
    }

    @Test fun choosingATypeReportsItAndClosesTheMenu() {
        var chosen: MovementType? = null
        show { chosen = it }
        rule.onNodeWithText(string(R.string.reconciliation_register_forgotten)).performScrollTo().performClick()
        rule.onNodeWithText(string(R.string.movement_type_income)).performClick()
        assertEquals(MovementType.INCOME, chosen)
        rule.onNodeWithText(string(R.string.movement_type_transfer)).assertDoesNotExist()
    }
}
