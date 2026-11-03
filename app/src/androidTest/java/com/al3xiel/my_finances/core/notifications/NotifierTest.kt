package com.al3xiel.my_finances.core.notifications

import android.Manifest
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotifierTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = Notifier(context)

    @Before
    fun grantPermission() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun tearDown() = manager.cancelAll()

    @Test
    fun theChannelExistsAndNeverForcesPublicContentOnTheLockScreen() {
        NotificationChannels.ensure(context)

        val channel = manager.getNotificationChannel(NotificationChannels.REMINDERS)
        assertNotNull(channel)
        // Android 16 reporta la visibilidad del canal como "sin anulación" (-1000) aunque se pida PRIVATE: lo que protege
        // es la visibilidad de cada notificación, que verifica la prueba de abajo.
        assertNotEquals(NotificationCompat.VISIBILITY_PUBLIC, channel.lockscreenVisibility)
    }

    @Test
    fun publishesAPrivateNotificationWithGenericTextAndAPublicVersion() {
        assertTrue(notifier.notifyPrivate(NotificationChannels.REMINDERS, 1, R.string.reminder_overdue_title, R.string.reminder_overdue_text))

        val notification = manager.activeNotifications.single { it.id == 1 }.notification
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.publicVersion)
        assertNotNull(notification.contentIntent)
        val text = notification.extras.getCharSequence("android.text").toString()
        assertFalse("sin montos ni cifras: $text", text.any { it.isDigit() })
        assertFalse("sin signo de moneda: $text", text.contains("S/"))
    }

    @Test
    fun theSameIdReplacesInsteadOfStacking() {
        repeat(2) { notifier.notifyPrivate(NotificationChannels.REMINDERS, 7, R.string.reminder_overdue_title, R.string.reminder_overdue_text) }

        assertEquals(1, manager.activeNotifications.count { it.id == 7 })
    }
}
