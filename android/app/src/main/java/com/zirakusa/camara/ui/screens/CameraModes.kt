package com.zirakusa.camara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.zirakusa.camara.data.Balance
import com.zirakusa.camara.data.ColorStyle
import com.zirakusa.camara.data.Pro
import com.zirakusa.camara.data.Scene
import com.zirakusa.camara.ui.CamaraState
import com.zirakusa.camara.ui.Dial
import com.zirakusa.camara.ui.Panel
import com.zirakusa.camara.ui.theme.CamaraColors

fun sceneName(s: Scene) = when (s) {
    Scene.VIDEO -> R.string.scene_video; Scene.PHOTO -> R.string.scene_photo; Scene.PORTRAIT -> R.string.scene_portrait
    Scene.NIGHT -> R.string.scene_night; Scene.PRO -> R.string.scene_pro; Scene.DOCUMENT -> R.string.scene_document
}

fun colorName(c: ColorStyle) = when (c) {
    ColorStyle.NATURAL -> R.string.color_natural; ColorStyle.VIVID -> R.string.color_vivid; ColorStyle.WARM -> R.string.color_warm
    ColorStyle.COOL -> R.string.color_cool; ColorStyle.MONO -> R.string.color_mono; ColorStyle.SEPIA -> R.string.color_sepia
}

private fun balanceName(b: Balance) = when (b) {
    Balance.AUTO -> R.string.wb_auto; Balance.DAYLIGHT -> R.string.wb_daylight; Balance.CLOUDY -> R.string.wb_cloudy
    Balance.INCANDESCENT -> R.string.wb_incandescent; Balance.FLUORESCENT -> R.string.wb_fluorescent
}

private fun sceneIcon(s: Scene): ImageVector = if (s == Scene.PRO) Icons.Outlined.Tune else Icons.Outlined.DocumentScanner

/** VIDEO · FOTO · RETRATO · NOCHE (and the «Más» mode in use), then ≡ for «Más». */
@Composable fun ModeStrip(state: CamaraState) = Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    val scroll = rememberScrollState()
    // A mode from «Más» sits at the end of the strip: show it.
    LaunchedEffect(state.scene, scroll.maxValue) { scroll.animateScrollTo(if (state.scene in Scene.more) scroll.maxValue else 0) }
    Row(Modifier.weight(1f).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (Scene.strip + Scene.more.filter { it == state.scene }).forEach { s ->
            val on = s == state.scene
            Text(stringResource(sceneName(s)).uppercase(), Modifier.clip(CircleShape).clickable { state.changeScene(s) }.padding(horizontal = 12.dp, vertical = 8.dp),
                color = if (on) CamaraColors.Accent else White, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.more_hint)) { state.toggle(Panel.MORE) }, contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Menu, stringResource(R.string.more_hint), tint = if (state.scene in Scene.more) CamaraColors.Accent else White)
    }
}

/** «Más»: the other modes, big, over a dark veil. A tap outside closes it. */
@Composable fun MoreSheet(state: CamaraState) = Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { state.panel = Panel.NONE }) {
    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Scene.more.forEach { s ->
                val on = s == state.scene
                Column(Modifier.width(140.dp).clip(RoundedCornerShape(16.dp)).clickable { state.changeScene(s) }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(sceneIcon(s), null, Modifier.size(40.dp), tint = if (on) CamaraColors.Accent else White)
                    Text(stringResource(sceneName(s)).uppercase(), color = if (on) CamaraColors.Accent else White, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Pro: EV · ISO · Obturador · Balance; the chosen one shows its values in a row, «Automático» puts all back. */
@Composable fun ProPanel(state: CamaraState) = Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val values: List<Pair<String, Boolean>>; val pick: (Int) -> Unit
    when (state.dial) {
        Dial.EV -> { val evs = (-state.evMax..state.evMax).toList(); values = evs.map { Pro.evLabel(it, state.evStep) to (it == state.ev) }; pick = { state.changeEv(evs[it]) } }
        Dial.ISO -> { values = Pro.isos.map { Pro.isoLabel(it) to (it == state.iso) }; pick = { state.changeIso(Pro.isos[it]) } }
        Dial.SHUTTER -> { values = Pro.shutters.map { Pro.shutterLabel(it) to (it == state.shutter) }; pick = { state.changeShutter(Pro.shutters[it]) } }
        Dial.BALANCE -> { values = Balance.entries.map { stringResource(balanceName(it)) to (it == state.balance) }; pick = { state.changeBalance(Balance.entries[it]) } }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEachIndexed { i, (text, on) ->
            Text(text, Modifier.clip(CircleShape).background(if (on) Color(0x33FFFFFF) else Glass).clickable { pick(i) }.padding(horizontal = 12.dp, vertical = 7.dp),
                color = if (on) CamaraColors.Accent else White, fontSize = 13.sp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Dial.EV to (stringResource(R.string.pro_ev) to Pro.evLabel(state.ev, state.evStep)), Dial.ISO to (stringResource(R.string.pro_iso) to Pro.isoLabel(state.iso)),
            Dial.SHUTTER to (stringResource(R.string.pro_shutter) to Pro.shutterLabel(state.shutter)),
            Dial.BALANCE to (stringResource(R.string.pro_wb) to stringResource(balanceName(state.balance)))).forEach { (d, label) ->
            val on = d == state.dial
            Column(Modifier.clip(CircleShape).clickable { state.dial = d }.padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label.first, color = if (on) CamaraColors.Accent else White, fontSize = 12.sp)
                Text(label.second, color = if (on) CamaraColors.Accent else White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(stringResource(R.string.pro_reset), Modifier.clip(CircleShape).background(Glass).clickable(onClick = state::resetPro).padding(horizontal = 10.dp, vertical = 6.dp),
            color = White, fontSize = 12.sp)
    }
}
