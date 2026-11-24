package com.zirakusa.camara

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.zirakusa.camara.data.ColorStyle
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.SharedValues
import com.zirakusa.camara.mcp.McpMode
import com.zirakusa.camara.ui.AppActions
import com.zirakusa.camara.ui.CamaraApp
import com.zirakusa.camara.ui.CamaraState
import com.zirakusa.camara.ui.Page
import com.zirakusa.camara.ui.Panel
import com.zirakusa.camara.ui.theme.CamaraTheme
import kotlinx.coroutines.launch

/** Creates the state and the camera, asks for permissions, keeps the camera in step with the settings, and the volume buttons shoot. */
class MainActivity : ComponentActivity() {
    private lateinit var state: CamaraState
    private lateinit var camera: Camera
    private val allow = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppIcon.load(this)
        enableEdgeToEdge()
        state = CamaraState(SharedValues(this), launch = { block -> lifecycleScope.launch { block() } })
        camera = Camera(this, state)
        McpMode.onChanged = { state.reload() }
        McpMode.load(this)
        lifecycleScope.launch { snapshotFlow { listOf(state.scene, state.front, state.hdr) }.collect { camera.bind() } }
        lifecycleScope.launch { snapshotFlow { state.flashNow }.collect { camera.flash() } }
        lifecycleScope.launch { snapshotFlow { state.ratio to state.processed }.collect { camera.resolution() } }
        lifecycleScope.launch { snapshotFlow { state.quality }.collect(camera::quality) }
        lifecycleScope.launch { snapshotFlow { state.zoom }.collect(camera::zoom) }
        lifecycleScope.launch { snapshotFlow { listOf(state.ev, state.iso, state.shutter, state.balance, state.stabilization) }.collect { camera.tune() } }
        val actions = AppActions(preview = { m -> Preview(m) },
            allow = ::ask, openLast = ::openLast, copyLink = ::copyLink, goToHub = ::goToHub)
        permissions()
        if (!state.cameraAllowed) ask()
        setContent {
            CamaraTheme(state.themeMode, state.accent) {
                // The camera is always dark: light bars on it.
                SideEffect { WindowCompat.getInsetsController(window, window.decorView).run { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false } }
                BackHandler(state.page != Page.CAMERA || state.panel != Panel.NONE) {
                    if (state.page == Page.CAMERA) state.panel = Panel.NONE else state.page = if (state.page == Page.MCP) Page.SETTINGS else Page.CAMERA
                }
                CamaraApp(state, actions)
            }
        }
    }

    /** The live picture, with the colour style on it (Android 12 and later; the saved photo always has it). */
    @Composable private fun Preview(modifier: Modifier) {
        val style = state.color
        val effect = if (Build.VERSION.SDK_INT >= 31 && style != ColorStyle.NATURAL) colorEffect(style.matrix) else null
        val look = if (effect != null) modifier.graphicsLayer { renderEffect = effect } else modifier
        // TextureView (COMPATIBLE) so the colour style can be drawn over the picture.
        AndroidView({ PreviewView(it).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; controller = camera.controller } }, look)
    }

    @RequiresApi(31) private fun colorEffect(m: FloatArray) = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(m)).asComposeRenderEffect()

    override fun onResume() {
        super.onResume()
        permissions(); state.reload()
        // Photos deleted in the gallery are forgotten here too.
        state.forget { s -> runCatching { contentResolver.query(s.uri.toUri(), arrayOf("_id"), null, null, null)?.use { it.count > 0 } ?: false }.getOrDefault(true) }
    }

    override fun onDestroy() { camera.release(); super.onDestroy() }

    /** The volume buttons take the photo or start and stop the video, as on most cameras. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && state.volumeKeys && state.page == Page.CAMERA && state.cameraAllowed) {
            if (event?.repeatCount == 0) state.press()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun permissions() {
        state.cameraAllowed = granted(Manifest.permission.CAMERA) && (Build.VERSION.SDK_INT >= 29 || granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        state.micAllowed = granted(Manifest.permission.RECORD_AUDIO)
    }
    private fun ask() = allow.launch(buildList {
        add(Manifest.permission.CAMERA); add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }.toTypedArray())

    /** The last photo or video, in Luma when installed, otherwise the phone's gallery. */
    private fun openLast() {
        val last = state.last ?: return
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(last.uri.toUri(), if (last.mode == Mode.PHOTO) "image/*" else "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(Intent(view).setPackage(LUMA)) } catch (_: ActivityNotFoundException) {
            try { startActivity(view) } catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun copyLink(link: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Cámara MCP", link))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    /** «Ir al Hub»: copies the hub's link (in case it must be pasted) and opens the Gateway Hub app. */
    private fun goToHub() {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Cámara › Gateway Hub", McpMode.hubLink()))
        val hub = packageManager.getLaunchIntentForPackage("com.zirakusa.gatewayhub")
        if (hub != null) startActivity(hub) else Toast.makeText(this, R.string.mcp_paste_in_hub, Toast.LENGTH_LONG).show()
    }

    companion object { const val LUMA = "app.luma.gallery" }
}
