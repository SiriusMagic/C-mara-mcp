package com.zirakusa.camara.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The camera's modes, as on the strip at the bottom. [kind] says if it makes a photo or a video; [main] ones are on the
 * strip, the rest under «Más». Retrato, Noche (and HDR) use the phone maker's processing when the phone offers it.
 */
enum class Scene(val key: String, val kind: Mode, val main: Boolean) {
    VIDEO("video", Mode.VIDEO, true),
    PHOTO("photo", Mode.PHOTO, true),
    PORTRAIT("portrait", Mode.PHOTO, true),
    NIGHT("night", Mode.PHOTO, true),
    PRO("pro", Mode.PHOTO, false),
    DOCUMENT("document", Mode.PHOTO, false);

    companion object {
        val strip = entries.filter { it.main }
        val more = entries.filter { !it.main }
    }
}

/** «Estilo de color»: a 4×5 colour matrix (as Android's ColorMatrix) used on the preview and on the saved photo. */
enum class ColorStyle(val key: String, val matrix: FloatArray) {
    NATURAL("natural", identity()),
    VIVID("vivid", saturation(1.45f)),
    WARM("warm", tint(1.10f, 1.0f, 0.86f)),
    COOL("cool", tint(0.88f, 1.0f, 1.12f)),
    MONO("mono", saturation(0f)),
    SEPIA("sepia", floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f)),
}

private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
private fun tint(r: Float, g: Float, b: Float) = floatArrayOf(r, 0f, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
/** The same as Android's ColorMatrix.setSaturation. */
private fun saturation(s: Float): FloatArray {
    val i = 1 - s; val r = 0.213f * i; val g = 0.715f * i; val b = 0.072f * i
    return floatArrayOf(r + s, g, b, 0f, 0f, r, g + s, b, 0f, 0f, r, g, b + s, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
}

/** Pure pixel work on ARGB ints (the photo after it is taken): colour styles, documents, square crop, watermark text. */
object Pixels {
    private fun clamp(v: Float) = Math.round(v).coerceIn(0, 255)

    /** One ARGB pixel through a colour matrix. */
    fun apply(m: FloatArray, argb: Int): Int {
        val a = argb ushr 24 and 255; val r = argb shr 16 and 255; val g = argb shr 8 and 255; val b = argb and 255
        val nr = clamp(m[0] * r + m[1] * g + m[2] * b + m[3] * a + m[4])
        val ng = clamp(m[5] * r + m[6] * g + m[7] * b + m[8] * a + m[9])
        val nb = clamp(m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14])
        val na = clamp(m[15] * r + m[16] * g + m[17] * b + m[18] * a + m[19])
        return na shl 24 or (nr shl 16) or (ng shl 8) or nb
    }

    fun luma(argb: Int) = (0.299f * (argb shr 16 and 255) + 0.587f * (argb shr 8 and 255) + 0.114f * (argb and 255)).toInt()

    /**
     * «Documentos»: grey, with the paper made white and the ink dark. The darkest 5 % and the brightest 5 % of the
     * picture set the stretch, so a page under yellow light still comes out clean.
     */
    fun document(pixels: IntArray) {
        if (pixels.isEmpty()) return
        val hist = IntArray(256); pixels.forEach { hist[luma(it)]++ }
        val cut = pixels.size / 20
        var low = 0; var seen = 0
        while (low < 255 && seen + hist[low] <= cut) { seen += hist[low]; low++ }
        var high = 255; seen = 0
        while (high > low && seen + hist[high] <= cut) { seen += hist[high]; high-- }
        val span = (high - low).coerceAtLeast(1).toFloat()
        for (i in pixels.indices) {
            val v = ((luma(pixels[i]) - low) / span).coerceIn(0f, 1f)
            // A soft S-curve: paper goes white and letters stay crisp.
            val c = clamp(255f * (v * v * (3 - 2 * v)))
            pixels[i] = (0xFF shl 24) or (c shl 16) or (c shl 8) or c
        }
    }

    /** The middle square of a w×h picture: left, top and side. */
    fun square(w: Int, h: Int): Triple<Int, Int, Int> { val side = minOf(w, h); return Triple((w - side) / 2, (h - side) / 2, side) }

    /** «Cámara · 23/11/2026 17:33», the text at the bottom of the photo. */
    fun watermark(at: LocalDateTime): String = "Cámara · " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(at)
}

/** «Pro»: the values the user steps through. Null means automatic. */
object Pro {
    val isos = listOf<Int?>(null, 100, 200, 400, 800, 1600, 3200)
    /** Shutter times in nanoseconds: 1/1000 s to 1/4 s. */
    val shutters = listOf<Long?>(null, 1_000_000, 2_000_000, 4_000_000, 8_000_000, 16_666_667, 33_333_333, 66_666_667, 125_000_000, 250_000_000)
    val evs = (-6..6).toList()

    fun isoLabel(iso: Int?) = iso?.toString() ?: "Auto"
    fun shutterLabel(ns: Long?) = when {
        ns == null -> "Auto"
        ns >= 1_000_000_000 -> "${ns / 1_000_000_000}s"
        else -> "1/${Math.round(1_000_000_000.0 / ns)}"
    }
    /** EV in thirds: «0», «+1», «−0.7». */
    fun evLabel(index: Int, step: Float): String {
        val v = index * step
        if (kotlin.math.abs(v) < 0.05f) return "0"
        val text = if (kotlin.math.abs(v - Math.round(v)) < 0.05f) "${Math.round(kotlin.math.abs(v))}" else "%.1f".format(java.util.Locale.ROOT, kotlin.math.abs(v))
        return (if (v > 0) "+" else "−") + text
    }
    /** Manual exposure needs both: when only one is set, the other takes a middle value. */
    fun manual(iso: Int?, shutter: Long?): Pair<Int, Long>? = if (iso == null && shutter == null) null else (iso ?: 400) to (shutter ?: 16_666_667)
}

/** White balance for Pro. [key] is what MCP and the settings use. */
enum class Balance(val key: String) { AUTO("auto"), DAYLIGHT("daylight"), CLOUDY("cloudy"), INCANDESCENT("incandescent"), FLUORESCENT("fluorescent") }

/** Retrato's lens buttons as a phone's 24 mm main camera sees them: 24, 35 and 50 mm. */
object Portrait {
    val focals = listOf(24, 35, 50)
    fun zoom(mm: Int) = mm / 24f
    /** The buttons the camera can reach. */
    fun steps(max: Float) = focals.filter { zoom(it) <= max + 0.01f }
}

/** The short label of the video quality on the top bar: «720P», «1080P», «4K». */
fun Quality.short() = when (this) { Quality.HD -> "720P"; Quality.FHD -> "1080P"; Quality.UHD -> "4K" }
