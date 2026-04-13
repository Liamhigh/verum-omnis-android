package com.verum.omnis

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verum.omnis.core.BoundedRenderSettings
import com.verum.omnis.ui.MainScreenBridge
import com.verum.omnis.ui.MainScreenUiState
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoundedRenderSettingsInstrumentationTest {

    @Test
    fun defaultLaunch_showsLegacyState_andConservativeGovernanceDefaults() {
        val appContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        BoundedRenderSettings().persist(appContext)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val state = readBridgeState(activity)
                assertFalse(state.boundedHumanBriefEnabled)
                assertFalse(state.boundedPoliceSummaryEnabled)
                assertFalse(state.boundedLegalStandingEnabled)
                assertEquals("LEGACY", state.boundedStatusLabel)
            }
        }
    }

    @Test
    fun enableFlow_requiresConfirmation_thenPersistsBoundedHumanBriefOnly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                BoundedRenderSettings().persist(activity.applicationContext)
                activity.onLegalAdvisoryChanged(true)
                activity.onBoundedHumanBriefChanged(true)
                val dialog = readBoundedDialog(activity)
                assertTrue(dialog?.isShowing == true)
                dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }

            scenario.onActivity { activity ->
                val persisted = BoundedRenderSettings.load(activity.applicationContext)
                val state = readBridgeState(activity)
                assertTrue(persisted.boundedHumanBriefEnabled)
                assertFalse(persisted.boundedPoliceSummaryEnabled)
                assertFalse(persisted.boundedLegalStandingEnabled)
                assertEquals("BOUNDED + AUDITED", state.boundedStatusLabel)
                assertTrue(state.boundedHumanBriefEnabled)
            }
        }
    }

    @Test
    fun cancelFlow_leavesBoundedHumanBriefOff_andStatusLegacy() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                BoundedRenderSettings().persist(activity.applicationContext)
                activity.onLegalAdvisoryChanged(true)
                activity.onBoundedHumanBriefChanged(true)
                val dialog = readBoundedDialog(activity)
                assertTrue(dialog?.isShowing == true)
                dialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }

            scenario.onActivity { activity ->
                val persisted = BoundedRenderSettings.load(activity.applicationContext)
                val state = readBridgeState(activity)
                assertFalse(persisted.boundedHumanBriefEnabled)
                assertEquals("LEGACY", state.boundedStatusLabel)
                assertFalse(state.boundedHumanBriefEnabled)
            }
        }
    }

    @Test
    fun restartPersistence_keepsBoundedHumanBriefEnabled_afterRecreate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                BoundedRenderSettings(
                    boundedHumanBriefEnabled = true,
                    boundedPoliceSummaryEnabled = false,
                    boundedLegalStandingEnabled = false,
                    boundedRenderAuditRequired = true,
                    boundedRenderFailClosed = true
                ).persist(activity.applicationContext)
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use { relaunched ->
            relaunched.onActivity { activity ->
                val persisted = BoundedRenderSettings.load(activity.applicationContext)
                val state = readBridgeState(activity)
                assertTrue(persisted.boundedHumanBriefEnabled)
                assertTrue(state.boundedHumanBriefEnabled)
                assertEquals("BOUNDED + AUDITED", state.boundedStatusLabel)
            }
        }
    }

    @Test
    fun laneIsolation_enablingHumanBriefDoesNotActivateOtherLanes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                BoundedRenderSettings().persist(activity.applicationContext)
                activity.onLegalAdvisoryChanged(true)
                activity.onBoundedHumanBriefChanged(true)
                readBoundedDialog(activity)!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }

            scenario.onActivity { activity ->
                val persisted = BoundedRenderSettings.load(activity.applicationContext)
                val state = readBridgeState(activity)
                assertTrue(persisted.boundedHumanBriefEnabled)
                assertFalse(persisted.boundedPoliceSummaryEnabled)
                assertFalse(persisted.boundedLegalStandingEnabled)
                assertFalse(state.boundedPoliceSummaryEnabled)
                assertFalse(state.boundedLegalStandingEnabled)
            }
        }
    }

    private fun readBridgeState(activity: MainActivity): MainScreenUiState {
        val field = activity.javaClass.getDeclaredField("mainScreenBridge")
        field.isAccessible = true
        val bridge = field.get(activity) as MainScreenBridge
        return bridge.state
    }

    private fun invokePrivateMethod(target: Any, methodName: String) {
        val method = target.javaClass.getDeclaredMethod(methodName)
        method.isAccessible = true
        method.invoke(target)
    }

    private fun readBoundedDialog(activity: MainActivity): AlertDialog? {
        val field = activity.javaClass.getDeclaredField("boundedHumanBriefDialog")
        field.isAccessible = true
        return field.get(activity) as? AlertDialog
    }
}
