package com.zynelabs.flowplay

import androidx.compose.ui.platform.LocalConfiguration

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.zynelabs.flowplay.GlassText as Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.zynelabs.flowplay.data.Cats
import com.zynelabs.flowplay.data.Channel
import com.zynelabs.flowplay.data.ChannelRepo
import com.zynelabs.flowplay.data.ImageLoader
import com.zynelabs.flowplay.data.PlAccount
import com.zynelabs.flowplay.data.PlaylistCache
import com.zynelabs.flowplay.data.Store
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- FlowPlay cinematic design system ----------
// Deep cinematic dark theme (#0B0B10) with crimson red accent. `Accent` keeps
// its historic name so the shared player/settings code compiles unchanged.
internal val Bg = Color(0xFF0B0B10)
internal val Card = Color(0xE616161D)
internal val CardSel = Color(0xE620202B)
internal val BtnBg = Color(0x8020202B)
internal val PanelBg = Color(0x9916161D)
/** Accent color choices: key -> (label, color). Crimson family only. */
internal val ACCENTS = linkedMapOf(
    "crimson" to ("Crimson" to Color(0xFFE5323B)),
    "scarlet" to ("Scarlet" to Color(0xFFFF5147)),
    "wine" to ("Wine Red" to Color(0xFFB3122E))
)
internal var AccentName by mutableStateOf("crimson")
/**
 * Current accent color. Backed by [AccentName] state, so every composable
 * reading [Accent] recomposes live when the user picks another accent —
 * no restart needed.
 */
internal val Accent: Color get() = ACCENTS[AccentName]?.second ?: Color(0xFFE5323B)
internal fun accentLabel(): String = ACCENTS[AccentName]?.first ?: "Crimson"
/** Content view mode: "grid" or "list". Backed by state so tabs re-render live. */
internal var ViewMode by mutableStateOf("grid")
internal val Ink = Color(0xFFF2F0FA)
internal val Dim = Color(0xFF8F8FA3)

/** Background tick (kept for shared settings code). */
object AppBg { var tick by mutableStateOf(0) }

/** Plain background (FlowPlay has no custom image picker yet). */
@Composable
fun AppBackground() {
    Box(Modifier.fillMaxSize().background(Bg))
}

/** TV-mode flag, set once in onCreate from UiModeManager. */
object DzTv { var isTv = false }

object AppCtx { var ctx: Context? = null }

/**
 * TV D-pad focus treatment: scale up to 1.1x, glowing neon-purple border and
 * an elevation shadow while focused. On mobile (touch) it only reports focus
 * changes and renders no visuals — touch uses press states instead.
 */
fun Modifier.tvFocus(
    corner: Dp = 16.dp,
    onFocused: (Boolean) -> Unit = {}
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val tv = DzTv.isTv
    val scale by animateFloatAsState(
        if (tv && focused) 1.1f else 1f, label = "dz_focus_scale"
    )
    this
        .onFocusChanged {
            focused = it.isFocused
            onFocused(it.isFocused)
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = if (tv && focused) 18.dp.toPx() else 0f
            shape = RoundedCornerShape(corner)
            clip = false
        }
        .border(
            width = if (tv && focused) 2.dp else 0.dp,
            color = if (tv && focused) Accent else Color.Transparent,
            shape = RoundedCornerShape(corner)
        )
}

// ---------- thin-line vector icons (no emoji) ----------
@Composable
private fun RawDzIcon(kind: String, color: Color = Ink, iconSize: Dp = 24.dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width; val h = size.height
        val sw = w * 0.075f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2),
                strokeWidth = sw, cap = StrokeCap.Round)
        when (kind) {
            "home" -> {
                val p = Path().apply {
                    moveTo(w * 0.12f, h * 0.52f); lineTo(w * 0.5f, h * 0.16f)
                    lineTo(w * 0.88f, h * 0.52f); moveTo(w * 0.22f, h * 0.46f)
                    lineTo(w * 0.22f, h * 0.86f); lineTo(w * 0.78f, h * 0.86f)
                    lineTo(w * 0.78f, h * 0.46f)
                }
                drawPath(p, color, style = Stroke(sw, cap = StrokeCap.Round))
            }
            "live" -> {
                drawRoundRect(color, Offset(w * 0.08f, h * 0.2f),
                    Size(w * 0.84f, h * 0.55f), CornerRadius(w * 0.08f),
                    style = Stroke(sw))
                line(0.38f, 0.75f, 0.62f, 0.75f); line(0.5f, 0.75f, 0.5f, 0.88f)
                line(0.32f, 0.88f, 0.68f, 0.88f)
            }
            "folder" -> {
                val p = Path().apply {
                    moveTo(w * 0.12f, h * 0.28f); lineTo(w * 0.40f, h * 0.28f)
                    lineTo(w * 0.49f, h * 0.39f); lineTo(w * 0.88f, h * 0.39f)
                    lineTo(w * 0.88f, h * 0.82f); lineTo(w * 0.12f, h * 0.82f); close()
                }
                drawPath(p, color, style = Stroke(sw, cap = StrokeCap.Round))
            }
            "film" -> {
                drawRoundRect(color, Offset(w * 0.1f, h * 0.18f),
                    Size(w * 0.8f, h * 0.64f), CornerRadius(w * 0.06f),
                    style = Stroke(sw))
                line(0.3f, 0.18f, 0.3f, 0.82f); line(0.7f, 0.18f, 0.7f, 0.82f)
            }
            "series" -> {
                drawRoundRect(color, Offset(w * 0.14f, h * 0.3f),
                    Size(w * 0.72f, h * 0.56f), CornerRadius(w * 0.06f),
                    style = Stroke(sw))
                drawRoundRect(color, Offset(w * 0.14f, h * 0.14f),
                    Size(w * 0.72f, h * 0.56f), CornerRadius(w * 0.06f),
                    style = Stroke(sw))
            }
            "settings" -> {
                line(0.15f, 0.3f, 0.85f, 0.3f); line(0.15f, 0.5f, 0.85f, 0.5f)
                line(0.15f, 0.7f, 0.85f, 0.7f)
                drawCircle(color, w * 0.09f, Offset(w * 0.62f, h * 0.3f))
                drawCircle(color, w * 0.09f, Offset(w * 0.35f, h * 0.5f))
                drawCircle(color, w * 0.09f, Offset(w * 0.68f, h * 0.7f))
            }
            "play" -> {
                val p = Path().apply {
                    moveTo(w * 0.35f, h * 0.25f); lineTo(w * 0.75f, h * 0.5f)
                    lineTo(w * 0.35f, h * 0.75f); close()
                }
                drawPath(p, color)
            }
            "search" -> {
                drawCircle(
                    color, w * 0.28f, Offset(w * 0.44f, h * 0.44f),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
                line(0.64f, 0.64f, 0.86f, 0.86f)
            }
            "info" -> {
                drawCircle(
                    color, w * 0.38f, Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
                line(0.5f, 0.46f, 0.5f, 0.68f)
                drawCircle(color, sw * 0.7f, Offset(w * 0.5f, h * 0.34f))
            }
            "close" -> {
                line(0.3f, 0.3f, 0.7f, 0.7f); line(0.7f, 0.3f, 0.3f, 0.7f)
            }
        }
    }
}

/** Async network image via the shared ImageLoader. */
@Composable
fun DzImage(url: String, modifier: Modifier = Modifier, fit: Boolean = false,
            placeholder: android.graphics.drawable.Drawable? = null) {
    AndroidView(
        factory = { c ->
            android.widget.ImageView(c).apply {
                scaleType =
                    if (fit) android.widget.ImageView.ScaleType.FIT_CENTER
                    else android.widget.ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { iv ->
            iv.scaleType = if (fit) android.widget.ImageView.ScaleType.FIT_CENTER
                else android.widget.ImageView.ScaleType.CENTER_CROP
            ImageLoader.load(url, iv, placeholder)
        },
        modifier = modifier
    )
}

/** Only Movies/Series receive local substitutes; genuine provider artwork always wins. */
@Composable
private fun MediaArtwork(ch: Channel, modifier: Modifier, compact: Boolean = false,
                         placeholder: android.graphics.drawable.Drawable? = null) {
    val context = LocalContext.current
    val local = remember(context, ch.name, ch.kind, compact, placeholder) {
        placeholder ?: com.zynelabs.flowplay.data.LocalPosterDrawable.create(
            context, ch.name, ch.kind, compact || ch.container == "FLOWPLAY_CATEGORY")
    }
    DzImage(ch.logo, modifier, fit = compact, placeholder = local)
}

// ---------- data ----------
private data class DzUi(
    val loading: Boolean = true,
    val account: PlAccount? = null,
    val title: String = "",
    val live: List<Channel> = emptyList(),
    val vod: List<Channel> = emptyList(),
    val series: List<Channel> = emptyList(),
    val error: String? = null
)

private fun providerCountLabel(count: Int): String =
    if (count >= 0) java.text.NumberFormat.getIntegerInstance().format(count) + " Live channels"
    else "Live channels: Unknown"

private fun providerExpiryLabel(expDate: Long): String =
    if (expDate > 0L) {
        val date = java.util.Date(if (expDate < 100000000000L) expDate * 1000L else expDate)
        "Exp: " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(date)
    } else "Exp: Unknown"

private fun selectedAccount(ctx: Context): PlAccount? {
    val store = Store(ctx)
    return store.account(store.activeAccountId())?.takeIf { it.enabled }
        ?: store.enabledAccounts().firstOrNull()
}

private fun playlistUi(
    ctx: Context, acc: PlAccount,
    live: List<Channel>, vod: List<Channel>, series: List<Channel>,
    error: String? = null
): DzUi {
    val locked = Store(ctx).adultLocked()
    fun visible(items: List<Channel>) =
        if (locked) items.filter { it.getSmartCat() != Cats.ADULT } else items
    return DzUi(loading = false, account = acc,
        title = acc.name.ifEmpty { "FlowPlay" },
        live = visible(live), vod = visible(vod), series = visible(series), error = error)
}

private fun loadDz(): DzUi {
    val ctx = AppCtx.ctx ?: return DzUi(loading = false, error = "No context")
    return try {
        val acc = selectedAccount(ctx) ?: return DzUi(loading = false)
        val data = PlaylistCache.load(ctx.filesDir, acc.id)
            ?: return DzUi(loading = false, account = acc,
                title = acc.name.ifEmpty { "FlowPlay" },
                error = "No saved data yet — tap Reload to load this provider.")
        playlistUi(ctx, acc, data.live, data.vod, data.series)
    } catch (t: Throwable) {
        DzUi(loading = false, error = "Load failed: ${t.message}")
    }
}

private data class FreshPlaylist(
    val live: List<Channel>, val vod: List<Channel>, val series: List<Channel>, val error: String?
)
private data class ProviderRefreshResult(val ui: DzUi, val successful: Boolean)

// ---------- activity ----------
class MainActivity : ComponentActivity() {
    private data class NavigationAppearance(
        val color: Int, val divider: Int, val contrastEnforced: Boolean,
        val lightIconsBackground: Boolean, val visible: Boolean
    )
    private var playerFullscreen = false
    private var previousNavigation: NavigationAppearance? = null

    fun setPlayerFullscreen(enabled: Boolean) {
        if (playerFullscreen == enabled) return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            previousNavigation = NavigationAppearance(
                window.navigationBarColor,
                if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor else 0,
                if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else false,
                controller.isAppearanceLightNavigationBars,
                androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
            )
        }
        playerFullscreen = enabled
        if (!enabled) {
            previousNavigation?.let { previous ->
                window.navigationBarColor = previous.color
                if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = previous.divider
                if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = previous.contrastEnforced
                controller.isAppearanceLightNavigationBars = previous.lightIconsBackground
                if (previous.visible) controller.show(WindowInsetsCompat.Type.navigationBars())
                else controller.hide(WindowInsetsCompat.Type.navigationBars())
            }
            previousNavigation = null
        }
        hideStatusBar()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && playerFullscreen) hideStatusBar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCtx.ctx = applicationContext
        // crash log: capture uncaught exceptions to Downloads for debugging
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val log = "FlowPlay crash ${java.util.Date()}\nThread: ${t.name}\n\n$sw"
                val resolver = applicationContext.contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "flowplay_crash_${System.currentTimeMillis()}.txt")
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(log.toByteArray()) }
                }
            } catch (_: Exception) {}
            prevHandler?.uncaughtException(t, e)
        }
        // restore the saved accent before first composition (no flash)
        try {
            val saved = Store(applicationContext).accent()
            AccentName = if (ACCENTS.containsKey(saved)) saved else "crimson"
        } catch (_: Exception) { AccentName = "crimson" }
        // restore the saved content view mode (grid is the FlowPlay default)
        try {
            val vm = Store(applicationContext).viewMode()
            ViewMode = if (vm == "list") "list" else "grid"
        } catch (_: Exception) { ViewMode = "grid" }
        val um = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        DzTv.isTv = um.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        if (DzTv.isTv) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        hideStatusBar()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Bg, surface = Card,
                    primary = Accent, onBackground = Ink, onSurface = Ink
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    Box(
                        Modifier.fillMaxSize().background(Bg)
                    ) { DzApp() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar()
    }

    private fun hideStatusBar() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (playerFullscreen) {
                    // Swipe can reveal the system bars temporarily over the edge-to-edge player.
                    window.navigationBarColor = android.graphics.Color.BLACK
                    if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = android.graphics.Color.BLACK
                    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
                    isAppearanceLightNavigationBars = false
                    hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    hide(WindowInsetsCompat.Type.statusBars())
                }
            }
        } catch (_: Exception) {}
    }
}

private data class NavItem(val label: String, val icon: String)

// ---------- app root ----------
@Composable
private fun DzApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(DzUi()) }
    var nav by remember { mutableIntStateOf(0) } // 0 home, 1 live, 2 movies, 3 series, 4 settings
    var playing by remember { mutableStateOf<Channel?>(null) }
    var mini by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var refreshingAccounts by remember { mutableStateOf(setOf<String>()) }
    val reloading = ui.account?.id in refreshingAccounts
    var uiGeneration by remember { mutableIntStateOf(0) }
    var selectedSeries by remember { mutableStateOf<Channel?>(null) }
    var selectedCatalog by remember { mutableStateOf<Channel?>(null) }
    val items = listOf(
        NavItem("Home", "home"),
        NavItem("Live TV", "live"),
        NavItem("Movies", "film"),
        NavItem("Series", "series"),
        NavItem("Settings", "settings")
    )

    fun load() {
        val generation = ++uiGeneration
        scope.launch(Dispatchers.IO) {
            val cached = loadDz()
            withContext(Dispatchers.Main) {
                // A late cache read must never overwrite a newer network result.
                if (generation == uiGeneration) ui = cached
            }
        }
    }
    LaunchedEffect(refreshTick) { load() }

    fun reload() {
        val accId = ui.account?.id ?: return
        if (accId in refreshingAccounts) return
        refreshingAccounts = refreshingAccounts + accId
        uiGeneration++
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val acc = Store(ctx).account(accId) ?: error("Provider no longer exists")
                    val response = CompletableDeferred<FreshPlaylist>()
                    ChannelRepo.load(ctx, acc, object : ChannelRepo.Callback {
                        override fun onResult(
                            live: List<Channel>, vod: List<Channel>,
                            series: List<Channel>, err: String?
                        ) {
                            // Callback runs on main. Disk work stays on Dispatchers.IO.
                            response.complete(FreshPlaylist(live, vod, series, err))
                        }
                    })
                    val fresh = response.await()
                    val previous = if (fresh.error != null) PlaylistCache.load(ctx.filesDir, accId) else null
                    val live = if (fresh.error != null && fresh.live.isEmpty()) previous?.live ?: fresh.live else fresh.live
                    val vod = if (fresh.error != null && fresh.vod.isEmpty()) previous?.vod ?: fresh.vod else fresh.vod
                    val series = if (fresh.error != null && fresh.series.isEmpty()) previous?.series ?: fresh.series else fresh.series
                    val hasFresh = fresh.live.isNotEmpty() || fresh.vod.isNotEmpty() || fresh.series.isNotEmpty()
                    val saved = if (fresh.error == null || hasFresh)
                        PlaylistCache.saveChecked(ctx.filesDir, accId, live, vod, series) else true
                    val warning = listOfNotNull(fresh.error,
                        if (!saved) "Latest data loaded, but offline cache could not be saved" else null)
                        .joinToString("; ").ifEmpty { null }
                    ProviderRefreshResult(playlistUi(ctx, acc, live, vod, series, warning),
                        fresh.error == null && saved)
                }
                if (selectedAccount(ctx)?.id == accId && ui.account?.id == accId) {
                    uiGeneration++
                    // Render network data directly, not an unchecked disk re-read.
                    ui = result.ui
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (selectedAccount(ctx)?.id == accId && ui.account?.id == accId) {
                    uiGeneration++
                    ui = ui.copy(loading = false, error = t.message ?: "Provider refresh failed")
                }
            } finally {
                refreshingAccounts = refreshingAccounts - accId
            }
        }
    }

    // Cache-only on app open/resume/provider switch. Network playlist refresh is manual.

    fun play(ch: Channel) {
        if (ch.container == "FLOWPLAY_CATEGORY") {
            selectedCatalog = ch
            return
        }
        if (ch.kind == Channel.SERIES &&
            (ch.stalkerPortal.isNotEmpty() || ch.server.isNotEmpty())) {
            selectedSeries = ch
            return
        }
        playing = ch
        mini = false
        if (!DzTv.isTv &&
            ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        ) {
            (ctx as? android.app.Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    fun closePlayer() {
        playing = null
        mini = false
        if (!DzTv.isTv) {
            (ctx as? android.app.Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        when {
            searchOpen -> searchOpen = false
            guideOpen -> guideOpen = false
            playing != null -> closePlayer()
            nav != 0 -> nav = 0
            // on the home tab with nothing open, back exits the app
            else -> (ctx as? android.app.Activity)?.finish()
        }
    }

    val current = playing
    Box(Modifier.fillMaxSize()) {
        if (DzTv.isTv) {
            // TV: top nav bar + content (no more bottom/side bar trips)
            // One FocusRequester per tab so D-pad UP from the content area can
            // always move focus back up to the top nav bar.
            val tabFrs = remember { List(items.size) { FocusRequester() } }
            Column(
                Modifier.fillMaxSize()
                    // TV D-pad: UP that the content couldn't consume (already at the
                    // top of the list) moves focus back to the top nav bar. Bubbling
                    // phase, so in-list UP navigation/scrolling keeps working.
                    .onKeyEvent { e ->
                        if (DzTv.isTv && e.type == KeyEventType.KeyDown &&
                            e.key == Key.DirectionUp
                        ) {
                            try { tabFrs[nav.coerceIn(items.indices)].requestFocus() }
                            catch (_: Exception) {}
                            true
                        } else false
                    }
            ) {
                TvTopBar(
                    items = items,
                    selected = nav,
                    onSelect = { nav = it },
                    tabFrs = tabFrs
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    DzContent(
                        ui = ui, nav = nav,
                        onNav = { nav = it },
                        onPlay = ::play,
                        onReload = ::reload,
                        reloading = reloading,
                        onRefresh = { refreshTick++ },
                        onSearch = { searchOpen = true },
                        onContentFocus = {}
                    )
                }
            }
        } else {
            // Mobile: content + bottom nav
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    DzContent(
                        ui = ui, nav = nav,
                        onNav = { nav = it },
                        onPlay = ::play,
                        onReload = ::reload,
                        reloading = reloading,
                        onRefresh = { refreshTick++ },
                        onSearch = { searchOpen = true },
                        onContentFocus = {}
                    )
                }
                MobileBottomNav(items = items, selected = nav, onSelect = { nav = it })
            }
        }

        // fullscreen player above everything
        if (current != null) {
            Box(
                modifier = if (mini)
                    Modifier.align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 96.dp)
                        .size(200.dp, 112.dp)
                else Modifier.fillMaxSize()
            ) {
                key(current.url + current.name) {
                    PlayerScreen(
                        channels = ui.live + ui.vod + ui.series,
                        initial = current,
                        accountId = ui.account?.id ?: "",
                        mini = mini,
                        onChannelSelected = { playing = it },
                        onClose = { closePlayer() },
                        onMinimize = { mini = true },
                        onExpand = { mini = false },
                        onOpenGuide = { guideOpen = true },
                        onOpenMultiview = {}
                    )
                }
            }
        }
        if (guideOpen) {
            Box(Modifier.fillMaxSize()) {
                GuideScreen(
                    accountId = ui.account?.id ?: "",
                    channels = ui.live,
                    onBack = { guideOpen = false },
                    onPlay = { ch -> playing = ch; guideOpen = false },
                    playerOverlay = current != null && !mini
                )
            }
        }
        // search overlay above everything except the player
        if (searchOpen && current == null) {
            FlowSearchOverlay(
                ui = ui,
                onPlay = { ch -> searchOpen = false; play(ch) },
                onClose = { searchOpen = false }
            )
        }
    }
    selectedCatalog?.let { folder ->
        PortalCatalogDialog(
            folder = folder,
            onDismiss = { selectedCatalog = null },
            onPlay = { entry -> selectedCatalog = null; play(entry) }
        )
    }
    selectedSeries?.let { series ->
        SeriesEpisodeDialog(
            series = series,
            onDismiss = { selectedSeries = null },
            onPlay = { episode -> selectedSeries = null; play(episode) }
        )
    }
}

/** Only one provider page is fetched per request; a giant catalog stays on the server. */
@Composable
private fun PortalCatalogDialog(
    folder: Channel,
    onDismiss: () -> Unit,
    onPlay: (Channel) -> Unit
) {
    var page by remember(folder.key) { mutableIntStateOf(1) }
    var retry by remember(folder.key) { mutableIntStateOf(0) }
    var loading by remember(folder.key) { mutableStateOf(true) }
    var entries by remember(folder.key) { mutableStateOf(emptyList<Channel>()) }
    var hasMore by remember(folder.key) { mutableStateOf(false) }
    var total by remember(folder.key) { mutableIntStateOf(0) }
    var error by remember(folder.key) { mutableStateOf("") }
    var query by remember(folder.key) { mutableStateOf("") }
    var search by remember(folder.key) { mutableStateOf("") }
    LaunchedEffect(folder.key, page, search, retry) {
        loading = true
        error = ""
        try {
            val result = withContext(Dispatchers.IO) {
                com.zynelabs.flowplay.data.StalkerClient.catalogPage(folder, page, search)
            }
            val previous = if (page == 1) emptyList() else entries
            val merged = (previous + result.items).distinctBy { it.key }
            hasMore = result.hasMore && result.items.isNotEmpty() &&
                (page == 1 || merged.size > previous.size)
            entries = merged
            total = result.total
        } catch (e: Exception) {
            error = "Could not load this page. Please try again."
        } finally { loading = false }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folder.name, color = Ink, maxLines = 2) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth().background(Bg, RoundedCornerShape(8.dp)).padding(12.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search this category…", color = Dim, fontSize = 14.sp)
                        inner()
                    }
                )
                GlassTextButton(onClick = {
                    entries = emptyList(); page = 1; search = query.trim(); retry++
                }, enabled = !loading) { Text("Search", color = Accent) }
                Text(
                    if (total > 0) "${entries.size} loaded · $total reported by portal" else "${entries.size} loaded",
                    color = Dim, fontSize = 12.sp
                )
                LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                    items(entries, key = { it.key }) { entry ->
                        Text(entry.name, color = Ink, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPlay(entry) }.padding(vertical = 13.dp))
                    }
                    if (loading) item { CircularProgressIndicator(color = Accent, modifier = Modifier.padding(12.dp)) }
                    if (error.isNotEmpty()) item {
                        Text(error, color = Dim, fontSize = 13.sp)
                        GlassTextButton(onClick = { retry++ }) { Text("Retry", color = Accent) }
                    }
                    if (!loading && error.isEmpty() && entries.isEmpty()) item {
                        Text("No titles returned in this category.", color = Dim, modifier = Modifier.padding(12.dp))
                    }
                    if (!loading && error.isEmpty() && hasMore) item {
                        GlassTextButton(onClick = { page++ }) { Text("Load more", color = Accent) }
                    }
                }
            }
        },
        confirmButton = { GlassTextButton(onClick = onDismiss) { Text("Close", color = Accent) } },
        containerColor = Card
    )
}

/** Catalog entries are folders, not streams. Resolve only the selected episode. */
@Composable
private fun SeriesEpisodeDialog(
    series: Channel,
    onDismiss: () -> Unit,
    onPlay: (Channel) -> Unit
) {
    var loading by remember(series.key) { mutableStateOf(true) }
    var episodes by remember(series.key) { mutableStateOf(emptyList<Channel>()) }
    var error by remember(series.key) { mutableStateOf("") }
    LaunchedEffect(series.key) {
        try {
            episodes = withContext(Dispatchers.IO) {
                if (series.stalkerPortal.isNotEmpty()) {
                    com.zynelabs.flowplay.data.StalkerClient.episodeChannels(series)
                } else {
                    com.zynelabs.flowplay.data.XtreamClient.episodes(series).mapIndexed { i, ep ->
                        Channel().apply {
                            kind = Channel.VOD
                            key = "${series.key}_episode_$i"
                            name = ep.label
                            url = ep.url
                            logo = series.logo
                            group = series.group
                        }
                    }
                }
            }
            if (episodes.isEmpty()) error = "No episodes returned by this portal."
        } catch (e: Exception) {
            error = "Could not load episodes. Try Reload."
        } finally { loading = false }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(series.name, color = Ink, maxLines = 2) },
        text = {
            when {
                loading -> CircularProgressIndicator(color = Accent)
                error.isNotEmpty() -> Text(error, color = Dim)
                else -> LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(episodes, key = { it.key }) { ep ->
                        Text(
                            ep.name, color = Ink, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onPlay(ep) }.padding(14.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            GlassTextButton(onClick = onDismiss) {
                Text("Close", color = Accent)
            }
        },
        containerColor = Card
    )
}

// ---------- TV top navigation bar ----------
@Composable
private fun TvTopBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tabFrs: List<FocusRequester>
) {
    // brand + tabs in one row: D-pad left/right across tabs,
    // down into content, up back to the tabs
    LaunchedEffect(Unit) {
        delay(400)
        try { tabFrs.getOrNull(0)?.requestFocus() } catch (_: Exception) {}
    }
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF101016))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "FlowPlay", color = Accent, fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = Shadow(Accent, Offset(0f, 0f), 14f)),
            maxLines = 1
        )
        Spacer(Modifier.width(32.dp))
        items.forEachIndexed { i, item ->
            val isSel = selected == i
            // TV focus tracked locally: the old chain had .clip() BEFORE
            // .tvFocus(), which clipped the focus border away and left the
            // top bar with no visible focus indicator. This draws an
            // unclipped white ring + light tint so focused tabs are clearly
            // visible and distinct from the crimson selected pill.
            var tabFocused by remember { mutableStateOf(false) }
            val tvFocused = DzTv.isTv && tabFocused
            Box(
                Modifier
                    .glassCapsule(selected = isSel)
                    .then(
                        tabFrs.getOrNull(i)?.let { Modifier.focusRequester(it) }
                            ?: Modifier
                    )
                    .focusable()
                    .onFocusChanged { tabFocused = it.isFocused }
                    .graphicsLayer {
                        val s = if (tvFocused) 1.08f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.label,
                    color = when {
                        tvFocused -> Color.White
                        isSel -> Accent
                        else -> Dim
                    },
                    fontSize = 16.sp,
                    fontWeight = if (isSel) FontWeight.Bold
                    else FontWeight.Medium,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

// ---------- mobile bottom navigation ----------
@Composable
private fun MobileBottomNav(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    GlassContent {
    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFF101016))
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEachIndexed { i, item ->
            val isSel = selected == i
            Column(
                Modifier.weight(1f).padding(horizontal = 3.dp)
                    .glassCapsule(selected = isSel)
                    .clickable { onSelect(i) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DzIcon(
                    item.icon,
                    color = if (isSel) Accent else Dim,
                    iconSize = 24.dp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.label,
                    color = if (isSel) Accent else Dim,
                    fontSize = 11.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }

    }
}

@Composable
private fun FlowIconBtn(kind: String, onClick: () -> Unit) {
    GlassContent {
        Box(Modifier.size(48.dp).tvFocus(corner = 18.dp).clickable { onClick() }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(34.dp).glassCapsule(), contentAlignment = Alignment.Center) {
                DzIcon(kind, Ink, 20.dp)
            }
        }
    }
}

/** Full-screen search over all loaded content. */
@Composable
private fun FlowSearchOverlay(
    ui: DzUi,
    onPlay: (Channel) -> Unit,
    onClose: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    val results = remember(q, ui) {
        val all = (ui.live + ui.vod + ui.series).filterNot { it.isAdult() }
        val t = q.trim()
        if (t.length < 2) emptyList()
        else all.filter { it.name.contains(t, ignoreCase = true) }.take(60)
    }
    Box(Modifier.fillMaxSize().background(Color(0xF50B0B10))) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(CardSel)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    BasicTextField(
                        value = q,
                        onValueChange = { q = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (q.isEmpty()) Text(
                                    "Search movies, series, channels…",
                                    color = Dim, fontSize = 16.sp
                                )
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.width(4.dp))
                FlowIconBtn("close") { onClose() }
            }
            Spacer(Modifier.height(12.dp))
            if (q.trim().length >= 2) {
                Text(
                    "${results.size} result${if (results.size == 1) "" else "s"}",
                    color = Dim, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.url + it.name }) { ch ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Card).clickable { onPlay(ch) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(72.dp, 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardSel),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ch.kind == Channel.VOD || ch.kind == Channel.SERIES)
                                MediaArtwork(ch, Modifier.fillMaxSize(), compact = true)
                            else if (ch.logo.isNotEmpty()) DzImage(
                                ch.logo, Modifier.fillMaxSize(),
                                fit = ch.kind == Channel.LIVE
                            )
                            else DzIcon("play", Dim, 20.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.name, color = Ink, fontSize = 15.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                (when (ch.kind) {
                                    Channel.VOD -> "Movie"
                                    Channel.SERIES -> "Series"
                                    else -> "Live TV"
                                }) + " • " + ch.displayGroup(),
                                color = Dim, fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (ch.kind == Channel.LIVE) Text(
                            "● LIVE", color = Accent,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ---------- dashboard content ----------
@Composable
private fun DzContent(
    ui: DzUi,
    nav: Int,
    onNav: (Int) -> Unit,
    onPlay: (Channel) -> Unit,
    onReload: () -> Unit,
    reloading: Boolean,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onContentFocus: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Provider switcher opened from the media section header
    var showSwitch by remember { mutableStateOf(false) }
    var switchAccounts by remember { mutableStateOf(listOf<PlAccount>()) }
    var switchActive by remember { mutableStateOf("") }
    var switchCounts by remember { mutableStateOf<Map<String, PlaylistCache.Counts>>(emptyMap()) }

    fun openSwitch() {
        scope.launch(Dispatchers.IO) {
            val s = Store(ctx)
            val list = try { s.enabledAccounts() } catch (_: Exception) { emptyList() }
            val aid = try { s.activeAccountId() } catch (_: Exception) { "" }
            val counts = list.mapNotNull { acc ->
                PlaylistCache.readCounts(ctx.filesDir, acc.id)?.let { acc.id to it }
            }.toMap()
            withContext(Dispatchers.Main) {
                switchAccounts = list
                switchActive = aid
                switchCounts = counts
                showSwitch = true
            }
        }
    }

    fun switchTo(id: String) {
        showSwitch = false
        scope.launch(Dispatchers.IO) {
            try { Store(ctx).setActiveAccountId(id) } catch (_: Exception) {}
            withContext(Dispatchers.Main) { onNav(0); onRefresh() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Top toolbar: brand, manual Reload and Search only.
        val compactHeader = LocalConfiguration.current.screenWidthDp < 380
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!DzTv.isTv) {
                Text(
                    "FLOWPLAY", color = Accent,
                    fontSize = if (compactHeader) 17.sp else 20.sp, fontWeight = FontWeight.Black,
                    style = TextStyle(
                        shadow = Shadow(Accent, Offset(0f, 0f), 14f),
                        letterSpacing = if (compactHeader) 1.sp else 2.sp
                    )
                )
                Spacer(Modifier.width(if (compactHeader) 6.dp else 12.dp))
            }
            Spacer(Modifier.weight(1f))
            if (reloading) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent,
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            } else {
                Box(Modifier.height(48.dp).widthIn(min = 48.dp).tvFocus(corner = 16.dp).clickable { onReload() },
                    contentAlignment = Alignment.Center) {
                    GlassContent {
                        Box(Modifier.height(32.dp).glassCapsule().padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center) {
                            Text("Reload", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            FlowIconBtn("search") { onSearch() }
        }
        // quick provider switch dialog
        if (showSwitch) {
            val switchFr = remember { FocusRequester() }
            LaunchedEffect(showSwitch) {
                try { switchFr.requestFocus() } catch (_: Exception) {}
            }
            AlertDialog(
                onDismissRequest = { showSwitch = false },
                title = { Text("Switch provider", color = Ink) },
                text = {
                    if (switchAccounts.isEmpty()) {
                        Text("No providers yet.", color = Dim, fontSize = 14.sp)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(
                                switchAccounts,
                                key = { _, acc -> acc.id }
                                ) { index, acc ->
                                val cachedCount = switchCounts[acc.id]?.live
                                val liveCount = cachedCount ?: if (acc.id == ui.account?.id && ui.live.isNotEmpty()) ui.live.size else -1
                                Row(
                                    Modifier.fillMaxWidth()
                                        .glassCapsule(selected = acc.id == switchActive, corner = 20.dp)
                                        .then(
                                            if (index == 0) Modifier.focusRequester(switchFr)
                                            else Modifier
                                        )
                                        .tvFocus(corner = 8.dp)
                                        .focusable()
                                        .clickable { switchTo(acc.id) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            acc.name.ifEmpty { "Playlist" },
                                            color = Ink, fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            providerCountLabel(liveCount) + "\n" +
                                                providerExpiryLabel(acc.expDate),
                                            color = Dim, fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (acc.id == switchActive) Text(
                                        "✓", color = Accent, fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                containerColor = Card
            )
        }
        if (ui.error != null && (ui.live.isNotEmpty() || ui.vod.isNotEmpty() || ui.series.isNotEmpty())) {
            Text(
                "Refresh incomplete: ${ui.error} — showing available/saved data. Tap Reload to retry.",
                color = Accent, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        when {
            ui.loading -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Accent) }
            // Settings must open even with no provider, otherwise the
            // "Add Provider" button appears to do nothing.
            nav == 4 -> DzSettings(onRefresh = { onRefresh() })
            nav == 5 -> DzSettings(
                onRefresh = { onRefresh() },
                startAt = "providers"
            )
            ui.account == null -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DzIcon("live", Dim, 56.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("No provider yet", color = Ink, fontSize = 17.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add your IPTV provider to start watching",
                        color = Dim, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.heightIn(min = 48.dp).glassCapsule(selected = true)
                            .tvFocus(corner = 12.dp)
                            .focusable()
                            .clickable { onNav(5) }
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Add Provider", color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            ui.error != null && ui.live.isEmpty() && ui.vod.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.error!!, color = Ink, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.heightIn(min = 48.dp).glassCapsule(selected = true)
                            .clickable { onReload() }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Reload", color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            else -> when (nav) {
                0 -> DzHome(ui, onPlay, onContentFocus)
                1 -> MediaGrid(
                    "Live TV", ui.live, onPlay, onContentFocus,
                    providerTitle = ui.title, onProviderSwitch = { openSwitch() },
                    live = true, showCountries = true
                )
                2 -> MediaGrid("Movies", ui.vod, onPlay, onContentFocus,
                    providerTitle = ui.title, onProviderSwitch = { openSwitch() })
                3 -> MediaGrid("Series", ui.series, onPlay, onContentFocus,
                    providerTitle = ui.title, onProviderSwitch = { openSwitch() })
                else -> AccountsScreen(
                    onBack = { onNav(0) },
                    onChanged = { onRefresh() }
                )
            }
        }
    }
}

/** True when the smart category engine flags this as adult content. */
private fun Channel.isAdult(): Boolean = getSmartCat() == Cats.ADULT

@Composable
private fun DzHome(
    ui: DzUi,
    onPlay: (Channel) -> Unit,
    onContentFocus: () -> Unit
) {
    val ctx = LocalContext.current
    // Featured: first available movie, else first live channel (never adult).
    val hero = remember(ui) {
        val sfwVod = ui.vod.filterNot { it.isAdult() || it.container == "FLOWPLAY_CATEGORY" }
        val sfwLive = ui.live.filterNot { it.isAdult() }
        (sfwVod.firstOrNull { it.logo.isNotEmpty() } ?: sfwVod.firstOrNull())
            ?: (sfwLive.firstOrNull { it.logo.isNotEmpty() } ?: sfwLive.firstOrNull())
    }
    // Home never shows adult content — it lives under its own chip in the tabs.
    val homeLive = remember(ui) { ui.live.filterNot { it.isAdult() } }
    val homeVod = remember(ui) { ui.vod.filterNot { it.isAdult() } }
    val homeSeries = remember(ui) { ui.series.filterNot { it.isAdult() } }
    val recent = remember(ui) {
        try {
            val keys = Store(ctx).recent(ui.account?.id ?: "")
            val all = homeLive + homeVod + homeSeries
            keys.mapNotNull { k -> all.find { it.url == k } }
                .distinctBy { it.url }.take(20)
        } catch (_: Exception) { emptyList() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hero != null) item(key = "hero") {
            DzHero(hero, onPlay, onContentFocus)
        }
        if (recent.isNotEmpty()) item(key = "recent") {
            DzRow("Continue Watching", recent, onPlay, onContentFocus)
        }
        item(key = "live") {
            DzRow("Live Channels", homeLive.take(40), onPlay, onContentFocus, live = true)
        }
        if (homeVod.isNotEmpty()) item(key = "movies") {
            DzRow(if (homeVod.any { it.container == "FLOWPLAY_CATEGORY" }) "Movie Categories" else "Trending Movies",
                homeVod.take(40), onPlay, onContentFocus)
        }
        if (homeSeries.isNotEmpty()) item(key = "series") {
            DzRow(if (homeSeries.any { it.container == "FLOWPLAY_CATEGORY" }) "Series Categories" else "Popular Series",
                homeSeries.take(40), onPlay, onContentFocus)
        }
        item(key = "pad") { Spacer(Modifier.height(20.dp)) }
    }
}

/** Hero banner with bottom gradient fade (vignette). */
@Composable
private fun DzHero(
    ch: Channel,
    onPlay: (Channel) -> Unit,
    onContentFocus: () -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    val kindLabel = when (ch.kind) {
        Channel.VOD -> "Movie"
        Channel.SERIES -> "Series"
        else -> "Live TV"
    }
    Box(
        Modifier.fillMaxWidth()
            .height(if (DzTv.isTv) 296.dp else 184.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardSel)
            .tvFocus(corner = 16.dp, onFocused = { if (it) onContentFocus() })
            .focusable()
            .clickable { onPlay(ch) }
    ) {
        if (ch.kind == Channel.VOD || ch.kind == Channel.SERIES)
            MediaArtwork(ch, Modifier.fillMaxSize())
        else if (ch.logo.isNotEmpty()) DzImage(ch.logo, Modifier.fillMaxSize())
        // vignette: smooth bottom fade into the background
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xE60B0B10))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xB30B0B10), Color.Transparent)
                )
            )
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            Column(Modifier.padding(16.dp).fillMaxWidth(0.85f)) {
                Text(
                    "TRENDING NOW", color = Accent,
                    fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    ch.name, color = Color.White,
                    fontSize = if (DzTv.isTv) 26.sp else 20.sp,
                    lineHeight = if (DzTv.isTv) 30.sp else 24.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$kindLabel • ${ch.displayGroup()}",
                    color = Color(0xFFB9B9C9),
                    fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.heightIn(min = 48.dp).glassCapsule(selected = true)
                            .clickable { onPlay(ch) }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassContent {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DzIcon("play", Color.White, 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Play", color = Color.White,
                                fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    // glassmorphism secondary button
                    Box(
                        Modifier.heightIn(min = 48.dp).glassCapsule()
                            .clickable { showInfo = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassContent {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DzIcon("info", Color.White, 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Info", color = Color.White,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium
                            )
                        }
                        }
                    }
                }
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(ch.name, color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Type: $kindLabel", color = Dim, fontSize = 14.sp)
                    Text(
                        "Group: ${ch.displayGroup()}",
                        color = Dim, fontSize = 14.sp
                    )
                    if (ch.kind == Channel.LIVE && ch.archive) Text(
                        "Catch-up available", color = Accent, fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Text(
                    "Play", color = Accent, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 48.dp).glassCapsule()
                        .clickable { showInfo = false; onPlay(ch) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            },
            dismissButton = {
                Text(
                    "Close", color = Dim, fontSize = 15.sp,
                    modifier = Modifier.heightIn(min = 48.dp).glassCapsule()
                        .clickable { showInfo = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            },
            containerColor = Card
        )
    }
}

/** Horizontal content row. */
@Composable
private fun DzRow(
    title: String,
    items: List<Channel>,
    onPlay: (Channel) -> Unit,
    onContentFocus: () -> Unit,
    live: Boolean = false
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            title, color = Ink, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
        ) {
            items(items, key = { it.url + it.name }) { ch ->
                DzCard(
                    ch,
                    w = if (live) 96.dp else if (ch.container == "FLOWPLAY_CATEGORY") 104.dp else 112.dp,
                    h = if (live || ch.container == "FLOWPLAY_CATEGORY") 96.dp else 156.dp,
                    live = live,
                    onPlay = onPlay,
                    onContentFocus = onContentFocus
                )
            }
        }
    }
}

/** Poster/logo card with TV focus treatment. */
@Composable
private fun DzCard(
    ch: Channel,
    w: Dp,
    h: Dp,
    live: Boolean,
    onPlay: (Channel) -> Unit,
    onContentFocus: () -> Unit
) {
    Column(Modifier.width(w)) {
        Box(
            Modifier.size(w, h)
                .clip(RoundedCornerShape(12.dp))
                .background(Card)
                .tvFocus(corner = 12.dp, onFocused = { if (it) onContentFocus() })
                .focusable()
                .clickable { onPlay(ch) },
            contentAlignment = Alignment.Center
        ) {
            if (!live && ch.container == "FLOWPLAY_CATEGORY")
                CategoryFolderArtwork(ch, Modifier.fillMaxSize())
            else if (!live) MediaArtwork(ch, Modifier.fillMaxSize())
            else if (ch.logo.isNotEmpty()) DzImage(ch.logo, Modifier.fillMaxSize(), fit = live)
            else Text(
                if (ch.container == "FLOWPLAY_CATEGORY") "FOLDER" else ch.name.take(2).uppercase(), color = Dim,
                fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            if (live) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "● LIVE", color = Accent,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            ch.name, color = Ink, fontSize = if (ch.container == "FLOWPLAY_CATEGORY") 11.sp else 12.sp, lineHeight = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Full grid for the Live/Movies/Series tabs. */
/** Small filter chip used by the media tabs. */
@Composable
private fun DzChip(
    label: String,
    selected: Boolean,
    onContentFocus: () -> Unit,
    onClick: () -> Unit
) {
    GlassContent {
    Box(
        Modifier.heightIn(min = if (DzTv.isTv) 48.dp else 44.dp).glassCapsule(selected = selected)
            .tvFocus(corner = 20.dp, onFocused = { if (it) onContentFocus() })
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        val display = categoryDisplayName(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(
                display.substringBefore(" · "),
                if (selected) Color.White else Dim,
                14.dp
            )
            Spacer(Modifier.width(4.dp))
            Text(
                display,
                color = if (selected) Color.White else Dim,
                fontSize = if (DzTv.isTv) 12.sp else 11.sp, fontWeight = FontWeight.Medium, maxLines = 1
            )
        }
    }

    }
}

@Composable
private fun ViewModeToggle() {
    GlassContent {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        Box(
            Modifier.width(if (DzTv.isTv) 56.dp else 52.dp).height(48.dp)
                .tvFocus(corner = 16.dp)
                .focusable()
                .clickable {
                    ViewMode = if (ViewMode == "grid") "list" else "grid"
                    val m = ViewMode
                    scope.launch(Dispatchers.IO) {
                        try { Store(ctx).setViewMode(m) } catch (_: Exception) {}
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(Modifier.width(if (DzTv.isTv) 52.dp else 48.dp)
                .height(if (DzTv.isTv) 32.dp else 28.dp).glassCapsule()
                .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                DzGlyph(if (ViewMode == "grid") "\u25A6" else "\u2630", Accent,
                    if (DzTv.isTv) 14.dp else 12.dp)
                Spacer(Modifier.width(3.dp))
                Text(if (ViewMode == "grid") "Grid" else "List", color = Ink,
                    fontSize = if (DzTv.isTv) 11.sp else 10.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** Equal title/provider shares leave the small Grid/List switch at the right. */
@Composable
private fun MediaSectionHeader(
    title: String,
    providerTitle: String,
    onProviderSwitch: () -> Unit,
    onContentFocus: () -> Unit
) {
    GlassContent {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f).height(if (DzTv.isTv) 36.dp else 32.dp)
                .glassCapsule().padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart) {
                Text(title, color = Ink, fontSize = if (DzTv.isTv) 18.sp else 17.sp,
                    lineHeight = if (DzTv.isTv) 22.sp else 21.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.weight(1f).height(48.dp)
                .tvFocus(corner = 16.dp, onFocused = { if (it) onContentFocus() })
                .focusable().clickable(onClick = onProviderSwitch),
                contentAlignment = Alignment.Center) {
                Row(Modifier.fillMaxWidth().height(if (DzTv.isTv) 36.dp else 32.dp)
                    .glassCapsule().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(providerTitle, color = Ink,
                        fontSize = if (DzTv.isTv) 12.sp else 11.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("▾", color = Accent, fontSize = 10.sp)
                }
            }
            ViewModeToggle()
        }
    }
}

@Composable
private fun MediaGrid(
    title: String,
    items: List<Channel>,
    onPlay: (Channel) -> Unit,
    onContentFocus: () -> Unit,
    providerTitle: String,
    onProviderSwitch: () -> Unit,
    live: Boolean = false,
    showCountries: Boolean = false
) {
    // Category filter: "All" shows everything except adult; adult gets its
    // own separated chip so it never mixes into the normal browsing.
    var selCat by remember(items) { mutableStateOf<String?>(null) }
    var selCountry by remember(items) { mutableStateOf<String?>(null) }
    val cats = remember(items) {
        items.filterNot { it.isAdult() }
            .groupingBy { it.getSmartCat() }.eachCount()
            .entries.sortedByDescending { it.value }
    }
    val countries = remember(items) {
        items.filterNot { it.isAdult() }
            .groupingBy { it.getCountry() }.eachCount()
            .entries.sortedByDescending { it.value }
    }
    val adultCount = remember(items) { items.count { it.isAdult() } }
    val shown = remember(items, selCat, selCountry) {
        val co = selCountry
        when (val c = selCat) {
            null -> if (co != null)
                items.filter { it.getCountry() == co && !it.isAdult() }
            else items.filterNot { it.isAdult() }
            Cats.ADULT -> items.filter { it.isAdult() }
            else -> items.filter { it.getSmartCat() == c }
        }
    }
    Column(Modifier.fillMaxSize()) {
        MediaSectionHeader(title, providerTitle, onProviderSwitch, onContentFocus)
        if (items.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "cat_all") {
                    DzChip(
                        "All", selCat == null && selCountry == null,
                        onContentFocus
                    ) { selCat = null; selCountry = null }
                }
                items(cats, key = { "c_" + it.key }) { (name, count) ->
                    DzChip("$name · $count", selCat == name, onContentFocus) {
                        selCat = if (selCat == name) null else name
                        selCountry = null
                    }
                }
                if (adultCount > 0) item(key = "cat_adult") {
                    DzChip(
                        "${Cats.ADULT} · $adultCount", selCat == Cats.ADULT,
                        onContentFocus
                    ) {
                        selCat = if (selCat == Cats.ADULT) null else Cats.ADULT
                        selCountry = null
                    }
                }
            }
            // country filter row (Live TV only)
            if (showCountries && countries.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = 12.dp, vertical = 3.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(countries, key = { "co_" + it.key }) { (name, count) ->
                        DzChip(
                            "$name · $count", selCountry == name,
                            onContentFocus
                        ) {
                            selCountry =
                                if (selCountry == name) null else name
                            selCat = null
                        }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", color = Dim, fontSize = 15.sp)
            }
        } else if (ViewMode == "list") {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown, key = { it.url + it.name }) { ch ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .tvFocus(corner = 10.dp)
                            .focusable()
                            .clickable { onPlay(ch) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                .background(Card),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!live && ch.container == "FLOWPLAY_CATEGORY")
                                CategoryFolderArtwork(ch, Modifier.fillMaxSize(), compact = true)
                            else if (!live)
                                MediaArtwork(ch, Modifier.fillMaxSize(), compact = true)
                            else if (ch.logo.isNotEmpty())
                                DzImage(ch.logo, Modifier.fillMaxSize().padding(6.dp), fit = true)
                            else if (ch.container == "FLOWPLAY_CATEGORY")
                                DzIcon("folder", Accent, 30.dp)
                            else Text(
                                if (ch.container == "FLOWPLAY_CATEGORY") "FOLDER" else ch.name.take(2).uppercase(), color = Dim,
                                fontSize = 16.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.name, color = Ink, fontSize = if (live) 13.sp else 12.sp, lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            if (live && ch.container != "FLOWPLAY_CATEGORY" && ch.getCountry().isEmpty()) {
                                val category = categoryDisplayName(ch.getSmartCat())
                                GlassContent {
                                    Row(Modifier.glassCapsule(corner = 14.dp)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        CategoryIcon(category, Dim, 14.dp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(category, color = Dim, fontSize = 11.sp,
                                            lineHeight = 14.sp, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            } else {
                                Text(
                                    if (ch.container == "FLOWPLAY_CATEGORY") "Category · open to browse"
                                    else if (live) ch.getCountry()
                                        .ifEmpty { ch.getSmartCat() }
                                    else ch.getSmartCat(),
                                    color = Dim, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            "›", color = Accent, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (DzTv.isTv) 152.dp else 104.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown, key = { it.url + it.name }) { ch ->
                    Column(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier.fillMaxWidth()
                                .then(
                                    if (live) Modifier.height(100.dp)
                                    else Modifier.fillMaxWidth()
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(Card)
                                .tvFocus(
                                    corner = 12.dp,
                                    onFocused = { if (it) onContentFocus() }
                                )
                                .focusable()
                                .clickable { onPlay(ch) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!live) {
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height(if (ch.container == "FLOWPLAY_CATEGORY") 96.dp else 144.dp)
                                ) {
                                    if (ch.container == "FLOWPLAY_CATEGORY")
                                        CategoryFolderArtwork(ch, Modifier.fillMaxSize())
                                    else MediaArtwork(ch, Modifier.fillMaxSize())
                                }
                            } else {
                                if (ch.logo.isNotEmpty())
                                    DzImage(
                                        ch.logo,
                                        Modifier.fillMaxSize().padding(12.dp),
                                        fit = true
                                    )
                                else Text(
                                    if (ch.container == "FLOWPLAY_CATEGORY") "FOLDER" else ch.name.take(2).uppercase(), color = Dim,
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            ch.name, color = Ink, fontSize = if (live) 12.sp else 11.sp, lineHeight = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** Folder fallback: provider image, known service mark, local title poster, launcher PNG. */
@Composable
private fun CategoryFolderArtwork(ch: Channel, modifier: Modifier, compact: Boolean = false) {
    val brand = remember(ch.name) {
        com.zynelabs.flowplay.data.CategoryBrand.forName(ch.name)
    }
    val logo = when (brand) {
        "netflix" -> R.drawable.category_brand_netflix
        "disney_plus" -> R.drawable.category_brand_disney_plus
        "prime_video" -> R.drawable.category_brand_prime_video
        "apple_tv" -> R.drawable.category_brand_apple_tv
        "hbo" -> R.drawable.category_brand_hbo
        "movistar" -> R.drawable.category_brand_movistar
        else -> 0
    }
    if (ch.logo.isNotEmpty()) {
        val context = LocalContext.current
        val fallback = remember(context, logo) {
            if (logo == 0) null else context.getResources().getDrawable(logo, null)
        }
        MediaArtwork(ch, modifier, compact = true, placeholder = fallback)
    } else if (logo != 0) {
        // Preserve the actual logo colors; dark marks need a light backing for contrast.
        val lightBacking = brand == "disney_plus" || brand == "apple_tv" || brand == "hbo"
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(logo),
            contentDescription = when (brand) {
                "netflix" -> "Netflix"
                "disney_plus" -> "Disney+"
                "prime_video" -> "Prime Video"
                "apple_tv" -> "Apple TV+"
                "hbo" -> "HBO"
                "movistar" -> "Movistar"
                else -> ""
            },
            modifier = modifier.padding(if (compact) 4.dp else 12.dp)
                .background(if (lightBacking) Color(0xFFF0F2F6) else Color.Transparent,
                    RoundedCornerShape(8.dp))
                .padding(if (lightBacking) 4.dp else 0.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    } else {
        MediaArtwork(ch, modifier, compact = compact)
    }
}

/** All reusable app icons share the glass visual language. */
@Composable
private fun DzIcon(kind: String, color: Color = Ink, iconSize: Dp = 24.dp) {
    GlassIconSurface { RawDzIcon(kind, color, iconSize) }
}
