package com.al3xiel.my_finances.feature.catalogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import com.al3xiel.my_finances.feature.accounts.ui.IncomeSourcesContent
import com.al3xiel.my_finances.feature.accounts.ui.IncomeSourcesUiAction
import com.al3xiel.my_finances.feature.accounts.ui.IncomeSourcesUiState
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.categories.ui.CategoriesContent
import com.al3xiel.my_finances.feature.categories.ui.CategoriesUiAction
import com.al3xiel.my_finances.feature.categories.ui.CategoriesUiState
import com.al3xiel.my_finances.feature.categories.ui.CategoryNode
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.ui.PeopleContent
import com.al3xiel.my_finances.feature.people.ui.PeopleUiAction
import com.al3xiel.my_finances.feature.people.ui.PeopleUiState
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import com.al3xiel.my_finances.feature.tags.ui.TagsContent
import com.al3xiel.my_finances.feature.tags.ui.TagsUiAction
import com.al3xiel.my_finances.feature.tags.ui.TagsUiState
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Los cuatro catálogos comparten marco: «Agregar» en el encabezado, filas en tarjeta y estado vacío con su acción. */
class CatalogScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun peopleListsAliasesAndAddsFromTheHeader() {
        val actions = mutableListOf<PeopleUiAction>()
        rule.setContent {
            MyfinancesTheme { PeopleContent(PeopleUiState(listOf(Person(PersonId("1"), "Mamá", false))), actions::add) }
        }
        rule.onNodeWithText("Mamá").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.catalog_add)).performClick()
        assertEquals(listOf<PeopleUiAction>(PeopleUiAction.ShowCreate), actions)
    }

    @Test
    fun categoriesShowTheirChildrenAndOfferAddingOneUnderEachParent() {
        val actions = mutableListOf<CategoriesUiAction>()
        val state = CategoriesUiState(
            nodes = listOf(
                CategoryNode(
                    Category(CategoryId("1"), "Comida", null, false),
                    listOf(Category(CategoryId("2"), "Restaurantes", CategoryId("1"), false)),
                ),
            ),
        )
        rule.setContent { MyfinancesTheme { CategoriesContent(state, actions::add) } }
        rule.onNodeWithText("Comida").assertIsDisplayed()
        rule.onNodeWithText("Restaurantes").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.categories_add_child)).performClick()
        assertEquals(listOf<CategoriesUiAction>(CategoriesUiAction.ShowCreate(parentId = CategoryId("1"))), actions)
    }

    @Test
    fun tagsAreGroupedByKind() {
        val state = TagsUiState(
            tags = listOf(
                Tag(TagId("1"), "Mamá", TagKind.PERSON, PersonId("p"), false),
                Tag(TagId("2"), "Viaje", TagKind.TOPIC, null, false),
            ),
        )
        rule.setContent { MyfinancesTheme { TagsContent(state, {}) } }
        rule.onNodeWithText(string(R.string.tags_section_person)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.tags_section_topic)).assertIsDisplayed()
        rule.onNodeWithText("Viaje").assertIsDisplayed()
    }

    @Test
    fun incomeSourcesMarkTheSystemOnesAndAddFromTheHeader() {
        val actions = mutableListOf<IncomeSourcesUiAction>()
        val state = IncomeSourcesUiState(
            sources = listOf(
                IncomeSource(IncomeSourceId("1"), "Sueldo", IncomeSourceKind.REGULAR, null),
                IncomeSource(IncomeSourceId("2"), "Reembolso", IncomeSourceKind.REIMBURSEMENT, PersonId("p")),
            ),
        )
        rule.setContent { MyfinancesTheme { IncomeSourcesContent(state, actions::add) } }
        rule.onNodeWithText("Sueldo").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.sources_system)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.catalog_add)).performClick()
        assertEquals(listOf<IncomeSourcesUiAction>(IncomeSourcesUiAction.ShowCreate), actions)
    }

    @Test
    fun anEmptyCatalogShowsTheEmptyState() {
        rule.setContent { MyfinancesTheme { PeopleContent(PeopleUiState(), {}) } }
        rule.onNodeWithText(string(R.string.catalog_empty_title)).assertIsDisplayed()
    }
}
