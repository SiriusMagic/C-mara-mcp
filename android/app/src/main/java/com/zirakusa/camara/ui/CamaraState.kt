package com.zirakusa.camara.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.camara.data.Balance
import com.zirakusa.camara.data.ColorStyle
import com.zirakusa.camara.data.Flash
import com.zirakusa.camara.data.KeyValues
import com.zirakusa.camara.data.Lens
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.Quality
import com.zirakusa.camara.data.Portrait
import com.zirakusa.camara.data.Ratio
import com.zirakusa.camara.data.Scene
import com.zirakusa.camara.data.Settings
import com.zirakusa.camara.data.Shot
import com.zirakusa.camara.data.Shots
import com.zirakusa.camara.data.Timer
import com.zirakusa.camara.ui.theme.Accent
import com.zirakusa.camara.ui.theme.ThemeMode
import kotlinx.coroutines.delay

/** The camera, or a page over it. */
enum class Page { CAMERA, SETTINGS, MCP }

/** What is open over the camera: the quick settings (˅), the flash choices, the «Más» modes, or nothing. */
enum class Panel { NONE, QUICK, FLASH, MORE }

/** The value Pro is changing. */
enum class Dial { EV, ISO, SHUTTER, BALANCE }

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
    var scene by mutableStateOf(settings.scene); private set
    /** Photo or video, from the mode. */
    val mode get() = scene.kind
    var panel by mutableStateOf(Panel.NONE)
    var dial by mutableStateOf(Dial.EV)
    /** The modes and HDR this phone does with its maker's processing (the camera fills it). */
    var extensions by mutableStateOf(emptySet<Scene>())
    var hdrAvailable by mutableStateOf(false)
    /** What one step of exposure is worth (⅓ EV on most phones) and how far it goes. */
    var evStep by mutableFloatStateOf(1f / 3)
    var evMax by mutableIntStateOf(6)
    var front by mutableStateOf(false); private set
    var zoom by mutableFloatStateOf(1f)
    var zoomMin by mutableFloatStateOf(1f)
    var zoomMax by mutableFloatStateOf(1f)
    val steps get() = Lens.steps(zoomMin, zoomMax)
    /** Retrato's 24 / 35 / 50 mm buttons. */
    val focals get() = Portrait.steps(zoomMax)
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
    var hdr by mutableStateOf(settings.hdr); private set
    var color by mutableStateOf(settings.color); private set
    var watermark by mutableStateOf(settings.watermark); private set
    var stabilization by mutableStateOf(settings.stabilization); private set
    var iso by mutableStateOf(settings.iso); private set
    var shutter by mutableStateOf(settings.shutter); private set
    var ev by mutableIntStateOf(settings.ev); private set
    var balance by mutableStateOf(settings.balance); private set
    var themeMode by mutableStateOf(settings.theme?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.BLACK); private set
    var accent by mutableStateOf(settings.accent?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.AMARILLO); private set

    /** Reads the settings again (after MCP changed them). */
    fun reload() {
        val s = Settings(kv)
        flash = s.flash; timer = s.timer; ratio = s.ratio; quality = s.quality; grid = s.grid; sound = s.sound
        hdr = s.hdr; color = s.color; watermark = s.watermark; stabilization = s.stabilization
        if (!recording && countdown == null && s.scene != scene) { scene = s.scene; zoom = 1f }
        last = shots.list().firstOrNull()
    }

    /** The big button: photo (after the timer), start or stop the video; during the countdown it cancels. */
    fun press() = press(timer.seconds)

    private fun press(seconds: Int) {
        when {
            recording -> stop()
            countdown != null -> countdown = null
            seconds == 0 -> shoot(mode)
            else -> launch {
                for (left in seconds downTo 1) { countdown = left; delay(1000); if (countdown == null) return@launch }
                countdown = null; shoot(mode)
            }
        }
    }

    /**
     * «Oye Lucía, toma una foto» after the user said «sí»: a photo with a visible countdown of [VOICE_SECONDS] that a tap
     * on the button cancels. Never through MCP (no AI tool takes photos); never while recording or counting already.
     */
    fun voiceCapture(selfie: Boolean) {
        if (recording || countdown != null || !cameraAllowed) return
        if (mode != Mode.PHOTO) changeScene(Scene.PHOTO)
        if (selfie != front) flip()
        press(VOICE_SECONDS)
    }

    companion object { const val VOICE_SECONDS = 3 }

    fun changeMode(m: Mode) = changeScene(if (m == Mode.VIDEO) Scene.VIDEO else Scene.PHOTO)
    /** Another mode (not while recording or counting down). Retrato starts at 24 mm. */
    fun changeScene(s: Scene) {
        if (recording || countdown != null) return
        if (s != scene) { scene = s; settings.scene = s; zoom = 1f }
        panel = Panel.NONE
    }
    fun flip() { if (!recording) { front = !front; zoom = 1f } }
    /** The panel opens, or closes if it was open. */
    fun toggle(p: Panel) { panel = if (panel == p) Panel.NONE else p }
    fun zoomTo(r: Float) { zoom = r.coerceIn(zoomMin, zoomMax) }

    fun started() { recording = true; recorded = 0 }
    /** A photo or video was saved: it is the last one, and is remembered for MCP and Inicio. */
    fun saved(s: Shot) { recording = false; last = shots.add(s) }
    fun failed() { recording = false }
    fun forget(alive: (Shot) -> Boolean) { shots.keep(alive); last = shots.list().firstOrNull() }

    fun changeFlash(f: Flash) { flash = f; settings.flash = f }
    /** In video only off and the torch make sense. */
    fun nextFlash() = changeFlash(if (mode == Mode.VIDEO) (if (flash == Flash.TORCH) Flash.OFF else Flash.TORCH) else Flash.entries[(flash.ordinal + 1) % Flash.entries.size])
    /** The flash as the camera should use it now. */
    val flashNow get() = if (mode == Mode.VIDEO && flash != Flash.TORCH) Flash.OFF else flash
    fun changeTimer(t: Timer) { timer = t; settings.timer = t }
    fun nextTimer() = changeTimer(Timer.entries[(timer.ordinal + 1) % Timer.entries.size])
    fun changeRatio(r: Ratio) { ratio = r; settings.ratio = r }
    fun changeQuality(q: Quality) { quality = q; settings.quality = q }
    fun changeGrid(on: Boolean) { grid = on; settings.grid = on }
    fun changeSound(on: Boolean) { sound = on; settings.sound = on }
    fun changeVolumeKeys(on: Boolean) { volumeKeys = on; settings.volumeKeys = on }
    fun changeAudio(on: Boolean) { audio = on; settings.audio = on }
    fun changeHdr(on: Boolean) { hdr = on; settings.hdr = on }
    fun changeColor(c: ColorStyle) { color = c; settings.color = c }
    fun nextColor() = changeColor(ColorStyle.entries[(color.ordinal + 1) % ColorStyle.entries.size])
    fun changeWatermark(on: Boolean) { watermark = on; settings.watermark = on }
    fun changeStabilization(on: Boolean) { stabilization = on; settings.stabilization = on }
    fun nextRatio() = changeRatio(Ratio.entries[(ratio.ordinal + 1) % Ratio.entries.size])
    fun nextQuality() = changeQuality(Quality.entries[(quality.ordinal + 1) % Quality.entries.size])
    fun changeIso(v: Int?) { iso = v; settings.iso = v }
    fun changeShutter(v: Long?) { shutter = v; settings.shutter = v }
    fun changeEv(v: Int) { ev = v.coerceIn(-evMax, evMax); settings.ev = ev }
    fun changeBalance(b: Balance) { balance = b; settings.balance = b }
    /** Pro back to automatic. */
    fun resetPro() { changeIso(null); changeShutter(null); changeEv(0); changeBalance(Balance.AUTO) }
    /** The photo is changed after it is taken: colour style, watermark, square, or a document. */
    val processed get() = mode == Mode.PHOTO && (color != ColorStyle.NATURAL || watermark || ratio == Ratio.R1_1 || scene == Scene.DOCUMENT)
    fun changeTheme(m: ThemeMode) { themeMode = m; settings.theme = m.name }
    fun changeAccent(a: Accent) { accent = a; settings.accent = a.name }
}
