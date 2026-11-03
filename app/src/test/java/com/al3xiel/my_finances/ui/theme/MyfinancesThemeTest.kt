package com.al3xiel.my_finances.ui.theme

import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MyfinancesThemeTest {
    private fun kind(mode: ThemeMode, systemDark: Boolean = false, dynamic: Boolean = false, sdk: Int = 34) =
        schemeKindFor(mode, systemDark, dynamic, sdk)

    @Test fun `each mode picks its own scheme`() {
        assertEquals(SchemeKind.LIGHT, kind(ThemeMode.LIGHT, systemDark = true))
        assertEquals(SchemeKind.DARK, kind(ThemeMode.DARK))
        assertEquals(SchemeKind.AMOLED, kind(ThemeMode.AMOLED))
    }

    @Test fun `the system mode follows the phone`() {
        assertEquals(SchemeKind.LIGHT, kind(ThemeMode.SYSTEM, systemDark = false))
        assertEquals(SchemeKind.DARK, kind(ThemeMode.SYSTEM, systemDark = true))
    }

    @Test fun `the system color applies only when asked and from Android 12`() {
        assertEquals(SchemeKind.LIGHT, kind(ThemeMode.LIGHT, dynamic = false, sdk = 34))
        assertEquals(SchemeKind.LIGHT, kind(ThemeMode.LIGHT, dynamic = true, sdk = 30))
        assertEquals(SchemeKind.DYNAMIC_LIGHT, kind(ThemeMode.LIGHT, dynamic = true, sdk = 31))
        assertEquals(SchemeKind.DYNAMIC_DARK, kind(ThemeMode.DARK, dynamic = true, sdk = 34))
        assertEquals(SchemeKind.DYNAMIC_AMOLED, kind(ThemeMode.AMOLED, dynamic = true, sdk = 34))
        assertEquals(SchemeKind.AMOLED, kind(ThemeMode.AMOLED, dynamic = true, sdk = 28))
    }

    @Test fun `static kinds map to the turquoise schemes and dynamic kinds to none`() {
        assertSame(LightColors, staticSchemeFor(SchemeKind.LIGHT))
        assertSame(DarkColors, staticSchemeFor(SchemeKind.DARK))
        assertSame(AmoledColors, staticSchemeFor(SchemeKind.AMOLED))
        assertNull(staticSchemeFor(SchemeKind.DYNAMIC_DARK))
    }

    @Test fun `dark kinds use the dark finance colors`() {
        assertSame(FinanceColors.Light, financeColorsFor(SchemeKind.LIGHT))
        assertSame(FinanceColors.Light, financeColorsFor(SchemeKind.DYNAMIC_LIGHT))
        assertSame(FinanceColors.Dark, financeColorsFor(SchemeKind.DARK))
        assertSame(FinanceColors.Dark, financeColorsFor(SchemeKind.AMOLED))
        assertSame(FinanceColors.Dark, financeColorsFor(SchemeKind.DYNAMIC_AMOLED))
    }
}
