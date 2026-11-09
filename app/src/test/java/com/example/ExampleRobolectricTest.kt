package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.BotViewModel
import com.example.ui.BotDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("Solana Arb Bot", appName)
  }

  @Test
  fun `test BotViewModel instantiation`() {
    try {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val app = context as Application
      println("Instantiating BotViewModel...")
      val model = BotViewModel(app)
      assertNotNull(model)
      println("BotViewModel instantiated successfully!")
    } catch (e: Throwable) {
      org.junit.Assert.fail(e.stackTraceToString())
    }
  }

  @Test
  fun `test BotDashboardScreen composition`() {
    try {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val app = context as Application
      val viewModel = BotViewModel(app)
      
      composeTestRule.setContent {
        MyApplicationTheme {
          BotDashboardScreen(viewModel = viewModel)
        }
      }
      
      // Wait for idle to verify layout did not crash
      composeTestRule.waitForIdle()
      println("BotDashboardScreen Composed and idle successfully without crashes!")
    } catch (e: Throwable) {
      org.junit.Assert.fail("Compose broke: " + e.stackTraceToString())
    }
  }
}

