package org.noblecow.hrservice

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SmokeTest {

    @get:Rule
    internal val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
            InstrumentationRegistry.getInstrumentation().uiAutomation.apply {
                grantRuntimePermission(packageName, Manifest.permission.BLUETOOTH_CONNECT)
                grantRuntimePermission(packageName, Manifest.permission.BLUETOOTH_ADVERTISE)
                grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Test
    fun basicTest() {
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.app_name))
            .assertExists()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.start))
            .assertHasClickAction()
    }

    @Test
    fun startStopTest() {
        val startText = composeTestRule.activity.getString(R.string.start)
        val stopText = composeTestRule.activity.getString(R.string.stop)

        composeTestRule
            .onNodeWithText(startText)
            .performClick()
        composeTestRule
            .waitUntilNodeCount(matcher = hasText(stopText), count = 1, timeoutMillis = 10000)
        composeTestRule
            .onNodeWithText(stopText)
            .performClick()
        composeTestRule
            .waitUntilNodeCount(matcher = hasText(startText), count = 1, timeoutMillis = 10000)
    }
}
