package com.zirakusa.camara

import com.zirakusa.camara.data.MapValues
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.Settings
import com.zirakusa.camara.data.Shot
import com.zirakusa.camara.data.Shots
import com.zirakusa.camara.mcp.Access
import com.zirakusa.camara.mcp.CameraData
import com.zirakusa.camara.mcp.HubLink
import com.zirakusa.camara.mcp.McpHandler
import com.zirakusa.camara.mcp.Token
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset

/** Cámara's MCP: tools list (no shooting), good calls, bad arguments, not found, read-only tokens, wrong tokens, hub link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpTest {
    private val kv = MapValues()
    private val data = object : CameraData {
        override val settings = Settings(kv)
        override val shots = Shots(kv)
        override fun zone() = ZoneOffset.UTC
    }
    private val writer = Token("cam_" + "w".repeat(40), Access.WRITE)
    private val reader = Token("cam_" + "r".repeat(40), Access.READ)
    private val handler = McpHandler({ listOf(writer, reader) }, data, "0.1.0")

    init {
        data.shots.add(Shot(0, "content://media/1", "IMG_20231114_221320.jpg", Mode.PHOTO, 1_700_000_000_000))
        data.shots.add(Shot(0, "content://media/2", "VID_20231114_221500.mp4", Mode.VIDEO, 1_700_000_100_000, 42))
    }

    private fun rpc(token: Token, method: String, params: JSONObject = JSONObject()) = runBlocking {
        JSONObject(handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${token.secret}"), emptyMap(),
            JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString()).body).getJSONObject("result")
    }
    private fun tool(name: String, args: JSONObject, token: Token = writer) = rpc(token, "tools/call", JSONObject().put("name", name).put("arguments", args))
    private fun ok(name: String, args: JSONObject = JSONObject()) =
        tool(name, args).also { assertFalse(it.toString(), it.optBoolean("isError")) }.getJSONObject("structuredContent")
    private fun code(name: String, args: JSONObject = JSONObject(), token: Token = writer) = tool(name, args, token).also { assertTrue(it.optBoolean("isError")) }
        .getJSONObject("structuredContent").getJSONObject("error").getString("code")
    private fun names(token: Token) = rpc(token, "tools/list").getJSONArray("tools").let { t -> (0 until t.length()).map { t.getJSONObject(it).getString("name") } }

    @Test fun listsTheToolsWithoutShooting() {
        assertEquals(listOf("camera_settings", "camera_recent", "camera_shot", "camera_settings_set"), names(writer))
        assertEquals(listOf("camera_settings", "camera_recent", "camera_shot"), names(reader))
        assertEquals("UNKNOWN_TOOL", code("camera_take_photo"))
        assertEquals("camara", rpc(writer, "initialize").getJSONObject("serverInfo").getString("name"))
    }

    @Test fun readsAndChanges() {
        assertEquals("off", ok("camera_settings").getString("flash"))
        val recent = ok("camera_recent")
        assertEquals(2, recent.getInt("total")); assertEquals("video", recent.getJSONArray("shots").getJSONObject(0).getString("mode"))
        assertFalse(recent.getJSONArray("shots").getJSONObject(0).has("uri"))                    // where the file is never goes out
        assertEquals(1, ok("camera_recent", JSONObject().put("mode", "photo")).getInt("total"))
        assertEquals("2023-11-14T22:13:20", ok("camera_shot", JSONObject().put("id", 1)).getJSONObject("shot").getString("at"))
        val set = ok("camera_settings_set", JSONObject().put("flash", "auto").put("timer", "10").put("grid", true))
        assertEquals("auto", set.getString("flash")); assertEquals("10", set.getString("timer")); assertTrue(data.settings.grid)
        val looks = ok("camera_settings_set", JSONObject().put("mode", "night").put("color", "sepia").put("watermark", true).put("flash", "torch"))
        assertEquals("night", looks.getString("mode")); assertEquals("sepia", looks.getString("color")); assertTrue(looks.getBoolean("watermark"))
        assertEquals("torch", looks.getString("flash")); assertTrue(looks.getBoolean("stabilization"))
    }

    @Test fun badArgumentsChangeNothing() {
        assertEquals("BAD_ARGUMENT", code("camera_settings_set", JSONObject().put("flash", "auto").put("timer", "7")))
        assertEquals("5", ok("camera_settings_set", JSONObject().put("timer", "5")).getString("timer"))     // Ajustes › Temporizador, 5 s
        assertEquals("off", data.settings.flash.key)                                              // checked before changing
        assertEquals("BAD_ARGUMENT", code("camera_settings_set", JSONObject()))
        assertEquals("BAD_ARGUMENT", code("camera_settings_set", JSONObject().put("grid", "yes")))
        assertEquals("BAD_ARGUMENT", code("camera_settings_set", JSONObject().put("color", "neon").put("watermark", true)))
        assertFalse(data.settings.watermark)
        assertEquals("BAD_ARGUMENT", code("camera_settings_set", JSONObject().put("mode", "slowmo")))
        assertEquals("BAD_ARGUMENT", code("camera_recent", JSONObject().put("mode", "selfie")))
        assertEquals("BAD_ARGUMENT", code("camera_recent", JSONObject().put("limit", 0)))
        assertEquals("BAD_ARGUMENT", code("camera_shot", JSONObject().put("id", "1")))
    }

    @Test fun notFoundAndReadOnly() {
        assertEquals("NOT_FOUND", code("camera_shot", JSONObject().put("id", 99)))
        assertEquals("READ_ONLY", code("camera_settings_set", JSONObject().put("grid", true), reader))
        assertFalse(data.settings.grid)
    }

    @Test fun wrongAndPausedTokensAndHubLink() = runBlocking {
        val body = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer wrong_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer cam_wrong"), emptyMap(), body).status)
        assertTrue(Token.secret().startsWith("cam_"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.answer(false).getString("error"))
        assertEquals("REDIRECT_OFF", HubLink.answer(true).getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.connect(false).getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, "{}").getString("error"))
        // Only the same developer (Arka) may connect, disconnect, read the status or run a tool.
        listOf(HubLink.disconnect(false), HubLink.status(false)).forEach { assertEquals("NOT_SAME_DEVELOPER", it.getString("error")) }
    }

    /** Lucía runs a tool on the phone through the same handler, with or without the public link; Arka turns that on and off. */
    @Test fun luciaRunsToolsOnThePhone() {
        val relay = com.zirakusa.camara.mcp.McpMode.relayUrl
        com.zirakusa.camara.mcp.McpMode.relayUrl = ""                                             // no relay in tests
        com.zirakusa.camara.mcp.McpMode.load(org.robolectric.RuntimeEnvironment.getApplication())
        com.zirakusa.camara.mcp.McpMode.turnRemote(false)
        val list = JSONObject(com.zirakusa.camara.mcp.McpMode.local("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")!!)
        assertTrue(list.getJSONObject("result").getJSONArray("tools").length() > 0)
        val viaProvider = JSONObject(HubLink.tool(true, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").getString("response"))
        assertEquals(2, viaProvider.getInt("id"))
        assertEquals("BAD_REQUEST", HubLink.tool(true, "").getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, "{}").getString("error"))
        assertTrue(HubLink.status(true).containsKey("remote"))
        HubLink.connect(true); assertTrue(com.zirakusa.camara.mcp.McpMode.remote)                 // Arka › «Activar MCP»
        assertFalse(HubLink.disconnect(true).getBoolean("remote")); assertFalse(com.zirakusa.camara.mcp.McpMode.remote)
        com.zirakusa.camara.mcp.McpMode.relayUrl = relay                                          // as the other tests expect
    }

    /** The hub's token from before Arka took over is kept, so the hub's link keeps working. */
    @Test fun theOldHubTokenIsKept() {
        val old = """[{"id":"a","secret":"${"l".repeat(44)}","kind":"llm"},{"id":"b","secret":"${"h".repeat(44)}","kind":"redirect"}]"""
        assertEquals("h".repeat(44), com.zirakusa.camara.mcp.Legacy.redirectSecret(old))
        assertEquals(null, com.zirakusa.camara.mcp.Legacy.redirectSecret("[]"))
        assertEquals(null, com.zirakusa.camara.mcp.Legacy.redirectSecret(""))
    }
}
