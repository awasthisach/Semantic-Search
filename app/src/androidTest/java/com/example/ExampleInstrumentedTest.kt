package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testBottomNavigationSwitchesScreens() {
        // Initially on Home (Dashboard) screen
        composeTestRule.onNodeWithText("Dashboard").assertIsDisplayed()

        // Click on Files tab
        composeTestRule.onNodeWithText("Files").performClick()
        
        // Wait for idle to let recomposition happen
        composeTestRule.waitForIdle()

        // Click on Duplicates tab
        composeTestRule.onNodeWithText("Duplicates").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Duplicates").assertIsDisplayed()
        
        // Click on Vault tab
        composeTestRule.onNodeWithText("Vault").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Secure Vault").assertIsDisplayed()
    }
}
