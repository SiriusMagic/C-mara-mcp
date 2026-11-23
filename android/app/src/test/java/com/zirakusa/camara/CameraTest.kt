package com.zirakusa.camara

import com.zirakusa.camara.data.Flash
import com.zirakusa.camara.data.Lens
import com.zirakusa.camara.data.MapValues
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.Settings
import com.zirakusa.camara.data.Shot
import com.zirakusa.camara.data.Shots
import com.zirakusa.camara.data.Timer
import com.zirakusa.camara.ui.CamaraState
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
        state.nextFlash(); assertEquals(Flash.AUTO, state.flash); assertEquals(Flash.AUTO, CamaraState(kv).flash)
        state.zoomMin = 0.5f; state.zoomMax = 8f; state.zoomTo(20f); assertEquals(8f, state.zoom); assertEquals(listOf(0.5f, 1f, 2f, 5f), state.steps)
    }
}
