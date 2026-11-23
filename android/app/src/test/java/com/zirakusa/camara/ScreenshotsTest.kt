package com.zirakusa.camara

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.camara.data.MapValues
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.Shot
import com.zirakusa.camara.ui.AppActions
import com.zirakusa.camara.ui.CamaraApp
import com.zirakusa.camara.ui.CamaraState
import com.zirakusa.camara.ui.Page
import com.zirakusa.camara.ui.theme.CamaraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The camera (photo with grid, video recording), the permission card and Ajustes. A gradient stands in for the picture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private fun shot(name: String) { compose.waitForIdle(); onView(isRoot()).captureRoboImage("build/screenshots/camara-$name.png") }
    private val fake = AppActions(preview = { m -> Box(m.background(Brush.verticalGradient(listOf(Color(0xFF6A8CAF), Color(0xFF2D3E50))))) })

    @Test fun allScreens() {
        val state = CamaraState(MapValues())
        val taken = mutableListOf<Mode>()
        state.shoot = { taken += it }
        state.zoomMin = 0.6f; state.zoomMax = 10f; state.changeGrid(true)
        state.saved(Shot(0, "content://media/1", "IMG.jpg", Mode.PHOTO, 0))
        compose.setContent { CamaraTheme(state.themeMode, state.accent) { CamaraApp(state, fake) } }
        compose.onNodeWithText("Permitir").assertExists()
        shot("permission")
        state.cameraAllowed = true
        compose.onNodeWithText("2×").assertExists()
        shot("photo")
        compose.onNodeWithContentDescription("Hacer foto").performClick(); assertEquals(listOf(Mode.PHOTO), taken)
        compose.onNodeWithText("VIDEO").performClick()
        state.started(); state.recorded = 75
        compose.onNodeWithText("● 01:15").assertExists()
        shot("recording")
        state.saved(Shot(0, "content://media/2", "VID.mp4", Mode.VIDEO, 0, 75))
        state.page = Page.SETTINGS
        compose.onNodeWithText("Botones de volumen").assertExists()
        shot("settings")
    }
}
