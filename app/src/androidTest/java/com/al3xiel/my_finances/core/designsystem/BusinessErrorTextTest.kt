package com.al3xiel.my_finances.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.al3xiel.my_finances.domain.model.BusinessError
import org.junit.Rule
import org.junit.Test

/** Un error de "falta elegir algo" dice qué falta y dónde crearlo; nunca el genérico "No se encontró". */
class BusinessErrorTextTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(error: BusinessError) = rule.setContent { Text(error.message()) }

    @Test fun aMissingCategoryExplainsWhereToCreateOne() {
        show(BusinessError.CategoryRequired)
        rule.onNodeWithText("Elige una categoría para el gasto. Si aún no tienes, créala en Más → Categorías.").assertIsDisplayed()
    }

    @Test fun aMissingIncomeSourceExplainsWhereToCreateOne() {
        show(BusinessError.IncomeSourceRequired)
        rule.onNodeWithText("Elige una fuente de ingreso. Si aún no tienes, créala en Más → Fuentes de ingreso.").assertIsDisplayed()
    }

    @Test fun aMissingAccountIsNamed() {
        show(BusinessError.AccountRequired)
        rule.onNodeWithText("Elige la cuenta del movimiento.").assertIsDisplayed()
    }

    @Test fun aMissingDestinationIsNamed() {
        show(BusinessError.DestinationAccountRequired)
        rule.onNodeWithText("Elige la cuenta de destino de la transferencia.").assertIsDisplayed()
    }
}
