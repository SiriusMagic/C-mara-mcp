package com.zirakusa.camara.mcp

import com.zirakusa.camara.data.Flash
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.Quality
import com.zirakusa.camara.data.Ratio
import com.zirakusa.camara.data.Settings
import com.zirakusa.camara.data.Shot
import com.zirakusa.camara.data.Shots
import com.zirakusa.camara.data.Timer
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** What the tools read and change; the app gives its settings and shots, tests maps in memory. */
interface CameraData {
    val settings: Settings
    val shots: Shots
    fun zone(): ZoneId = ZoneId.systemDefault()
    /** After a tool changed a setting, so the screen shows it. */
    fun changed() {}
}

/** An expected failure the AI can read (structuredContent.error.code), never a crash. */
class ToolFailure(val code: String, message: String) : Exception(message)

/**
 * Cámara's MCP tools: the camera's settings and the photos and videos made with it (names and times, never the images);
 * with a read-and-write token also change the settings. There is no tool to take a photo or record: only the user does.
 */
object CameraTools {
    class Tool(val name: String, val description: String, val write: Boolean, val schema: JSONObject)

    private fun schema(required: List<String>, vararg props: Pair<String, JSONObject>) = JSONObject()
        .put("type", "object").put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
        .put("required", JSONArray(required)).put("additionalProperties", false)
    private fun prop(type: String, description: String) = JSONObject().put("type", type).put("description", description)
    private fun choice(values: List<String>, description: String) = JSONObject().put("type", "string").put("enum", JSONArray(values)).put("description", description)

    val tools = listOf(
        Tool("camera_settings", "The camera's settings: flash, self-timer, photo ratio, video quality, grid, shutter sound.", false, schema(emptyList())),
        Tool("camera_recent", "Photos and videos made with Cámara, newest first (names and times; the images stay in the gallery).", false,
            schema(emptyList(), "mode" to choice(Mode.entries.map { it.key }, "Only photos or only videos."), "limit" to prop("integer", "1–100, default 20."))),
        Tool("camera_shot", "One photo or video made with Cámara.", false, schema(listOf("id"), "id" to prop("integer", "From camera_recent."))),
        Tool("camera_settings_set", "Changes some settings for the next photos and videos (only those given).", true,
            schema(emptyList(), "flash" to choice(Flash.entries.map { it.key }, "Flash."), "timer" to choice(Timer.entries.map { it.key }, "Self-timer in seconds."),
                "ratio" to choice(Ratio.entries.map { it.key }, "Photo shape."), "video_quality" to choice(Quality.entries.map { it.key }, "Video quality."),
                "grid" to prop("boolean", "Lines of thirds on the screen."), "sound" to prop("boolean", "Shutter sound."))),
    )

    fun available(access: Access) = tools.filter { !it.write || access == Access.WRITE }

    fun call(name: String, args: JSONObject, access: Access, data: CameraData): JSONObject {
        val tool = tools.firstOrNull { it.name == name } ?: throw ToolFailure("UNKNOWN_TOOL", "No tool named $name. (Cámara never takes photos for the AI.)")
        if (tool.write && access != Access.WRITE) throw ToolFailure("READ_ONLY", "This token can only read. Ask for a read-and-write token.")
        val known = tool.schema.getJSONObject("properties").keys().asSequence().toSet()
        args.keys().forEach { if (it !in known) throw ToolFailure("BAD_ARGUMENT", "Unknown argument «$it».") }
        return when (name) {
            "camera_settings" -> settings(data.settings)
            "camera_recent" -> {
                val mode = if (args.has("mode")) Mode.entries.firstOrNull { it.key == args.opt("mode") } ?: throw ToolFailure("BAD_ARGUMENT", "«mode» is photo or video.") else null
                val limit = if (args.has("limit")) ((args.opt("limit") as? Int) ?: throw ToolFailure("BAD_ARGUMENT", "«limit» is a whole number."))
                    .takeIf { it in 1..100 } ?: throw ToolFailure("BAD_ARGUMENT", "«limit» is 1–100.") else 20
                val all = data.shots.list().filter { mode == null || it.mode == mode }
                JSONObject().put("total", all.size).put("shots", JSONArray(all.take(limit).map { shot(it, data.zone()) }))
            }
            "camera_shot" -> {
                val id = (args.opt("id") as? Int)?.toLong() ?: (args.opt("id") as? Long) ?: throw ToolFailure("BAD_ARGUMENT", "«id» is a whole number.")
                JSONObject().put("shot", shot(data.shots.list().firstOrNull { it.id == id } ?: throw ToolFailure("NOT_FOUND", "No photo or video with id $id."), data.zone()))
            }
            "camera_settings_set" -> set(args, data)
            else -> throw ToolFailure("UNKNOWN_TOOL", "No tool named $name.")
        }
    }

    private fun set(args: JSONObject, data: CameraData): JSONObject {
        val s = data.settings
        // Check everything first, then change: a bad value changes nothing.
        val flash = pick(args, "flash", Flash.entries) { it.key }
        val timer = pick(args, "timer", Timer.entries) { it.key }
        val ratio = pick(args, "ratio", Ratio.entries) { it.key }
        val quality = pick(args, "video_quality", Quality.entries) { it.key }
        val grid = flag(args, "grid"); val sound = flag(args, "sound")
        if (args.length() == 0) throw ToolFailure("BAD_ARGUMENT", "Give at least one setting to change.")
        flash?.let { s.flash = it }; timer?.let { s.timer = it }; ratio?.let { s.ratio = it }; quality?.let { s.quality = it }
        grid?.let { s.grid = it }; sound?.let { s.sound = it }
        data.changed()
        return settings(s)
    }

    private fun <E> pick(args: JSONObject, key: String, values: List<E>, keyOf: (E) -> String): E? =
        if (!args.has(key)) null else values.firstOrNull { keyOf(it) == args.opt(key) } ?: throw ToolFailure("BAD_ARGUMENT", "«$key» is one of ${values.joinToString { keyOf(it) }}.")
    private fun flag(args: JSONObject, key: String): Boolean? = if (!args.has(key)) null else args.opt(key) as? Boolean ?: throw ToolFailure("BAD_ARGUMENT", "«$key» is true or false.")

    private fun settings(s: Settings) = JSONObject().put("flash", s.flash.key).put("timer", s.timer.key).put("ratio", s.ratio.key)
        .put("video_quality", s.quality.key).put("grid", s.grid).put("sound", s.sound)

    private fun shot(s: Shot, zone: ZoneId) = JSONObject().put("id", s.id).put("name", s.name).put("mode", s.mode.key)
        .put("at", Instant.ofEpochMilli(s.at).atZone(zone).toLocalDateTime().withNano(0).toString()).put("seconds", s.seconds)
}
