package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.ContainerRepository
import com.example.ui.AppNavigation
import com.example.ui.MainViewModel
import com.example.ui.UserRole
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DEPO", appName)
    }

    @Test
    fun `all three user profiles launch and switch without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(context)
        }
        val viewModel = MainViewModel(ContainerRepository(), context.filesDir, context)

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, currentDepo = "Depo 1") {
                AppNavigation(
                    viewModel = viewModel,
                    onNavigateToCamera = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // 1. Test ADMIN profile startup
        composeTestRule.onNodeWithTag("role_admin_card_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(UserRole.ADMIN, viewModel.userRole.value)
        assertTrue(viewModel.roleConfirmedOnStartup.value)

        // Return to role selection
        composeTestRule.onNodeWithTag("switch_role_top_button").performClick()
        composeTestRule.waitForIdle()

        // 2. Test DEPO profile startup
        composeTestRule.onNodeWithTag("role_depo_card_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(UserRole.DEPO, viewModel.userRole.value)
        assertTrue(viewModel.roleConfirmedOnStartup.value)

        // Navigate to NASTAVENÍ in DEPO profile
        composeTestRule.onNodeWithText("NASTAVENÍ").performClick()
        composeTestRule.waitForIdle()

        // Return to role selection
        composeTestRule.onNodeWithTag("switch_role_top_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Test PŘEPRAVCE profile startup
        composeTestRule.onNodeWithTag("role_prepravce_card_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(UserRole.PREPRAVCE, viewModel.userRole.value)
        assertTrue(viewModel.roleConfirmedOnStartup.value)
    }
}
