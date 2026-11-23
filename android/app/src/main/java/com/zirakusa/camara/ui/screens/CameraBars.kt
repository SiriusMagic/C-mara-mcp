package com.zirakusa.camara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.HdrOff
import androidx.compose.material.icons.outlined.HdrOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material.icons.outlined.VideoStable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.camara.R
import com.zirakusa.camara.data.Flash
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.Scene
import com.zirakusa.camara.data.Timer
import com.zirakusa.camara.data.short
import com.zirakusa.camara.ui.CamaraState
import com.zirakusa.camara.ui.Page
import com.zirakusa.camara.ui.Panel
import com.zirakusa.camara.ui.theme.CamaraColors

private fun flashIcon(f: Flash) = when (f) { Flash.OFF -> Icons.Outlined.FlashOff; Flash.AUTO -> Icons.Outlined.FlashAuto; Flash.ON -> Icons.Outlined.FlashOn; Flash.TORCH -> Icons.Outlined.FlashlightOn }

/** HDR is offered in Foto when the phone does it. */
private fun CamaraState.showHdr() = scene == Scene.PHOTO && hdrAvailable

/**
 * Photo: flash · HDR · ˅ · colour style · shape. Video: torch · ˅ · stabilizer · quality. While recording, the clock.
 * The flash opens its choices (Apagado, Activado, Auto, Linterna); ˅ opens the quick settings.
 */
@Composable fun TopBar(state: CamaraState, modifier: Modifier) = Row(modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    val video = state.mode == Mode.VIDEO
    Top(flashIcon(state.flashNow), stringResource(R.string.flash), state.flashNow != Flash.OFF) { if (video) state.nextFlash() else state.toggle(Panel.FLASH) }
    if (!video && state.showHdr()) Top(if (state.hdr) Icons.Outlined.HdrOn else Icons.Outlined.HdrOff, stringResource(R.string.hdr), state.hdr) { state.changeHdr(!state.hdr) }
    if (state.recording) Text("● " + com.zirakusa.camara.data.Lens.clock(state.recorded), Modifier.clip(CircleShape).background(Red).padding(horizontal = 14.dp, vertical = 6.dp), color = White, fontSize = 16.sp)
    else Top(if (state.panel == Panel.QUICK) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, stringResource(R.string.quick), state.panel == Panel.QUICK) { state.toggle(Panel.QUICK) }
    if (video) {
        Top(Icons.Outlined.VideoStable, stringResource(R.string.stabilization), state.stabilization) { state.changeStabilization(!state.stabilization) }
        TextButton(state.quality.short(), stringResource(R.string.quality), state::nextQuality)
    } else {
        Top(Icons.Outlined.Palette, stringResource(R.string.color_style), state.color != com.zirakusa.camara.data.ColorStyle.NATURAL, onClick = state::nextColor)
        TextButton(state.ratio.key, stringResource(R.string.ratio), state::nextRatio)
    }
}

@Composable private fun Top(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) =
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(26.dp), tint = if (on) CamaraColors.Accent else White)
    }

@Composable private fun TextButton(text: String, label: String, onClick: () -> Unit) =
    Text(text, Modifier.clip(CircleShape).clickable(onClickLabel = label, onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

/** A round button with its name under it, for the quick settings and the flash choices. */
@Composable private fun Chip(icon: ImageVector, label: String, value: String?, on: Boolean, onClick: () -> Unit) =
    Column(Modifier.width(80.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = label, onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(if (on) Color(0x33FFFFFF) else Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (on) CamaraColors.Accent else White)
        }
        Text(label, color = if (on) CamaraColors.Accent else White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 13.sp)
        if (value != null) Text(value, color = CamaraColors.Accent, fontSize = 11.sp, maxLines = 1)
    }

/** ˅: Ajustes, shape, HDR, colour style, timer, grid, watermark, flash (photo); stabilizer, quality and torch (video). */
@Composable fun QuickRow(state: CamaraState) = Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    val video = state.mode == Mode.VIDEO
    Chip(Icons.Outlined.Settings, stringResource(R.string.settings), null, false) { state.panel = Panel.NONE; state.page = Page.SETTINGS }
    if (video) {
        Chip(Icons.Outlined.VideoStable, stringResource(R.string.stabilization), null, state.stabilization) { state.changeStabilization(!state.stabilization) }
        Chip(Icons.Outlined.AspectRatio, stringResource(R.string.quality), state.quality.short(), false, state::nextQuality)
    } else {
        Chip(Icons.Outlined.AspectRatio, stringResource(R.string.ratio), state.ratio.key, false, state::nextRatio)
        if (state.showHdr()) Chip(Icons.Outlined.HdrOn, stringResource(R.string.hdr), null, state.hdr) { state.changeHdr(!state.hdr) }
        Chip(Icons.Outlined.Palette, stringResource(R.string.color_style), stringResource(colorName(state.color)), state.color != com.zirakusa.camara.data.ColorStyle.NATURAL, state::nextColor)
    }
    Chip(if (state.timer == Timer.OFF) Icons.Outlined.TimerOff else Icons.Outlined.Timer, stringResource(R.string.timer),
        if (state.timer == Timer.OFF) null else "${state.timer.seconds} s", state.timer != Timer.OFF, state::nextTimer)
    Chip(Icons.Outlined.GridOn, stringResource(R.string.grid), null, state.grid) { state.changeGrid(!state.grid) }
    if (!video) Chip(Icons.Outlined.BrandingWatermark, stringResource(R.string.watermark), null, state.watermark) { state.changeWatermark(!state.watermark) }
    Chip(flashIcon(state.flashNow), stringResource(if (video) R.string.flash_torch else R.string.flash), null, state.flashNow != Flash.OFF) {
        if (video) state.nextFlash() else state.panel = Panel.FLASH
    }
}

/** ← · Apagado · Activado · Auto · Linterna. */
@Composable fun FlashRow(state: CamaraState) = Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x22FFFFFF)).clickable(onClickLabel = stringResource(R.string.back)) { state.panel = Panel.NONE },
        contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = White) }
    listOf(Flash.OFF to R.string.flash_off, Flash.ON to R.string.flash_on, Flash.AUTO to R.string.flash_auto, Flash.TORCH to R.string.flash_torch).forEach { (f, name) ->
        Chip(flashIcon(f), stringResource(name), null, state.flash == f) { state.changeFlash(f); state.panel = Panel.NONE }
    }
}
