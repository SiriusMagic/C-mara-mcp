package com.zirakusa.camara.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.zirakusa.camara.data.Shot
import com.zirakusa.camara.ui.screens.CameraScreen
import com.zirakusa.camara.ui.screens.McpScreen
import com.zirakusa.camara.ui.screens.SettingsScreen

/** What the screens ask of the phone (the activity gives the real ones; tests leave the defaults). */
class AppActions(
    /** The live camera preview. Tests paint nothing. */
    val preview: @Composable (Modifier) -> Unit = {},
    val allow: () -> Unit = {},
    /** Opens the last photo or video in Luma (or the phone's gallery). */
    val openLast: () -> Unit = {},
    /** A small picture of [Shot] for the button next to the shutter (read on the phone, off the main thread); null if none. */
    val thumbnail: suspend (Shot) -> ImageBitmap? = { null },
    val copyLink: (String) -> Unit = {},
    val goToHub: () -> Unit = {},
)

@Composable fun CamaraApp(state: CamaraState, actions: AppActions = AppActions()) = when (state.page) {
    Page.CAMERA -> CameraScreen(state, actions)
    Page.SETTINGS -> SettingsScreen(state)
    Page.MCP -> McpScreen(actions.copyLink, actions.goToHub) { state.page = Page.SETTINGS }
}
