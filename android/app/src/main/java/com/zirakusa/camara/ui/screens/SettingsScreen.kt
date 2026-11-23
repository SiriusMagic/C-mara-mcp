package com.zirakusa.camara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zirakusa.camara.BuildConfig
import com.zirakusa.camara.data.Quality
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.sp
import com.zirakusa.camara.R
import com.zirakusa.camara.mcp.McpMode
import com.zirakusa.camara.mcp.RelayLink
import com.zirakusa.camara.ui.Page
import com.zirakusa.camara.ui.CamaraState
import com.zirakusa.camara.ui.theme.CamaraColors

/** «Ajustes»: grid, sound, volume buttons, video quality and sound, Modo MCP, Temas, privacy and version. */
@Composable fun SettingsScreen(state: CamaraState) {
    var privacy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(CamaraColors.Background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTop(stringResource(R.string.settings)) { state.page = Page.CAMERA }
        Caption(stringResource(R.string.photo))
        Group {
            SwitchItem(stringResource(R.string.grid), stringResource(R.string.grid_hint), state.grid, onChange = state::changeGrid)
            Divider()
            SwitchItem(stringResource(R.string.sound), null, state.sound, onChange = state::changeSound)
            Divider()
            SwitchItem(stringResource(R.string.volume_keys), stringResource(R.string.volume_keys_hint), state.volumeKeys, onChange = state::changeVolumeKeys)
        }
        Caption(stringResource(R.string.video))
        Group {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.quality), fontSize = 18.sp)
                Segmented(Quality.entries.map { it.label }, state.quality.ordinal, Modifier.fillMaxWidth()) { state.changeQuality(Quality.entries[it]) }
            }
            Divider()
            SwitchItem(stringResource(R.string.audio), stringResource(R.string.audio_hint), state.audio, onChange = state::changeAudio)
        }
        Group {
            val status = when {
                !McpMode.active -> stringResource(R.string.mcp_status_off)
                McpMode.hubRedirect -> stringResource(R.string.mcp_redirect)
                McpMode.state == RelayLink.State.ONLINE -> stringResource(R.string.mcp_online)
                McpMode.state == RelayLink.State.CONNECTING -> stringResource(R.string.mcp_connecting)
                else -> stringResource(R.string.mcp_offline)
            }
            Item(stringResource(R.string.mcp_title), "$status · ${stringResource(R.string.mcp_settings_hint)}") { state.page = Page.MCP }
        }
        ThemeCard(state.themeMode, state.accent, state::changeTheme, state::changeAccent)
        Caption(stringResource(R.string.about))
        Group {
            Item(stringResource(R.string.privacy), null) { privacy = true }
            Divider()
            Item(stringResource(R.string.version_title), BuildConfig.VERSION_NAME, onClick = null)
        }
        Text("", Modifier.padding(bottom = 16.dp))
    }
    if (privacy) AlertDialog({ privacy = false }, confirmButton = { TextButton({ privacy = false }) { Text(stringResource(R.string.done), color = CamaraColors.AccentText) } },
        title = { Text(stringResource(R.string.privacy)) }, text = { Text(stringResource(R.string.privacy_text)) })
}
