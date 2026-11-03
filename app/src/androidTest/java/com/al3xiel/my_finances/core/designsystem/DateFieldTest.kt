package com.al3xiel.my_finances.core.designsystem

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DateFieldTest {
    @get:Rule
    val rule = createComposeRule()

    private val day = LocalDate.of(2026, 10, 26)
    private val confirm = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dialog_date_confirm)

    private val description = "Fecha: ${dayText(day)}"

    @Test
    fun isASingleButtonThatReadsLabelAndDay() {
        rule.setContent { DateField(label = "Fecha", value = day, onValueChange = {}) }
        rule.onNodeWithContentDescription(description).assertIsDisplayed()
        // El campo de texto de debajo no es otra parada de foco ni se lee dos veces.
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun touchOpensThePicker() {
        rule.setContent { DateField(label = "Fecha", value = day, onValueChange = {}, maxDate = day) }
        rule.onNodeWithContentDescription(description).performClick()
        rule.onNodeWithText(confirm).assertIsDisplayed()
    }

    @Test
    fun anAccessibilityClickOpensThePickerToo() {
        rule.setContent { DateField(label = "Fecha", value = day, onValueChange = {}, maxDate = day) }
        rule.onNode(hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText(confirm).assertIsDisplayed()
    }
}
