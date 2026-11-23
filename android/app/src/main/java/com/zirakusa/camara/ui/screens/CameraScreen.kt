package com.zirakusa.camara.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.camara.R
import com.zirakusa.camara.data.Flash
import com.zirakusa.camara.data.Lens
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.Timer
import com.zirakusa.camara.ui.AppActions
import com.zirakusa.camara.ui.CamaraState
import com.zirakusa.camara.ui.Page
import com.zirakusa.camara.ui.theme.CamaraColors

private val White = Color.White
private val Glass = Color(0x55000000)
private val Red = Color(0xFFFF453A)

/**
 * The camera: flash, timer, ratio and Ajustes on top; the picture in its shape (4:3 or 16:9) with optional thirds; zoom
 * buttons; FOTO · VIDEO; the last shot, the big button and the lens switch at the bottom. Pinch zooms and a tap focuses.
 */
@Composable fun CameraScreen(state: CamaraState, actions: AppActions) = Box(Modifier.fillMaxSize().background(Color.Black)) {
    if (!state.cameraAllowed) return@Box Column(Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(28.dp)).background(CamaraColors.Card).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(48.dp), tint = CamaraColors.AccentText)
        Text(stringResource(R.string.allow_text), color = CamaraColors.Muted, textAlign = TextAlign.Center)
        MainButton(stringResource(R.string.allow), onClick = actions.allow)
    }
    val video = state.mode == Mode.VIDEO
    Box(Modifier.fillMaxWidth().align(Alignment.Center).aspectRatio(if (video) 9f / 16f else state.ratio.w.toFloat() / state.ratio.h)) {
        actions.preview(Modifier.fillMaxSize())
        if (state.grid) Thirds()
        if (state.flashBlink) Box(Modifier.fillMaxSize().background(Color(0xAAFFFFFF)))
        state.countdown?.let { Text("$it", Modifier.align(Alignment.Center), color = White, fontSize = 120.sp, fontWeight = FontWeight.Light) }
    }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (!video) Top(when (state.flash) { Flash.OFF -> Icons.Outlined.FlashOff; Flash.AUTO -> Icons.Outlined.FlashAuto; Flash.ON -> Icons.Outlined.FlashOn },
            stringResource(R.string.flash), state.flash != Flash.OFF, onClick = state::nextFlash) else Box(Modifier.size(48.dp))
        if (state.recording) Text("● " + Lens.clock(state.recorded), Modifier.clip(CircleShape).background(Red).padding(horizontal = 14.dp, vertical = 6.dp), color = White, fontSize = 16.sp)
        else Top(if (state.timer == Timer.OFF) Icons.Outlined.TimerOff else Icons.Outlined.Timer, stringResource(R.string.timer), state.timer != Timer.OFF,
            if (state.timer == Timer.OFF) null else "${state.timer.seconds}s", state::nextTimer)
        if (!video) Text(state.ratio.key, Modifier.clip(CircleShape).background(Glass).clickable(onClickLabel = stringResource(R.string.ratio)) {
            state.changeRatio(if (state.ratio.ordinal == 0) com.zirakusa.camara.data.Ratio.R16_9 else com.zirakusa.camara.data.Ratio.R4_3) }
            .padding(horizontal = 12.dp, vertical = 10.dp), color = White, fontSize = 14.sp) else Box(Modifier.size(48.dp))
        Top(Icons.Outlined.Settings, stringResource(R.string.settings), false) { state.page = Page.SETTINGS }
    }
    Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (state.steps.size > 1) Row(Modifier.clip(CircleShape).background(Glass).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            state.steps.forEach { r ->
                val on = kotlin.math.abs(state.zoom - r) < 0.05f
                Text(if (on) Lens.label(state.zoom) else Lens.label(r), Modifier.size(42.dp).clip(CircleShape).background(if (on) Color(0x33FFFFFF) else Color.Transparent)
                    .clickable { state.zoomTo(r) }.padding(top = 11.dp), color = if (on) CamaraColors.Accent else White, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        if (!state.recording) Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            ModeTab(stringResource(R.string.photo), !video) { state.changeMode(Mode.PHOTO) }
            ModeTab(stringResource(R.string.video), video) { state.changeMode(Mode.VIDEO) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Glass).border(2.dp, Color(0x66FFFFFF), RoundedCornerShape(14.dp))
                .clickable(enabled = state.last != null, onClickLabel = stringResource(R.string.last_shot), onClick = actions.openLast), contentAlignment = Alignment.Center) {
                if (state.last != null) Icon(Icons.Outlined.Image, stringResource(R.string.last_shot), tint = White)
            }
            Shutter(state)
            Box(Modifier.size(52.dp).clip(CircleShape).background(Glass).clickable(enabled = !state.recording, onClickLabel = stringResource(R.string.flip), onClick = state::flip),
                contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Cameraswitch, stringResource(R.string.flip), tint = White) }
        }
    }
}

/** The big round button: white for photos, red for video (a square while recording). */
@Composable private fun Shutter(state: CamaraState) {
    val video = state.mode == Mode.VIDEO
    val label = stringResource(when { state.recording -> R.string.stop; video -> R.string.record; else -> R.string.take })
    Box(Modifier.size(80.dp).clip(CircleShape).border(4.dp, White, CircleShape).clickable(onClickLabel = label, onClick = state::press)
        .semantics { contentDescription = label }.padding(8.dp),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (state.recording) 30.dp else 64.dp).clip(if (state.recording) RoundedCornerShape(6.dp) else CircleShape).background(if (video) Red else White))
    }
}

@Composable private fun Top(icon: ImageVector, label: String, on: Boolean, badge: String? = null, onClick: () -> Unit) =
    Row(Modifier.clip(CircleShape).background(Glass).clickable(onClickLabel = label, onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, Modifier.size(24.dp), tint = if (on) CamaraColors.Accent else White)
        if (badge != null) Text(badge, Modifier.padding(start = 4.dp), color = CamaraColors.Accent, fontSize = 13.sp)
    }

@Composable private fun ModeTab(text: String, on: Boolean, onClick: () -> Unit) =
    Text(text.uppercase(), Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        color = if (on) CamaraColors.Accent else White, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)

/** Lines of thirds, to place things well. */
@Composable private fun Thirds() = Canvas(Modifier.fillMaxSize()) {
    val c = Color(0x66FFFFFF); val w = 1.dp.toPx()
    for (i in 1..2) {
        drawLine(c, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), w)
        drawLine(c, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), w)
    }
}
