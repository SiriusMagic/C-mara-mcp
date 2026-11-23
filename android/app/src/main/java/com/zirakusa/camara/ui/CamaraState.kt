package com.zirakusa.camara.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.camara.data.Flash
import com.zirakusa.camara.data.KeyValues
import com.zirakusa.camara.data.Lens
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.Quality
import com.zirakusa.camara.data.Ratio
import com.zirakusa.camara.data.Settings
import com.zirakusa.camara.data.Shot
import com.zirakusa.camara.data.Shots
import com.zirakusa.camara.data.Timer
import com.zirakusa.camara.ui.theme.Accent
import com.zirakusa.camara.ui.theme.ThemeMode
import kotlinx.coroutines.delay

/** The camera, or a page over it. */
enum class Page { CAMERA, SETTINGS, MCP }

/**
 * The only owner of Cámara's state: photo or video, flash, timer, ratio, zoom, the countdown, the recording and the last
 * shot, and the settings. The activity does the real camera work through [shoot] and [stop]; [launch] runs the countdown.
 */
class CamaraState(private val kv: KeyValues, private val launch: (suspend () -> Unit) -> Unit = {}) {
    private val settings = Settings(kv)
    private val shots = Shots(kv)
    /** Takes the photo or starts the video (the activity sets it). */
    var shoot: (Mode) -> Unit = {}
    var stop: () -> Unit = {}

    var page by mutableStateOf(Page.CAMERA)
    var mode by mutableStateOf(Mode.PHOTO); private set
    var front by mutableStateOf(false); private set
    var zoom by mutableFloatStateOf(1f)
    var zoomMin by mutableFloatStateOf(1f)
    var zoomMax by mutableFloatStateOf(1f)
    val steps get() = Lens.steps(zoomMin, zoomMax)
    /** Seconds left on the self-timer, or null. */
    var countdown by mutableStateOf<Int?>(null); private set
    var recording by mutableStateOf(false); private set
    var recorded by mutableLongStateOf(0L)
    var last by mutableStateOf(shots.list().firstOrNull()); private set
    var cameraAllowed by mutableStateOf(false)
    var micAllowed by mutableStateOf(false)
    /** A white blink when a photo is taken. */
    var flashBlink by mutableStateOf(false)

    var flash by mutableStateOf(settings.flash); private set
    var timer by mutableStateOf(settings.timer); private set
    var ratio by mutableStateOf(settings.ratio); private set
    var quality by mutableStateOf(settings.quality); private set
    var grid by mutableStateOf(settings.grid); private set
    var sound by mutableStateOf(settings.sound); private set
    var volumeKeys by mutableStateOf(settings.volumeKeys); private set
    var audio by mutableStateOf(settings.audio); private set
    var themeMode by mutableStateOf(settings.theme?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.BLACK); private set
    var accent by mutableStateOf(settings.accent?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.AMARILLO); private set

    /** Reads the settings again (after MCP changed them). */
    fun reload() {
        val s = Settings(kv)
        flash = s.flash; timer = s.timer; ratio = s.ratio; quality = s.quality; grid = s.grid; sound = s.sound
        last = shots.list().firstOrNull()
    }

    /** The big button: photo (after the timer), start or stop the video; during the countdown it cancels. */
    fun press() {
        when {
            recording -> stop()
            countdown != null -> countdown = null
            timer == Timer.OFF -> shoot(mode)
            else -> launch {
                for (left in timer.seconds downTo 1) { countdown = left; delay(1000); if (countdown == null) return@launch }
                countdown = null; shoot(mode)
            }
        }
    }

    fun changeMode(m: Mode) { if (!recording && countdown == null) mode = m }
    fun flip() { if (!recording) { front = !front; zoom = 1f } }
    fun zoomTo(r: Float) { zoom = r.coerceIn(zoomMin, zoomMax) }

    fun started() { recording = true; recorded = 0 }
    /** A photo or video was saved: it is the last one, and is remembered for MCP and Inicio. */
    fun saved(s: Shot) { recording = false; last = shots.add(s) }
    fun failed() { recording = false }
    fun forget(alive: (Shot) -> Boolean) { shots.keep(alive); last = shots.list().firstOrNull() }

    fun changeFlash(f: Flash) { flash = f; settings.flash = f }
    fun nextFlash() = changeFlash(Flash.entries[(flash.ordinal + 1) % Flash.entries.size])
    fun changeTimer(t: Timer) { timer = t; settings.timer = t }
    fun nextTimer() = changeTimer(Timer.entries[(timer.ordinal + 1) % Timer.entries.size])
    fun changeRatio(r: Ratio) { ratio = r; settings.ratio = r }
    fun changeQuality(q: Quality) { quality = q; settings.quality = q }
    fun changeGrid(on: Boolean) { grid = on; settings.grid = on }
    fun changeSound(on: Boolean) { sound = on; settings.sound = on }
    fun changeVolumeKeys(on: Boolean) { volumeKeys = on; settings.volumeKeys = on }
    fun changeAudio(on: Boolean) { audio = on; settings.audio = on }
    fun changeTheme(m: ThemeMode) { themeMode = m; settings.theme = m.name }
    fun changeAccent(a: Accent) { accent = a; settings.accent = a.name }
}
