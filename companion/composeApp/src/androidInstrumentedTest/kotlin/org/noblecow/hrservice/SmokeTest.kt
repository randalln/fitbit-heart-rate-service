package org.noblecow.hrservice

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import heartratemonitor.composeapp.generated.resources.Res
import heartratemonitor.composeapp.generated.resources.app_name
import heartratemonitor.composeapp.generated.resources.start
import heartratemonitor.composeapp.generated.resources.stop
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@OptIn(ExperimentalTestApi::class)
class SmokeTest {

    private val permissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(permissionRule)
        .around(composeRule)

    internal val composeTestRule get() = composeRule

    @Test
    fun basicTest() {
        val appName = runBlocking { getString(Res.string.app_name) }
        val start = runBlocking { getString(Res.string.start) }

        composeTestRule
            .onNodeWithText(appName)
            .assertExists()
        composeTestRule
            .onNodeWithText(start)
            .assertHasClickAction()
    }

    @Test
    fun startStopTest() {
        val startText = runBlocking { getString(Res.string.start) }
        val stopText = runBlocking { getString(Res.string.stop) }

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
