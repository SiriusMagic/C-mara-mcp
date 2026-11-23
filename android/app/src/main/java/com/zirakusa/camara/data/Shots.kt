package com.zirakusa.camara.data

import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Photo or video. [key] is what MCP uses. */
enum class Mode(val key: String) { PHOTO("photo"), VIDEO("video") }
/** [TORCH] keeps the light on («Linterna»), also while recording. */
enum class Flash(val key: String) { OFF("off"), AUTO("auto"), ON("on"), TORCH("torch") }
/** The self-timer in seconds. */
enum class Timer(val key: String, val seconds: Int) { OFF("off", 0), S3("3", 3), S10("10", 10) }
/** 1:1 is taken as 4:3 and its middle square kept. */
enum class Ratio(val key: String, val w: Int, val h: Int) { R4_3("4:3", 3, 4), R16_9("16:9", 9, 16), R1_1("1:1", 1, 1) }
enum class Quality(val key: String, val label: String) { HD("hd", "HD 720p"), FHD("fhd", "Full HD 1080p"), UHD("uhd", "4K") }

/** Pure camera rules: zoom buttons, the countdown, file names and the recording clock. */
object Lens {
    /** The zoom buttons a camera can do: 0.5× only with an ultra-wide lens, then 1×, 2× and 5× up to its maximum. */
    fun steps(min: Float, max: Float): List<Float> = listOf(0.5f, 1f, 2f, 5f).filter { it >= min - 0.01f && it <= max + 0.01f }.ifEmpty { listOf(1f) }

    /** «0.5×», «1×», «2.4×». */
    fun label(ratio: Float) = (if (ratio % 1f < 0.05f) "%d×".format(ratio.toInt()) else "%.1f×".format(java.util.Locale.ROOT, ratio))

    /** The zoom after pinching by [factor], kept inside the camera's range. */
    fun pinch(current: Float, factor: Float, min: Float, max: Float) = (current * factor).coerceIn(min, max)

    /** «IMG_20261121_093015.jpg» / «VID_….mp4», as phone cameras name them. */
    fun fileName(mode: Mode, at: LocalDateTime): String =
        (if (mode == Mode.PHOTO) "IMG_" else "VID_") + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(at) + if (mode == Mode.PHOTO) ".jpg" else ".mp4"

    /** «00:07», «1:02:03» for the recording time. */
    fun clock(seconds: Long): String {
        val h = seconds / 3600; val m = seconds / 60 % 60; val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}

/** A photo or video made with Cámara: where Android keeps it ([uri]), its name, when, and seconds for videos. */
data class Shot(val id: Long, val uri: String, val name: String, val mode: Mode, val at: Long, val seconds: Long = 0) {
    fun json(): JSONObject = JSONObject().put("id", id).put("uri", uri).put("name", name).put("mode", mode.key).put("at", at).put("seconds", seconds)
    companion object {
        fun from(o: JSONObject) = Shot(o.getLong("id"), o.getString("uri"), o.getString("name"),
            Mode.entries.firstOrNull { it.key == o.optString("mode") } ?: Mode.PHOTO, o.getLong("at"), o.optLong("seconds"))
    }
}

/** The last [MAX] photos and videos made here (not the pictures themselves: those are in the gallery). */
class Shots(private val kv: KeyValues) {
    fun list(): List<Shot> = runCatching { val a = JSONArray(kv.get(KEY) ?: "[]"); (0 until a.length()).map { Shot.from(a.getJSONObject(it)) } }.getOrDefault(emptyList())
    fun add(s: Shot): Shot {
        val items = list(); val saved = s.copy(id = (items.maxOfOrNull { it.id } ?: 0) + 1)
        kv.put(KEY, JSONArray((listOf(saved) + items).take(MAX).map { it.json() }).toString()); return saved
    }
    /** Forgets the ones the user deleted from the gallery. */
    fun keep(alive: (Shot) -> Boolean) { val items = list(); val left = items.filter(alive); if (left.size != items.size) kv.put(KEY, JSONArray(left.map { it.json() }).toString()) }
    companion object { const val MAX = 100; private const val KEY = "shots" }
}

/** Where the settings live: the phone's preferences in the app, a map in the tests. */
interface KeyValues {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MapValues(private val map: MutableMap<String, String> = mutableMapOf()) : KeyValues {
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

/** Every setting of Cámara, typed. */
class Settings(private val kv: KeyValues) {
    private inline fun <reified E : Enum<E>> pick(key: String, default: E): E = kv.get(key)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
    private fun flag(key: String, default: Boolean) = kv.get(key)?.let { it == "1" } ?: default
    private fun setFlag(key: String, on: Boolean) = kv.put(key, if (on) "1" else "0")

    var flash: Flash get() = pick("flash", Flash.OFF); set(v) = kv.put("flash", v.name)
    var timer: Timer get() = pick("timer", Timer.OFF); set(v) = kv.put("timer", v.name)
    var ratio: Ratio get() = pick("ratio", Ratio.R4_3); set(v) = kv.put("ratio", v.name)
    var quality: Quality get() = pick("quality", Quality.FHD); set(v) = kv.put("quality", v.name)
    var grid: Boolean get() = flag("grid", false); set(v) = setFlag("grid", v)
    var sound: Boolean get() = flag("sound", true); set(v) = setFlag("sound", v)
    /** The volume buttons take the photo (or start and stop the video). */
    var volumeKeys: Boolean get() = flag("volume", true); set(v) = setFlag("volume", v)
    /** Record sound with videos (needs the microphone permission). */
    var audio: Boolean get() = flag("audio", true); set(v) = setFlag("audio", v)
    var scene: Scene get() = pick("scene", Scene.PHOTO); set(v) = kv.put("scene", v.name)
    /** HDR with the phone maker's processing (only where the phone offers it). */
    var hdr: Boolean get() = flag("hdr", false); set(v) = setFlag("hdr", v)
    var color: ColorStyle get() = pick("color", ColorStyle.NATURAL); set(v) = kv.put("color", v.name)
    /** «Cámara · fecha» at the bottom of photos. */
    var watermark: Boolean get() = flag("watermark", false); set(v) = setFlag("watermark", v)
    var stabilization: Boolean get() = flag("stabilization", true); set(v) = setFlag("stabilization", v)
    var iso: Int? get() = kv.get("iso")?.toIntOrNull()?.takeIf { it in Pro.isos }; set(v) = kv.put("iso", v?.toString())
    var shutter: Long? get() = kv.get("shutter")?.toLongOrNull()?.takeIf { it in Pro.shutters }; set(v) = kv.put("shutter", v?.toString())
    var ev: Int get() = kv.get("ev")?.toIntOrNull()?.takeIf { it in Pro.evs } ?: 0; set(v) = kv.put("ev", v.toString())
    var balance: Balance get() = pick("balance", Balance.AUTO); set(v) = kv.put("balance", v.name)
    var theme: String? get() = kv.get("theme"); set(v) = kv.put("theme", v)
    var accent: String? get() = kv.get("accent"); set(v) = kv.put("accent", v)
}

/** The phone's preferences file «camara» (kept out of backups). The MCP tools and the family summary read the same. */
class SharedValues(context: android.content.Context) : KeyValues {
    private val prefs = context.applicationContext.getSharedPreferences("camara", android.content.Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String?) {
        prefs.edit(commit = true) { if (value == null) remove(key) else putString(key, value) }
    }
}
