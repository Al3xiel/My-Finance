package com.al3xiel.my_finances.core.navigation

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScaffoldViewModelTest {

    private val expense = RegisterAction("expense", 1, Privacy, order = 10)

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        actions: List<RegisterAction> = listOf(expense),
    ) = MainScaffoldViewModel(
        handle,
        RegisterActionRegistry(setOf(RegisterActionContributor { actions })),
        MoreMenuRegistry(setOf(MoreMenuContributor { listOf(MoreMenuEntry("privacy", 1, Privacy, order = 1)) })),
        emptySet(),
    )

    @Test
    fun `starts on Home`() {
        assertEquals(MainTab.HOME, viewModel().uiState.value.stacks.selected)
    }

    @Test
    fun `exposes the more menu entries`() {
        assertEquals(listOf("privacy"), viewModel().uiState.value.moreEntries.map { it.id })
    }

    @Test
    fun `select tab and push update the stacks`() {
        val vm = viewModel()
        vm.onAction(MainScaffoldUiAction.SelectTab(MainTab.MORE))
        vm.onAction(MainScaffoldUiAction.Push(Settings))
        assertEquals(listOf(More, Settings), vm.uiState.value.stacks.activeStack)
    }

    @Test
    fun `stacks survive process death`() {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.onAction(MainScaffoldUiAction.SelectTab(MainTab.MORE))
        first.onAction(MainScaffoldUiAction.Push(Privacy))
        val restored = viewModel(handle)
        assertEquals(MainTab.MORE, restored.uiState.value.stacks.selected)
        assertEquals(listOf(More, Privacy), restored.uiState.value.stacks.activeStack)
    }

    @Test
    fun `corrupt saved state falls back to the initial stacks`() {
        val handle = SavedStateHandle(mapOf("tab_stacks" to "{not json"))
        assertEquals(TabBackStacks(), viewModel(handle).uiState.value.stacks)
    }

    @Test
    fun `back pops and can go back only while there is somewhere to go`() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.canGoBack)
        vm.onAction(MainScaffoldUiAction.SelectTab(MainTab.REPORTS))
        assertTrue(vm.uiState.value.canGoBack)
        vm.onAction(MainScaffoldUiAction.Back)
        assertEquals(MainTab.HOME, vm.uiState.value.stacks.selected)
    }

    @Test
    fun `fab shows only on a root of Home Movements Receivables and with actions`() {
        val vm = viewModel()
        assertTrue(vm.uiState.value.showRegisterFab)
        vm.onAction(MainScaffoldUiAction.SelectTab(MainTab.REPORTS))
        assertFalse(vm.uiState.value.showRegisterFab)
        vm.onAction(MainScaffoldUiAction.SelectTab(MainTab.MOVEMENTS))
        assertTrue(vm.uiState.value.showRegisterFab)
        assertFalse(viewModel(actions = emptyList()).uiState.value.showRegisterFab)
    }

    @Test
    fun `choosing an action pushes its route on the active tab and closes the sheet`() {
        val vm = viewModel()
        vm.onAction(MainScaffoldUiAction.OpenRegisterSheet)
        assertTrue(vm.uiState.value.registerSheetVisible)
        vm.onAction(MainScaffoldUiAction.ChooseRegisterAction(expense))
        assertFalse(vm.uiState.value.registerSheetVisible)
        assertEquals(listOf(Home, Privacy), vm.uiState.value.stacks.activeStack)
    }
}
