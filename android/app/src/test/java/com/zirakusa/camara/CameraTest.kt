package com.zirakusa.camara

import com.zirakusa.camara.data.Balance
import com.zirakusa.camara.data.ColorStyle
import com.zirakusa.camara.data.Dual
import com.zirakusa.camara.data.Flash
import com.zirakusa.camara.data.Pixels
import com.zirakusa.camara.data.Portrait
import com.zirakusa.camara.data.Pro
import com.zirakusa.camara.data.Ratio
import com.zirakusa.camara.data.Scene
import com.zirakusa.camara.data.Lens
import com.zirakusa.camara.data.MapValues
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.Settings
import com.zirakusa.camara.data.Shot
import com.zirakusa.camara.data.Shots
import com.zirakusa.camara.data.Timer
import com.zirakusa.camara.ui.CamaraState
import com.zirakusa.camara.ui.Panel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The pure rules (zoom, names, clock, shots, settings) and the state's big button, timer and recording. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraTest {
    @Test fun lensRules() {
        assertEquals(listOf(0.5f, 1f, 2f, 5f), Lens.steps(0.5f, 10f)); assertEquals(listOf(1f, 2f), Lens.steps(1f, 4f)); assertEquals(listOf(1f), Lens.steps(1f, 1f))
        assertEquals("1×", Lens.label(1f)); assertEquals("0.5×", Lens.label(0.5f)); assertEquals("2.4×", Lens.label(2.4f))
        assertEquals(8f, Lens.pinch(4f, 3f, 1f, 8f)); assertEquals(1f, Lens.pinch(1.2f, 0.5f, 1f, 8f))
        val at = LocalDateTime.of(2026, 11, 21, 9, 30, 15)
        assertEquals("IMG_20261121_093015.jpg", Lens.fileName(Mode.PHOTO, at)); assertEquals("VID_20261121_093015.mp4", Lens.fileName(Mode.VIDEO, at))
        assertEquals("00:07", Lens.clock(7)); assertEquals("02:05", Lens.clock(125)); assertEquals("1:02:03", Lens.clock(3723))
    }

    @Test fun shotsAndSettings() {
        val kv = MapValues()
        val shots = Shots(kv)
        repeat(Shots.MAX + 2) { shots.add(Shot(0, "content://media/$it", "IMG_$it.jpg", Mode.PHOTO, it.toLong())) }
        assertEquals(Shots.MAX, shots.list().size); assertEquals("IMG_${Shots.MAX + 1}.jpg", shots.list().first().name)
        shots.keep { !it.uri.endsWith("/101") }; assertEquals(Shots.MAX - 1, shots.list().size)
        val s = Settings(kv)
        assertEquals(Flash.OFF, s.flash); assertEquals(Timer.OFF, s.timer); assertTrue(s.volumeKeys); assertFalse(s.grid)
        kv.put("flash", "LASER"); assertEquals(Flash.OFF, s.flash)
        val today = LocalDate.of(2026, 11, 21)
        val noon = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val list = listOf(Shot(1, "u", "a", Mode.PHOTO, noon), Shot(2, "u", "b", Mode.PHOTO, noon), Shot(3, "u", "c", Mode.VIDEO, noon), Shot(4, "u", "d", Mode.PHOTO, noon - 86_400_000))
        assertEquals(listOf("2/1"), SummaryLines.lines(list, today, ZoneOffset.UTC) { p, v -> "$p/$v" })
        assertTrue(SummaryLines.lines(list.drop(3), today, ZoneOffset.UTC) { _, _ -> "x" }.isEmpty())
    }

    @Test fun bigButtonTimerAndRecording() = runBlocking {
        val kv = MapValues()
        val state = CamaraState(kv, launch = { runBlocking { it() } })
        val shot = mutableListOf<Mode>()
        state.shoot = { shot += it }
        var stopped = 0
        state.stop = { stopped++ }
        state.press(); assertEquals(listOf(Mode.PHOTO), shot)
        state.changeTimer(Timer.S3)
        val t0 = System.currentTimeMillis(); state.press()
        assertTrue(System.currentTimeMillis() - t0 >= 2900); assertEquals(2, shot.size); assertNull(state.countdown)
        state.changeTimer(Timer.OFF); state.changeMode(Mode.VIDEO); state.press()
        assertEquals(Mode.VIDEO, shot.last()); state.started(); assertTrue(state.recording)
        state.changeMode(Mode.PHOTO); assertEquals(Mode.VIDEO, state.mode)                  // no switching while recording
        state.flip(); assertFalse(state.front)
        state.press(); assertEquals(1, stopped)
        state.saved(Shot(0, "content://v/1", "VID_1.mp4", Mode.VIDEO, 5, 12)); assertFalse(state.recording); assertEquals(1L, state.last!!.id)
        state.changeMode(Mode.PHOTO); state.nextFlash(); assertEquals(Flash.AUTO, state.flash); assertEquals(Flash.AUTO, CamaraState(kv).flash)
        state.zoomMin = 0.5f; state.zoomMax = 8f; state.zoomTo(20f); assertEquals(8f, state.zoom); assertEquals(listOf(0.5f, 1f, 2f, 5f), state.steps)
    }

    @Test fun colourDocumentAndWatermark() {
        val grey = 0xFF808080.toInt(); val red = 0xFFC83214.toInt()
        assertEquals(red, Pixels.apply(ColorStyle.NATURAL.matrix, red))
        val mono = Pixels.apply(ColorStyle.MONO.matrix, red)
        assertEquals(mono shr 16 and 255, mono and 255)                                           // no colour left
        assertEquals(grey, Pixels.apply(ColorStyle.VIVID.matrix, grey))                           // grey stays grey
        assertTrue((Pixels.apply(ColorStyle.WARM.matrix, grey) shr 16 and 255) > (Pixels.apply(ColorStyle.WARM.matrix, grey) and 255))
        assertEquals(0xFFFFFFFF.toInt(), Pixels.apply(ColorStyle.VIVID.matrix, 0xFFFFFFFF.toInt()))   // clamped, not overflowing
        // A yellowish page with dark letters: paper turns white, ink black.
        val page = IntArray(100) { if (it < 10) 0xFF302A20.toInt() else 0xFFC8B890.toInt() }
        Pixels.document(page)
        assertEquals(0xFFFFFFFF.toInt(), page[50]); assertEquals(0xFF000000.toInt(), page[0])
        Pixels.document(IntArray(0))
        assertEquals(Triple(500, 0, 3000), Pixels.square(4000, 3000)); assertEquals(Triple(0, 500, 3000), Pixels.square(3000, 4000))
        assertEquals("Cámara · 23/11/2026 17:33", Pixels.watermark(LocalDateTime.of(2026, 11, 23, 17, 33)))
    }

    @Test fun proAndPortraitRules() {
        assertEquals("Auto", Pro.isoLabel(null)); assertEquals("800", Pro.isoLabel(800))
        assertEquals("1/1000", Pro.shutterLabel(1_000_000)); assertEquals("1/60", Pro.shutterLabel(16_666_667)); assertEquals("1/4", Pro.shutterLabel(250_000_000))
        assertEquals("0", Pro.evLabel(0, 1f / 3)); assertEquals("+1", Pro.evLabel(3, 1f / 3)); assertEquals("−0.7", Pro.evLabel(-2, 1f / 3))
        assertNull(Pro.manual(null, null)); assertEquals(800 to 16_666_667L, Pro.manual(800, null)); assertEquals(400 to 1_000_000L, Pro.manual(null, 1_000_000))
        assertEquals(listOf(24, 35, 50), Portrait.steps(10f)); assertEquals(listOf(24, 35), Portrait.steps(1.5f)); assertEquals(35 / 24f, Portrait.zoom(35))
        assertEquals(listOf(Scene.VIDEO, Scene.PHOTO, Scene.PORTRAIT, Scene.NIGHT), Scene.strip)
        val s = Settings(MapValues())
        assertEquals(Scene.PHOTO, s.scene); assertEquals(ColorStyle.NATURAL, s.color); assertTrue(s.stabilization); assertNull(s.iso)
        s.iso = 9999; assertNull(s.iso); s.iso = 400; assertEquals(400, s.iso); s.ev = 99; assertEquals(0, s.ev)
    }

    @Test fun scenesPanelsAndPro() {
        val kv = MapValues()
        val state = CamaraState(kv)
        state.toggle(Panel.QUICK); assertEquals(Panel.QUICK, state.panel); state.toggle(Panel.QUICK); assertEquals(Panel.NONE, state.panel)
        state.toggle(Panel.MORE); state.changeScene(Scene.PRO)
        assertEquals(Scene.PRO, state.scene); assertEquals(Mode.PHOTO, state.mode); assertEquals(Panel.NONE, state.panel)
        assertEquals(Scene.PRO, CamaraState(kv).scene)                                            // opens in the last mode
        state.changeIso(800); state.changeShutter(4_000_000); state.changeBalance(Balance.CLOUDY); state.evMax = 6; state.changeEv(12)
        assertEquals(6, state.ev); assertEquals(800, CamaraState(kv).iso)
        state.resetPro(); assertNull(state.iso); assertEquals(0, state.ev); assertEquals(Balance.AUTO, state.balance)
        // Square, colour, watermark or document: the photo is changed after it is taken.
        assertTrue(state.processed.not()); state.changeRatio(Ratio.R1_1); assertTrue(state.processed); state.changeRatio(Ratio.R4_3)
        state.changeScene(Scene.DOCUMENT); assertTrue(state.processed); state.changeScene(Scene.PHOTO); state.nextColor(); assertEquals(ColorStyle.VIVID, state.color)
        assertTrue(state.processed); state.changeColor(ColorStyle.NATURAL)
        // Flash in video: only off and the torch.
        state.changeFlash(Flash.ON); state.changeScene(Scene.VIDEO); assertEquals(Flash.OFF, state.flashNow)
        state.nextFlash(); assertEquals(Flash.TORCH, state.flashNow); state.nextFlash(); assertEquals(Flash.OFF, state.flash)
        assertFalse(state.processed)                                                               // videos are never changed
    }

    /** «Oye Lucía, toma una foto» (after «sí»): photo mode, the asked camera, a visible countdown; never while recording. */
    @Test fun voiceCaptureCountsDownFirst() = runBlocking {
        val state = CamaraState(MapValues(), launch = { runBlocking { it() } })
        val shot = mutableListOf<Mode>(); state.shoot = { shot += it }
        state.voiceCapture(selfie = false); assertTrue(shot.isEmpty())                       // no camera permission: nothing
        state.cameraAllowed = true; state.changeScene(Scene.VIDEO)
        val t0 = System.currentTimeMillis(); state.voiceCapture(selfie = true)
        assertTrue(System.currentTimeMillis() - t0 >= 2900)                                    // the countdown ran
        assertEquals(listOf(Mode.PHOTO), shot); assertEquals(Scene.PHOTO, state.scene); assertTrue(state.front)
        state.changeScene(Scene.VIDEO); state.press(); state.started()
        state.voiceCapture(selfie = false); assertEquals(Mode.VIDEO, shot.last()); assertEquals(2, shot.size)   // recording: untouched
    }

    /** Ajustes › Temporizador: off, 3, 5 or 10 s; the countdown says each second (for the beep) and fills its ring. */
    @Test fun timerCountsEverySecond() = runBlocking {
        val kv = MapValues()
        val state = CamaraState(kv, launch = { runBlocking { it() } })
        val ticks = mutableListOf<Int>(); val taken = mutableListOf<Mode>()
        state.tick = { ticks += it }; state.shoot = { taken += it }; state.cameraAllowed = true
        assertEquals(listOf("off", "3", "5", "10"), Timer.entries.map { it.key })
        state.changeTimer(Timer.S5); assertEquals(Timer.S5, CamaraState(kv).timer)           // remembered
        state.press()
        assertEquals(listOf(5, 4, 3, 2, 1), ticks); assertEquals(5, state.countdownFrom); assertNull(state.countdown); assertEquals(listOf(Mode.PHOTO), taken)
    }

    /** «Video dual»: only with a back and a front camera the phone can open together; never chosen when it can't. */
    @Test fun dualVideo() {
        assertTrue(Dual.possible(listOf(listOf(Dual.BACK, Dual.FRONT))))
        assertFalse(Dual.possible(emptyList())); assertFalse(Dual.possible(listOf(listOf(Dual.BACK, Dual.BACK), listOf(Dual.FRONT, null))))
        assertEquals(Dual.SCALE, Dual.OFFSET)                                                    // two halves, nobody stretched
        val state = CamaraState(MapValues())
        state.dualAvailable = false; state.changeScene(Scene.DUAL); assertEquals(Scene.PHOTO, state.scene)
        state.dualAvailable = true; state.changeScene(Scene.DUAL); assertEquals(Scene.DUAL, state.scene); assertEquals(Mode.VIDEO, state.mode)
        val front = state.front; state.flip(); assertEquals(front, state.front)                 // both cameras are already on
    }
}
