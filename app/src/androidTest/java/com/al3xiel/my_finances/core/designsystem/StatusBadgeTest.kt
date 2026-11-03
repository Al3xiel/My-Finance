package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatusBadgeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test fun everyStatusShowsItsNameAsTextNotOnlyAsColor() {
        rule.setContent {
            MyfinancesTheme {
                Column { BadgeStatus.entries.forEach { StatusBadge(it) } }
            }
        }
        listOf("Pendiente", "Vencido", "Necesita revisión", "Conciliado", "Estimado").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test fun aBadgeCanCarryItsOwnLabelKeepingTheStatusIconAndColor() {
        rule.setContent { MyfinancesTheme { Column { StatusBadge(BadgeStatus.RECONCILED, label = "Saldado") } } }
        rule.onNodeWithText("Saldado").assertIsDisplayed()
    }

    @Test fun everyStatusHasItsOwnIcon() {
        val icons = BadgeStatus.entries.map { it.icon() }
        assertEquals(BadgeStatus.entries.size, icons.toSet().size)
    }

    @Test fun iconBadgeExposesItsDescriptionWhenGiven() {
        rule.setContent { MyfinancesTheme { IconBadge(icon = AppIcons.Add, contentDescription = "Agregar cuenta") } }
        rule.onNodeWithContentDescription("Agregar cuenta").assertIsDisplayed()
    }
}
