package com.zynelabs.flowplay

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.navigationBarsPadding

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.zynelabs.flowplay.GlassText as Text
import androidx.compose.material3.Text as PlainText
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.TextUnit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.zynelabs.flowplay.data.Cats
import com.zynelabs.flowplay.data.Channel
import com.zynelabs.flowplay.data.ChannelRepo
import com.zynelabs.flowplay.data.PlAccount
import com.zynelabs.flowplay.data.PlaylistCache
import com.zynelabs.flowplay.data.StalkerClient
import com.zynelabs.flowplay.data.Store
import com.zynelabs.flowplay.data.VideoScaleModes
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.zynelabs.flowplay.data.StreamRecorder
import com.zynelabs.flowplay.data.XtreamClient
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.zynelabs.flowplay.data.EpgCache

private val CardBg = Color(0xFF141C2E)
private val PlayerFocusRing = Color(0xFFFFCF66)
private val PlayerSecondary = Color(0xFFD3DCEB)
// Accent, Ink, Dim, Card, CardSel, Bg come from MainActivity.kt (internal shared).

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * Full player v3 — ZyneLabs-style UI:
 * transparent top bar, bottom stack (info box + seek row + centered
 * transport cluster), inline settings drawer inside the info box (⚙),
 * translucent channel drawer, hidden D-pad shortcuts, back-press order.
 * The volume/brightness edge-strip UI is intentionally untouched.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    channels: List<Channel>,
    initial: Channel,
    accountId: String,
    mini: Boolean = false,
    onChannelSelected: (Channel) -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit = {},
    onExpand: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenMultiview: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    // Hide Android's system bars only for the expanded player, not mini playback.
    DisposableEffect(activity, mini) {
        val host = activity as? MainActivity
        if (!mini) host?.setPlayerFullscreen(true)
        onDispose { if (!mini) host?.setPlayerFullscreen(false) }
    }
    val scope = rememberCoroutineScope()
    val audioMgr = remember {
        ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var current by remember { mutableStateOf(initial) }
    var showControls by remember { mutableStateOf(true) }
    // ZyneLabs-style: in-player settings live in a compact transparent left-edge drawer opened by the info action.
    var showSettingsOptions by remember { mutableStateOf(false) }
    var showSubDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var extSubName by remember { mutableStateOf<String?>(null) }
    var showChannels by remember { mutableStateOf(false) }
    var showCatchup by remember { mutableStateOf(false) }
    // TV D-pad: true while focus sits inside the controls overlay — the
    // auto-hide timer waits while the user is navigating the buttons.
    var overlayHasFocus by remember { mutableStateOf(false) }
    // TV D-pad: bumped on every D-pad key press; the auto-hide timer
    // restarts from the latest input so the overlay can never get stuck
    // on screen for D-pad-only users.
    var uiTick by remember { mutableIntStateOf(0) }
    // TV D-pad: focus targets for OK (show overlay) and DOWN (open drawer).
    val firstCtrlFr = remember { FocusRequester() }
    val drawerFirstFr = remember { FocusRequester() }
    val settingsFirstFr = remember { FocusRequester() }
    val gearFr = remember { FocusRequester() }
    val recordFr = remember { FocusRequester() }
    val infoFr = remember { FocusRequester() }
    val zoomFr = remember { FocusRequester() }
    var featureReturnFr by remember { mutableStateOf<FocusRequester?>(null) }
    // TV D-pad: focus anchor for the player root. Key events only reach
    // onPreviewKeyEvent while something in the Box holds focus; with every
    // overlay hidden no button is focusable, so the root itself anchors
    // focus — otherwise OK/DOWN/LEFT/RIGHT never arrive and the remote
    // goes dead. Phone/touch is untouched (anchor is TV-only).
    val tvRootFr = remember { FocusRequester() }
    var drawerCat by remember { mutableStateOf<String?>(null) }
    // Drawer can browse other providers without disturbing playback.
    var drawerChannels by remember { mutableStateOf(channels) }
    var drawerAccountId by remember { mutableStateOf(accountId) }
    var drawerAccounts by remember { mutableStateOf<List<PlAccount>>(emptyList()) }
    var drawerLoading by remember { mutableStateOf(false) }
    var showProviderPick by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }

    // player state
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var aspectMode by remember { mutableIntStateOf(0) }
    var videoScale by remember { mutableFloatStateOf(1f) }
    // floating text-only hint for aspect/scale (transparent, no toast box)
    var aspectHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(aspectHint) {
        if (aspectHint != null) {
            delay(1800)
            aspectHint = null
        }
    }
    var showScaleDialog by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<Tracks?>(null) }
    var sleepMin by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var showRecTimer by remember { mutableStateOf(false) }
    var recRemainSec by remember { mutableIntStateOf(0) } // 0 = no timer
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableIntStateOf(audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var catchupProgs by remember { mutableStateOf<List<XtreamClient.Program>?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf("") }
    var muted by remember { mutableStateOf(false) }
    var swipeSide by remember { mutableStateOf<Int?>(null) } // 0=brightness 1=volume
    var swipeSeq by remember { mutableIntStateOf(0) }
    var epgTick by remember { mutableIntStateOf(0) }
    // edge strip auto-hides shortly after the swipe ends
    LaunchedEffect(swipeSeq) {
        if (swipeSeq > 0) {
            delay(2500)
            swipeSide = null
        }
    }
    // manual surface layout (ZyneLabs-style): aspect + scale by sizing
    // the SurfaceView itself — no GPU-layer transforms (they glitch
    // SurfaceViews on some devices).
    var videoW by remember { mutableIntStateOf(0) }
    var videoH by remember { mutableIntStateOf(0) }
    var boxW by remember { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }

    val maxVolume = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val isPortrait =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val topIconSize: Dp = if (DzTv.isTv) 56.dp else 48.dp
    val topIconGap: Dp = if (isPortrait) 4.dp else 8.dp

    // init brightness from window
    LaunchedEffect(Unit) {
        val lp = activity?.window?.attributes
        val b = lp?.screenBrightness
        if (b != null && b >= 0) brightness = b
        isFavorite = try {
            Store(ctx).isFav(accountId, current.name)
        } catch (_: Exception) { false }
        videoScale = try { Store(ctx).videoScale() } catch (_: Exception) { 1f }
    }
    // track for Continue Watching on the home dashboard
    LaunchedEffect(current) {
        try { Store(ctx).addRecent(accountId, current.url) } catch (_: Exception) {}
    }

    // Stalker / MAC portal channels carry no direct URL: the tokenised link is
    // created at play time (off the main thread) and kept in resolvedUrl.
    val isStalker = current.stalkerCmd.isNotEmpty()
    var resolvedUrl by remember(current) { mutableStateOf("") }

    val player = remember(current) {
        ExoPlayer.Builder(ctx).build().apply {
            setVolume(if (muted) 0f else 1f)
            if (!isStalker) {
                setMediaItem(MediaItem.fromUri(current.url))
                prepare()
                play()
            }
        }
    }

    LaunchedEffect(player) {
        if (isStalker) {
            val u = withContext(Dispatchers.IO) { StalkerClient.playUrl(current) }
            if (u.isNotEmpty()) {
                resolvedUrl = u
                player.setMediaItem(MediaItem.fromUri(u))
                player.prepare()
                player.play()
            } else {
                error = StalkerClient.lastError.ifEmpty { "Could not open portal stream" }
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
            }
            override fun onTracksChanged(t: Tracks) {
                tracks = t
                quality = try {
                    val h = t.groups
                        .filter { it.type == C.TRACK_TYPE_VIDEO }
                        .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
                        .maxOrNull() ?: 0
                    when {
                        h >= 2160 -> "4K"
                        h >= 1080 -> "Full HD"
                        h >= 720 -> "HD"
                        h > 0 -> "SD"
                        else -> ""
                    }
                } catch (_: Exception) { "" }
            }
            override fun onVideoSizeChanged(vs: VideoSize) {
                videoW = vs.width
                videoH = vs.height
            }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                error = e.message ?: "Playback error"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            if (isRecording) stopRecording()
            recRemainSec = 0
        }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            if (d > 0) duration = d
            delay(500)
        }
    }

    // Player overlay auto-hide. TV: hides 4s after the LAST input of any
    // kind — every D-pad key bumps uiTick and restarts the timer, so the
    // info bar can never get stuck on screen for D-pad-only users (it used
    // to stay forever because the timer paused while a button had focus,
    // and only a mouse click could dismiss it). Phone: previous behavior
    // unchanged.
    LaunchedEffect(showControls, isPlaying, overlayHasFocus, uiTick,
        showSettingsOptions, showSubDialog, showSleepDialog, showSpeedDialog,
        showAudioDialog, showStatsDialog, showChannels, showCatchup,
        showScaleDialog, showRecTimer, showProviderPick) {
        if (!showControls) return@LaunchedEffect
        fun dialogsOpen() = showSettingsOptions || showSubDialog ||
            showSleepDialog || showSpeedDialog || showAudioDialog ||
            showStatsDialog || showChannels || showCatchup ||
            showScaleDialog || showRecTimer || showProviderPick
        if (DzTv.isTv) {
            if (!isPlaying || dialogsOpen()) return@LaunchedEffect
            delay(4000)
            if (dialogsOpen()) return@LaunchedEffect
            showControls = false
            showSettingsOptions = false // drawer closes with the controls
        } else if (isPlaying && !dialogsOpen() && !overlayHasFocus) {
            delay(5000)
            showControls = false
            showSettingsOptions = false // drawer closes with the controls
        }
    }

    LaunchedEffect(showControls, showChannels, showSettingsOptions, showSubDialog,
        showSleepDialog, showSpeedDialog, showAudioDialog, showStatsDialog,
        showCatchup, showScaleDialog, showRecTimer, showProviderPick) {
        // TV D-pad: whenever every overlay is hidden, park focus on the
        // player root so the preview key handler keeps receiving
        // OK/DOWN/LEFT/RIGHT. Without this nothing is focusable, key events
        // are dropped, and the remote stops working. Phone/touch untouched.
        if (!DzTv.isTv || mini || locked) return@LaunchedEffect
        if (!showControls && !showChannels && !showSettingsOptions && !showSubDialog &&
            !showSleepDialog && !showSpeedDialog && !showAudioDialog &&
            !showStatsDialog && !showCatchup && !showScaleDialog && !showRecTimer &&
            !showProviderPick) {
            try { tvRootFr.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(showSettingsOptions) {
        if (!DzTv.isTv || mini || locked || !showControls) return@LaunchedEffect
        delay(120)
        try {
            if (showSettingsOptions) settingsFirstFr.requestFocus()
            else gearFr.requestFocus()
        } catch (_: Exception) {}
    }

    val featureDialogOpen = showSubDialog || showSleepDialog || showSpeedDialog ||
        showAudioDialog || showStatsDialog || showScaleDialog || showRecTimer || showCatchup
    var featureWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(featureDialogOpen) {
        val restore = featureWasOpen && !featureDialogOpen
        featureWasOpen = featureDialogOpen
        if (restore && DzTv.isTv && !mini && !locked && showControls) {
            delay(120)
            try { (featureReturnFr ?: gearFr).requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(sleepMin) {
        if (sleepMin > 0) {
            delay(sleepMin * 60_000L)
            player.pause()
            sleepMin = 0
        }
    }

    // recording timer: counts down each second, auto-stops at zero
    LaunchedEffect(isRecording) {
        while (isRecording && recRemainSec > 0) {
            delay(1000)
            recRemainSec--
            if (recRemainSec <= 0) {
                stopRecording()
                isRecording = false
            }
        }
    }

    // Live = channel kind is LIVE, or no seekable duration reported.
    // (Some live HLS streams report a DVR window as duration, so kind is
    // the authoritative signal.) Seek bar / seek buttons show for VOD only.
    val isLive = duration <= 0L || current.kind == Channel.LIVE

    fun setBrightness(v: Float) {
        brightness = v.coerceIn(0.05f, 1f)
        activity?.window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = brightness
            w.attributes = lp
        }
    }

    fun setVolume(v: Int) {
        volume = v.coerceIn(0, maxVolume)
        audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    fun cycleAspect() {
        aspectMode = (aspectMode + 1) % VideoScaleModes.COUNT
        aspectHint = "Aspect: " + VideoScaleModes.label(aspectMode) + "  •  Scale: " +
            Math.round(videoScale * 100) + "% (Info box → Zoom)"
    }

    fun setVideoScale(v: Float) {
        videoScale = v
        try { Store(ctx).setVideoScale(v) } catch (_: Exception) {}
        aspectHint = "Scale: " + Math.round(v * 100) + "%"
    }

    fun cycleSpeed() {
        val i = SPEEDS.indexOf(speed)
        val n = SPEEDS[(i + 1) % SPEEDS.size]
        speed = n
        player.setPlaybackSpeed(n)
    }

    fun seekBy(ms: Long) {
        try {
            val d = player.duration
            val np = (player.currentPosition + ms)
                .coerceIn(0L, if (d > 0) d else Long.MAX_VALUE)
            player.seekTo(np)
        } catch (_: Exception) {}
    }

    fun enterPip(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                activity?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9)).build()
                ) == true
            } catch (_: Exception) { false }
        } else false
    }

    fun switchChannel(ch: Channel) {
        showChannels = false
        current = ch
        onChannelSelected(ch)
        // reset per-channel state
        position = 0; duration = 0; error = null
        speed = 1f; isBuffering = true; quality = ""
        epgTick++ // re-read schedule for the new channel
        scope.launch {
            isFavorite = try { Store(ctx).isFav(drawerAccountId, ch.name) }
            catch (_: Exception) { false }
        }
    }

    /** Load another provider's channels into the drawer (playback untouched). */
    fun loadDrawerProvider(acc: PlAccount) {
        drawerAccountId = acc.id
        drawerCat = null
        drawerLoading = true
        scope.launch(Dispatchers.IO) {
            var live: List<Channel> = emptyList()
            var vod: List<Channel> = emptyList()
            var series: List<Channel> = emptyList()
            try {
                PlaylistCache.load(ctx.filesDir, acc.id)?.let {
                    live = it.live; vod = it.vod; series = it.series
                }
            } catch (_: Exception) {}
            // Switching providers is cache-only; Reload on Home/Providers is explicit.
            try {
                if (live.isNotEmpty()) Cats.tagAll(live)
                if (vod.isNotEmpty()) Cats.tagAll(vod)
                if (series.isNotEmpty()) Cats.tagAll(series)
            } catch (_: Exception) {}
            val all = live + vod + series
            withContext(Dispatchers.Main) {
                drawerChannels = all
                drawerLoading = false
                if (all.isEmpty()) toast(ctx, "No saved data for this provider. Use Reload on Home or Providers.")
            }
        }
    }

    // account list for the drawer's provider switcher (enabled only)
    LaunchedEffect(showChannels) {
        if (showChannels && drawerAccounts.isEmpty()) {
            drawerAccounts = try { Store(ctx).enabledAccounts() }
            catch (_: Exception) { emptyList() }
        }
    }

    fun toggleMute() {
        muted = !muted
        try { player.setVolume(if (muted) 0f else 1f) } catch (_: Exception) {}
    }

    // ZyneLabs-style: hand the current stream URL to another video app.
    fun openExternal() {
        val url = if (isStalker) resolvedUrl else current.url
        if (url.isEmpty()) { toast(ctx, "No stream URL"); return }
        try {
            val mime = if (url.lowercase().contains(".m3u8")) "application/x-mpegURL"
            else "video/*"
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(url), mime)
            ctx.startActivity(Intent.createChooser(i, "Open with…"))
        } catch (_: Exception) { toast(ctx, "No app can open this stream") }
    }

    // warm the shared EPG cache so the info box can show Now/Next
    LaunchedEffect(accountId) {
        if (accountId.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val acc = Store(ctx).account(accountId)
                    if (acc != null && acc.isXtream()) {
                        EpgCache.ensure(ctx, acc, channels) { epgTick++ }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun prevChannel() {
        if (channels.isEmpty()) return
        val i = channels.indexOfFirst { it.url == current.url && it.name == current.name }
        switchChannel(channels[(i - 1 + channels.size) % channels.size])
    }

    fun nextChannel() {
        if (channels.isEmpty()) return
        val i = channels.indexOfFirst { it.url == current.url && it.name == current.name }
        switchChannel(channels[(i + 1) % channels.size])
    }

    fun toggleFavorite() {
        scope.launch(Dispatchers.IO) {
            try {
                val s = Store(ctx)
                s.toggleFav(accountId, current.name)
                val fav = s.isFav(accountId, current.name)
                withContext(Dispatchers.Main) { isFavorite = fav }
            } catch (_: Exception) {}
        }
    }

    fun toggleRecording() {
        if (isRecording) {
            stopRecording()
            isRecording = false
            recRemainSec = 0
        } else {
            showRecTimer = true
        }
    }

    fun startRecordingWithTimer(min: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                startRecording(ctx, current)
                withContext(Dispatchers.Main) {
                    isRecording = true
                    recRemainSec = if (min > 0) min * 60 else 0
                }
            } catch (_: Exception) {}
        }
    }

    fun loadCatchup() {
        showCatchup = true
        catchupProgs = null
        scope.launch(Dispatchers.IO) {
            val progs = try { XtreamClient.catchupEpg(current) }
            catch (_: Exception) { emptyList() }
            withContext(Dispatchers.Main) { catchupProgs = progs }
        }
    }

    // ZyneLabs-style: pick an external .srt via SAF and attach it to the
    // current item without losing the playback position.
    val subPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        try {
            val sub = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("und")
                .setLabel("External .srt")
                .build()
            val pos = player.currentPosition
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(if (isStalker && resolvedUrl.isNotEmpty()) resolvedUrl else current.url)
                    .setSubtitleConfigurations(listOf(sub))
                    .build(),
                pos
            )
            player.prepare()
            player.play()
            extSubName = "External .srt"
        } catch (_: Exception) { toast(ctx, "Could not load subtitle") }
    }

    BackHandler {
        // Close any open overlay first; otherwise mini: back closes the
        // pop-up, fullscreen: back minimizes to the in-app pop-up.
        when {
            // Subdialogs always close before their parent drawer.
            showSubDialog -> showSubDialog = false
            showSleepDialog -> showSleepDialog = false
            showSpeedDialog -> showSpeedDialog = false
            showAudioDialog -> showAudioDialog = false
            showStatsDialog -> showStatsDialog = false
            showScaleDialog -> showScaleDialog = false
            showRecTimer -> showRecTimer = false
            showProviderPick -> showProviderPick = false
            showCatchup -> showCatchup = false
            showChannels -> showChannels = false
            showSettingsOptions -> {
                showSettingsOptions = false
                scope.launch { delay(120); try { gearFr.requestFocus() } catch (_: Exception) {} }
            }
            mini -> onClose()
            else -> onMinimize()
        }
    }

    // tap toggles controls; vertical swipe on left = brightness,
    // on right = volume (ZyneLabs-style). An edge strip with the live
    // value appears on the swiped side while swiping.
    val brightnessRef = rememberUpdatedState(brightness)
    val volumeRef = rememberUpdatedState(volume)
    val gestureModifier = if (!mini) Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            var swiping = false
            var brightSide = false
            var b0 = 0f
            var v0 = 0
            var lifted = false
            while (!lifted) {
                val event = awaitPointerEvent()
                if (event.changes.all { !it.pressed }) { lifted = true; break }
                val cur = event.changes[0]
                val dy = cur.position.y - down.position.y
                val dx = cur.position.x - down.position.x
                if (!swiping && abs(dy) > 16.dp.toPx() && abs(dy) > abs(dx) * 1.3f) {
                    swiping = true
                    brightSide = down.position.x < size.width / 2f
                    swipeSide = if (brightSide) 0 else 1
                    if (brightSide) b0 = brightnessRef.value else v0 = volumeRef.value
                }
                if (swiping) {
                    val frac = (down.position.y - cur.position.y) / (size.height * 0.8f)
                    if (brightSide) {
                        setBrightness((b0 + frac).coerceIn(0.01f, 1f))
                    } else {
                        setVolume((v0 + (frac * maxVolume).roundToInt()).coerceIn(0, maxVolume))
                    }
                    cur.consume()
                }
            }
            if (swiping) swipeSeq++ else showControls = !showControls
        }
    } else Modifier

    Box(
        Modifier.fillMaxSize().background(if (mini) Color.Transparent else Color.Black)
            // TV-only focus anchor (see tvRootFr): with all overlays hidden no
            // button is focusable, so the root holds focus and D-pad key
            // events reach onPreviewKeyEvent below. Phone/touch unchanged.
            .then(if (DzTv.isTv && !mini) Modifier.focusRequester(tvRootFr).focusable() else Modifier)
            // TV D-pad remote: when the player interface is hidden, the remote
            // drives playback directly — LEFT/RIGHT switch channel, OK shows
            // the controls overlay (like a screen tap) and focuses the first
            // button, DOWN opens the channel box. Preview phase only when all
            // overlays are hidden, so D-pad focus navigation between the
            // overlay buttons keeps working untouched.
            .onPreviewKeyEvent { e ->
                if (!DzTv.isTv || mini || locked) return@onPreviewKeyEvent false
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // TV: every D-pad key restarts the overlay auto-hide timer,
                // so the info bar can never get stuck on screen for
                // D-pad-only users (previously only a mouse click dismissed
                // it). Runs before the overlay-shown early return so keys
                // pressed while navigating buttons count too.
                when (e.key) {
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft,
                    Key.DirectionRight, Key.DirectionCenter, Key.Enter,
                    Key.NumPadEnter, Key.Back -> uiTick++
                    else -> {}
                }
                if (showControls || showChannels || showSettingsOptions || showSubDialog ||
                    showSleepDialog || showSpeedDialog || showAudioDialog ||
                    showStatsDialog || showCatchup ||
                    showScaleDialog || showRecTimer || showProviderPick
                ) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { prevChannel(); true }
                    Key.DirectionRight -> { nextChannel(); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        showControls = true
                        scope.launch {
                            delay(120)
                            try { firstCtrlFr.requestFocus() } catch (_: Exception) {}
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        showSettingsOptions = false
                        showChannels = true
                        scope.launch {
                            delay(200)
                            try { drawerFirstFr.requestFocus() } catch (_: Exception) {}
                        }
                        true
                    }
                    else -> false
                }
            }
            // TV D-pad: tracks whether focus sits inside the overlay (used
            // by the auto-hide effect; every D-pad key also restarts the
            // hide timer via uiTick).
            .onFocusChanged { overlayHasFocus = it.hasFocus }
    ) {
        // video — manual ZyneLabs-style surface layout: the SurfaceView is
        // sized for aspect mode + video scale inside a clipping box, so no
        // GPU-layer transforms are needed (they glitch SurfaceViews on some
        // devices). The same call site keeps the player alive across
        // mini <-> fullscreen toggles.
        Box(
            Modifier.fillMaxSize()
                .clipToBounds()
                .then(gestureModifier)
                .onSizeChanged { boxW = it.width; boxH = it.height }
        ) {
            val density = LocalDensity.current
            val (sw, sh) = remember(videoW, videoH, boxW, boxH, aspectMode, videoScale) {
                val dimensions = VideoScaleModes.dimensions(
                    videoW, videoH, boxW, boxH, aspectMode, videoScale)
                dimensions[0] to dimensions[1]
            }

            AndroidView(
                factory = { c -> SurfaceView(c).also { player.setVideoSurfaceView(it) } },
                modifier = Modifier.align(Alignment.Center)
                    .size(with(density) { sw.toDp() }, with(density) { sh.toDp() })
            )
        }

        // ===== aspect/scale hint: plain floating text, fully transparent =====
        aspectHint?.let { hint ->
            Text(
                hint, color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xCC000000),
                        offset = Offset(1f, 2f), blurRadius = 6f
                    )
                ),
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
            )
        }

        if (mini) {
            // ===== floating mini pop-up overlay =====
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
            ) {
                Text(
                    current.name, color = Color.White, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp, end = 44.dp)
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .size(30.dp).clip(CircleShape).background(Color(0xAA000000))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) { Text("×", color = Color.White, fontSize = 16.sp) }
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .size(30.dp).clip(CircleShape).background(Color(0xAA000000))
                        .clickable { onExpand() },
                    contentAlignment = Alignment.Center
                ) { Text("⛶", color = Color.White, fontSize = 14.sp) }
            }
        } else {

            if (error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Playback failed", color = Color.White, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(error ?: "", color = Dim, fontSize = 14.sp)
                    }
                }
            }
            if (isBuffering && error == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }

            // recording indicator — red dot + "rec" only.
            // portrait: top (transport cluster sits at the bottom there)
            if (isRecording) {
                Row(
                    modifier = if (isPortrait)
                        Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 84.dp)
                    else
                        Modifier.align(Alignment.BottomStart)
                            .padding(start = 20.dp, bottom = 160.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Red))
                    Spacer(Modifier.width(6.dp))
                    Text("rec" + if (recRemainSec > 0) " " + fmtTime(recRemainSec * 1000L) else "",
                        color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium)
                }
            }

            // Invisible outside-tap catcher sits behind the info/settings stack.
            if (showSettingsOptions && !showChannels && !locked) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(onTap = { showSettingsOptions = false })
                })
            }
            if (showControls && !locked) {
                // soft shadow keeps floating text readable over the video
                // (ZyneLabs-style: bars are fully transparent)
                val videoTextStyle = TextStyle(
                    shadow = Shadow(
                        color = Color(0xCC000000),
                        offset = Offset(1f, 2f), blurRadius = 6f
                    )
                )
                // ===== top bar (fully transparent — floats over the video) =====
                Row(
                    modifier = Modifier.fillMaxWidth(if (showChannels) { if (isPortrait) 0.6f else 0.75f } else 1f)
                        .background(Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).glassCapsule(corner = 22.dp)
                            .clip(CircleShape)
                            .focusRequester(firstCtrlFr)
                            .tvFocus(corner = 22.dp)
                            .focusable()
                            .clickable { onMinimize() },
                        contentAlignment = Alignment.Center
                    ) { DzGlyph("\u2190", Color.White, 24.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        PlayerLabel(current.name, color = Color.White, fontSize = 17.sp,
                            fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLive) {
                                val blink = rememberInfiniteTransition(label = "liveDot")
                                val dotAlpha by blink.animateFloat(
                                    initialValue = 1f, targetValue = 0.2f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(650, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "blink"
                                )
                                Text(
                                    "●", color = Color(0xFFFF3B30).copy(alpha = dotAlpha),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    " LIVE", color = Accent, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (!showChannels) {
                    // (favorite star removed from top bar — already in the info box action row)
                    // in-player channel drawer (ZyneLabs-style TV icon in the top bar)
                    TopIcon("", CardSel, size = topIconSize, icon = { TvIcon() }) {
                        showSettingsOptions = false
                        showChannels = true
                        scope.launch {
                            delay(120)
                            try { drawerFirstFr.requestFocus() } catch (_: Exception) {}
                        }
                    }
                    Spacer(Modifier.width(topIconGap))
                    // lock
                    TopIcon("", CardSel, size = topIconSize, icon = { LockIcon() }) { locked = true }
                    Spacer(Modifier.width(topIconGap))
                    // picture-in-picture pill
                    Box(
                        Modifier.glassCapsule(corner = 20.dp)
                            .clip(RoundedCornerShape(20.dp)).background(CardSel)
                            .tvFocus(corner = 20.dp)
                            .focusable()
                            .clickable { enterPip() }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "pip", color = Color.White,
                            fontSize = if (isPortrait) 9.sp else 10.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(topIconGap))
                    // (top-bar gear removed — ⚙ already lives in the info box action row)
                    }
                }

                // (transport now lives in the bottom stack — ZyneLabs-style)

                // ===== bottom stack (ZyneLabs-style): info box + seek row +
                // centered transport cluster — all fully transparent =====
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp - 88).coerceAtLeast(140).dp)
                        .navigationBarsPadding()
                        .background(Color.Transparent)
                        .animateContentSize(animationSpec = tween(160))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    // channel + quality
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val n = channels.indexOf(current).takeIf { it >= 0 }?.plus(1)
                        if (n != null) {
                            Box(
                                Modifier.glassCapsule(corner = 14.dp)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                PlainText(
                                    "#$n", color = PlayerSecondary, fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        PlayerLabel(
                            current.name, color = Color.White,
                            fontSize = if (DzTv.isTv) 17.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLive) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "● LIVE", color = Accent, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, style = videoTextStyle
                            )
                        }
                        if (quality.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.glassCapsule(corner = 14.dp)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                PlainText(
                                    quality, color = Color.White, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    // Show actual Now/Next EPG only; no empty-schedule placeholder row.
                    @Suppress("UNUSED_VARIABLE")
                    val epgRead = epgTick
                    run {
                        val now =
                            if (accountId.isNotEmpty()) EpgCache.now(accountId, EpgCache.key(current))
                            else null
                        val next =
                            if (accountId.isNotEmpty()) EpgCache.next(accountId, EpgCache.key(current))
                            else null
                        val tf = java.text.SimpleDateFormat(
                            "HH:mm", java.util.Locale.getDefault()
                        )
                        val txt = when {
                            now != null -> "Now  ${tf.format(java.util.Date(now.start))}–${tf.format(java.util.Date(now.stop))}  ${now.title}"
                            next != null -> "Next  ${tf.format(java.util.Date(next.start))}  ${next.title}"
                            else -> null
                        }
                        if (txt != null) {
                            Spacer(Modifier.height(2.dp))
                            PlayerLabel(txt, color = PlayerSecondary,
                                fontSize = if (DzTv.isTv) 15.sp else 13.sp)
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    // seek row (seekable content only — ZyneLabs hides it for live)
                    if (!isLive) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                fmtTime(position), color = Color.White,
                                fontSize = 13.sp, style = videoTextStyle
                            )
                            Spacer(Modifier.width(12.dp))
                            var sliderPos by remember { mutableFloatStateOf(0f) }
                            LaunchedEffect(position) {
                                sliderPos = if (duration > 0)
                                    (position.toFloat() / duration.toFloat())
                                        .coerceIn(0f, 1f) else 0f
                            }
                            Slider(
                                value = sliderPos,
                                onValueChange = { sliderPos = it },
                                onValueChangeFinished = {
                                    player.seekTo((sliderPos * duration).toLong())
                                },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Accent,
                                    inactiveTrackColor = Color(0xFF3A4556)
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                fmtTime(duration), color = Color.White,
                                fontSize = 13.sp, style = videoTextStyle
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                    }
                    // action row (ZyneLabs-style): REC | info | EPG | ★ | ⚙ — left aligned
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(
                            4.dp, Alignment.Start
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoAction(
                            "REC",
                            modifier = Modifier.tvPanelTarget(recordFr),
                            color = if (isRecording) Color(0xFFFFB4AB) else Color.White
                        ) { featureReturnFr = recordFr; toggleRecording() }
                        InfoAction("info", modifier = Modifier.tvPanelTarget(infoFr)) {
                            featureReturnFr = infoFr; showStatsDialog = true
                        }
                        InfoAction("EPG") { onOpenGuide() }
                        InfoAction("Zoom ${(videoScale * 100).roundToInt()}%", modifier = Modifier.tvPanelTarget(zoomFr)) {
                            featureReturnFr = zoomFr; showScaleDialog = true
                        }
                        if (accountId.isNotEmpty()) {
                            InfoAction(
                                if (isFavorite) "★" else "☆",
                                color = if (isFavorite) Color(0xFFFFD54A)
                                else Color.White
                            ) { toggleFavorite() }
                        }
                        InfoAction("Settings", modifier = Modifier.focusRequester(gearFr).focusProperties {
                            down = if (showSettingsOptions) settingsFirstFr else FocusRequester.Default
                        }) {
                            showChannels = false
                            showSettingsOptions = !showSettingsOptions
                        }
                    }
                    // Expanded settings belong below the info/actions in the same bottom stack.
                    // Adding these rows lifts the info box naturally; closing lowers it again.
                    if (showSettingsOptions && !showChannels) {
                        Spacer(Modifier.height(2.dp))
                        PlayerSettingsOptions(
                            settingsFirstFr = settingsFirstFr,
                            gearFr = gearFr,
                            onActionFocus = { featureReturnFr = it },
                            speed = speed, muted = muted, aspectMode = aspectMode,
                            showCatchup = current.streamId != 0,
                            onSubtitles = { showSubDialog = true },
                            onSleep = { showSleepDialog = true },
                            onSpeed = { showSpeedDialog = true },
                            onAudio = { showAudioDialog = true },
                            onExternal = { openExternal() },
                            onMute = { toggleMute() },
                            onAspect = { cycleAspect() },
                            onCatchup = { loadCatchup() }
                        )
                    }
                    // Center transport controls are intentionally omitted.

                }
            }

            // edge strip: brightness (left) / volume (right) while swiping.
            // Outside showControls so it appears on swipe even when the
            // info box is hidden.
            val side = swipeSide
            if (side != null) {
                val frac = if (side == 0) brightness
                else volume.toFloat() / maxVolume.toFloat()
                val pctText = if (side == 0) "${(brightness * 100).roundToInt()}%"
                else "$volume"
                Box(
                    Modifier.align(if (side == 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (side == 0) SunIcon() else SpeakerIcon()
                        Spacer(Modifier.height(6.dp))
                        Text(
                            pctText, color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.width(8.dp).height(140.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF3A4556))
                        ) {
                            Box(
                                Modifier.width(8.dp)
                                    .fillMaxHeight(frac.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Accent)
                                    .align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }

            // locked: tap to unlock
            if (locked) {
                Box(
                    Modifier.fillMaxSize().clickable { locked = false },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.clip(CircleShape).background(Color(0xAA000000))
                            .padding(20.dp)
                    ) { LockIcon(iconSize = 28.dp) }
                }
            }

            // recording timer picker
            if (showRecTimer) {
                val initialFr = remember { FocusRequester() }
                val closeFr = remember { FocusRequester() }
                PlayerFeatureDialog(
                    initialFocus = initialFr,
                    onDismissRequest = { showRecTimer = false },
                    title = {
                        Text(
                            "Record",
                            color = Color.White,
                            fontSize = if (DzTv.isTv) 15.sp else 14.sp
                        )
                    },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Stop recording after…", color = PlayerSecondary, fontSize = 11.sp)
                            if (DzTv.isTv) {
                                val minutes = listOf(0, 15, 30, 60, 90, 120)
                                TvChoiceGrid(minutes.map { if (it == 0) "No timer" else "$it min" },
                                    -1, initialFr, columns = 3, downTarget = closeFr) {
                                    showRecTimer = false
                                    startRecordingWithTimer(minutes[it])
                                }
                            } else FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                listOf(
                                    0 to "No timer",
                                    15 to "15 min", 30 to "30 min",
                                    60 to "60 min", 90 to "90 min", 120 to "120 min"
                                ).forEach { (m, label) ->
                                    ScaleModeChoice(label, false,
                                        modifier = if (m == 0) Modifier.tvPanelTarget(initialFr) else Modifier) {
                                        showRecTimer = false
                                        startRecordingWithTimer(m)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        if (DzTv.isTv) ScaleModeChoice("Close", false,
                            Modifier.focusRequester(closeFr).focusProperties { up = initialFr }) { showRecTimer = false }
                    }
                )
            }

            // Floating scale/ratio controls: no panel or dim layer over the video.
            if (showScaleDialog) {
                VideoScaleDialog(
                    videoScale = videoScale,
                    aspectMode = aspectMode,
                    onScale = { setVideoScale(it); showScaleDialog = false },
                    onMode = {
                        aspectMode = it
                        aspectHint = "Aspect: " + VideoScaleModes.label(it)
                        showScaleDialog = false
                    },
                    onDismiss = { showScaleDialog = false }
                )
            }
            // ===== in-player dialogs (ZyneLabs-style: opened from the
            // inline settings drawer inside the info box) =====
            if (showSubDialog) {
                val initialFr = remember { FocusRequester() }
                val textGroups = tracks?.groups
                    ?.filter { it.type == C.TRACK_TYPE_TEXT } ?: emptyList()
                PlayerFeatureDialog(
                    initialFocus = initialFr,
                    onDismissRequest = { showSubDialog = false },
                    title = { Text("Subtitles", color = Color.White) },
                    text = {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = ((2 + textGroups.sumOf { it.length }) * 48).coerceAtMost(192).dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            item {
                                TrackRow(
                                    "CC", extSubName ?: "External .srt",
                                    extSubName != null, modifier = Modifier.tvPanelTarget(initialFr)
                                ) { subPicker.launch("*/*") }
                            }
                            item {
                                TrackRow("✕", "Off", player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                                    try {
                                        player.trackSelectionParameters =
                                            player.trackSelectionParameters.buildUpon()
                                                .setTrackTypeDisabled(
                                                    C.TRACK_TYPE_TEXT, true
                                                ).build()
                                    } catch (_: Exception) {}
                                    showSubDialog = false
                                }
                            }
                            textGroups.forEach { g ->
                                for (ti in 0 until g.length) {
                                    val f = g.getTrackFormat(ti)
                                    item {
                                        TrackRow(
                                            "CC",
                                            f.label ?: f.language
                                                ?: "CC ${ti + 1}",
                                            g.isTrackSelected(ti)
                                        ) {
                                            try {
                                                player.trackSelectionParameters =
                                                    player.trackSelectionParameters
                                                        .buildUpon()
                                                        .setTrackTypeDisabled(
                                                            C.TRACK_TYPE_TEXT, false
                                                        )
                                                        .setOverrideForType(
                                                            TrackSelectionOverride(
                                                                g.mediaTrackGroup, ti
                                                            )
                                                        ).build()
                                            } catch (_: Exception) {}
                                            showSubDialog = false
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        ScaleModeChoice("Close", false, onClick = { showSubDialog = false })
                    }
                )
            }

            if (showSleepDialog) {
                val initialFr = remember { FocusRequester() }
                val closeFr = remember { FocusRequester() }
                PlayerFeatureDialog(
                    initialFocus = initialFr,
                    onDismissRequest = { showSleepDialog = false },
                    title = { Text("Sleep timer", color = Color.White) },
                    text = {
                        if (DzTv.isTv) {
                            val minutes = listOf(0, 15, 30, 60, 90)
                            TvChoiceGrid(minutes.map { if (it == 0) "Off" else "${it}m" },
                                minutes.indexOf(sleepMin), initialFr, columns = 5, downTarget = closeFr) {
                                sleepMin = minutes[it]
                                showSleepDialog = false
                            }
                        } else FlowRow(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf(0, 15, 30, 60, 90).forEach { m ->
                                PlayerOptionChip(if (m == 0) "Off" else "${m}m", sleepMin == m,
                                    modifier = if (sleepMin == m) Modifier.tvPanelTarget(initialFr) else Modifier) {
                                    sleepMin = m
                                    showSleepDialog = false
                                }
                            }
                        }
                    },
                    confirmButton = {
                        if (DzTv.isTv) ScaleModeChoice("Close", false,
                            Modifier.focusRequester(closeFr).focusProperties { up = initialFr }) { showSleepDialog = false }
                    }
                )
            }

            if (showSpeedDialog) {
                val initialFr = remember { FocusRequester() }
                val closeFr = remember { FocusRequester() }
                PlayerFeatureDialog(
                    initialFocus = initialFr,
                    onDismissRequest = { showSpeedDialog = false },
                    title = { Text("Playback speed", color = Color.White) },
                    text = {
                        if (DzTv.isTv) {
                            TvChoiceGrid(SPEEDS.map { if (it == 1f) "Normal" else "${it}x" },
                                SPEEDS.indexOf(speed), initialFr, downTarget = closeFr) {
                                speed = SPEEDS[it]
                                player.setPlaybackSpeed(speed)
                                showSpeedDialog = false
                            }
                        } else FlowRow(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            SPEEDS.forEach { s ->
                                PlayerOptionChip(
                                    if (s == 1f) "Normal" else "${s}x",
                                    speed == s,
                                    modifier = if (speed == s) Modifier.tvPanelTarget(initialFr) else Modifier
                                ) {
                                    speed = s
                                    player.setPlaybackSpeed(s)
                                    showSpeedDialog = false
                                }
                            }
                        }
                    },
                    confirmButton = {
                        if (DzTv.isTv) ScaleModeChoice("Close", false,
                            Modifier.focusRequester(closeFr).focusProperties { up = initialFr }) { showSpeedDialog = false }
                    }
                )
            }

            if (showAudioDialog) {
                val initialFr = remember { FocusRequester() }
                val audioGroups = tracks?.groups
                    ?.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 } ?: emptyList()
                PlayerFeatureDialog(
                    initialFocus = initialFr,
                    onDismissRequest = { showAudioDialog = false },
                    title = { Text("Audio track", color = Color.White) },
                    text = {
                        if (audioGroups.isEmpty()) {
                            Text("No alternate audio tracks.", color = PlayerSecondary)
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = (audioGroups.sumOf { it.length } * 48).coerceIn(48, 192).dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                audioGroups.forEach { g ->
                                    for (ti in 0 until g.length) {
                                        val f = g.getTrackFormat(ti)
                                        item {
                                            TrackRow(
                                                "♪",
                                                f.label ?: f.language
                                                    ?: "Track ${ti + 1}",
                                                g.isTrackSelected(ti),
                                                modifier = if (g == audioGroups.first() && ti == 0)
                                                    Modifier.tvPanelTarget(initialFr) else Modifier
                                            ) {
                                                try {
                                                    player.trackSelectionParameters =
                                                        player.trackSelectionParameters
                                                            .buildUpon()
                                                            .setOverrideForType(
                                                                TrackSelectionOverride(
                                                                    g.mediaTrackGroup, ti
                                                                )
                                                            ).build()
                                                } catch (_: Exception) {}
                                                showAudioDialog = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        ScaleModeChoice("Close", false,
                            modifier = if (audioGroups.isEmpty()) Modifier.tvPanelTarget(initialFr) else Modifier,
                            onClick = { showAudioDialog = false })
                    }
                )
            }

            // stream info (ZyneLabs-style "info" action)
            if (showStatsDialog) {
                val initialFr = remember { FocusRequester() }
                PlayerFeatureDialog(
                    initialFocus = initialFr,
                    onDismissRequest = { showStatsDialog = false },
                    title = { Text("Stream info", color = Color.White) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(current.name, color = Color.White,
                                fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            if (videoW > 0 && videoH > 0)
                                Text("Resolution  ${videoW}×${videoH}",
                                    color = PlayerSecondary, fontSize = 11.sp)
                            if (quality.isNotEmpty())
                                Text("Quality  $quality", color = PlayerSecondary, fontSize = 11.sp)
                            Text("Speed  ${speed}x", color = PlayerSecondary, fontSize = 11.sp)
                            Text("Aspect  ${VideoScaleModes.label(aspectMode)}  •  " +
                                "Scale  ${(videoScale * 100).roundToInt()}%",
                                color = PlayerSecondary, fontSize = 11.sp)
                            Text(
                                if (isLive) "Live stream"
                                else "Position  ${fmtTime(position)} / ${fmtTime(duration)}",
                                color = PlayerSecondary, fontSize = 11.sp
                            )
                        }
                    },
                    confirmButton = {
                        ScaleModeChoice("Close", false, modifier = Modifier.tvPanelTarget(initialFr),
                            onClick = { showStatsDialog = false })
                    }
                )
            }

            // ===== right channel drawer (fully transparent, ZyneLabs-style) =====
            if (showChannels) {
                ChannelDrawer(
                    modifier = Modifier.align(Alignment.TopEnd),
                    drawerChannels = drawerChannels,
                    drawerCat = drawerCat,
                    onCatChange = { drawerCat = it },
                    drawerAccounts = drawerAccounts,
                    drawerAccountId = drawerAccountId,
                    drawerLoading = drawerLoading,
                    current = current,
                    isPortrait = isPortrait,
                    drawerFirstFr = drawerFirstFr,
                    scope = scope,
                    onChannelClick = { switchChannel(it) },
                    onClose = { showChannels = false },
                    onProviderPick = { showProviderPick = true },
                    onLoadProvider = { loadDrawerProvider(it) }
                )
            }
                // provider picker dialog
                if (showProviderPick) {
                    AlertDialog(
                        onDismissRequest = { showProviderPick = false },
                        title = { Text("Provider", color = Ink) },
                        text = {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(drawerAccounts, key = { it.id }) { acc ->
                                    val accName =
                                        if (acc.name.isNotEmpty()) acc.name
                                        else "Playlist"
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .tvFocus(corner = 8.dp)
                                            .focusable()
                                            .clickable {
                                                showProviderPick = false
                                                if (acc.id != drawerAccountId)
                                                    loadDrawerProvider(acc)
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            accName, color = Ink, fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (acc.id == drawerAccountId) Text(
                                            "✓", color = Accent, fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        containerColor = CardBg
                    )
                }
            }

            // ===== catch-up dialog =====
            if (showCatchup) {
                val initialFr = remember { FocusRequester() }
                PlayerFeatureDialog(
                    initialFocus = initialFr,
                    onDismissRequest = { showCatchup = false },
                    title = { Text("Catch-up", color = Ink) },
                    text = {
                        when (val progs = catchupProgs) {
                            null -> Box(
                                Modifier.fillMaxWidth().height(120.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(color = Accent) }
                            else -> if (progs.isEmpty()) {
                                Text("No catch-up available for this channel.",
                                    color = PlayerSecondary)
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 240.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(progs) { p ->
                                        Column(
                                            Modifier.fillMaxWidth()
                                                .glassCapsule(corner = 20.dp)
                                                .tvFocus(corner = 20.dp)
                                                .clickable {
                                                    playCatchup(player, current, p)
                                                    showCatchup = false
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(p.title ?: "", color = Ink,
                                                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text("${p.start} - ${p.stop}", color = PlayerSecondary,
                                                fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        ScaleModeChoice("Close", false, modifier = Modifier.tvPanelTarget(initialFr),
                            onClick = { showCatchup = false })
                    }
                )
            }
        }

    }

/** Genre categories for the in-player channel drawer (mirrors MainActivity.buildCategories). */
private fun drawerCategories(channels: List<Channel>): List<Pair<String, Int>> {
    val counts = linkedMapOf<String, Int>()
    for (c in channels) {
        val cat = c.getSmartCat()
        counts[cat] = (counts[cat] ?: 0) + 1
    }
    return counts.entries.sortedByDescending { it.value }
        .map { it.key to it.value }
}

private fun playCatchup(player: ExoPlayer, ch: Channel, p: XtreamClient.Program) {
    try {
        val startMs = XtreamClient.parseEpgTime(p.start ?: "")
        val stopMs = XtreamClient.parseEpgTime(p.stop ?: "")
        if (startMs > 0 && stopMs > startMs) {
            val durMin = ((stopMs - startMs) / 60000).coerceAtLeast(1L)
            val url = XtreamClient.timeshiftUrl(ch, startMs, durMin)
            if (url.isNotEmpty()) {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
            }
        }
    } catch (_: Exception) {}
}

// ---- recording helpers (StreamRecorder is Java) ----
private var recorder: StreamRecorder? = null
private fun startRecording(ctx: Context, ch: Channel) {
    try {
        val r = StreamRecorder()
        val fileName = StreamRecorder.fileNameFor(ch.name)
        recorder = r
        // Stalker links are created over the network, so resolve off the main thread.
        Thread {
            try {
                val u = StalkerClient.playUrl(ch)
                if (u.isNotEmpty()) {
                    r.start(ctx, u, fileName, object : StreamRecorder.Listener {
                        override fun onStarted(f: String) {}
                        override fun onStopped(f: String, bytes: Long) {}
                        override fun onError(msg: String) {}
                    })
                }
            } catch (_: Exception) {}
        }.start()
    } catch (_: Exception) {}
}
private fun stopRecording() {
    try { recorder?.stop() } catch (_: Exception) {}
    recorder = null
}

@Composable
private fun TopIcon(
    label: String,
    bg: Color,
    small: Boolean = false,
    size: Dp = 40.dp,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    GlassContent {
    Box(
        Modifier.size(size).glassCapsule(corner = size / 2)
            .clip(CircleShape).background(bg)
            .tvFocus(corner = size / 2)
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) icon()
        else Text(label, color = Color.White,
            fontSize = if (small) 11.sp else 18.sp,
            fontWeight = FontWeight.Bold)
    }

    }
}

/** Transport icons drawn with Canvas — plain white, matches the UI
 *  (the ⏸/⏺/⏮/⏭ text glyphs render as orange emoji on some phones). */
@Composable
private fun PlayIcon(color: Color = LocalContentColor.current) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        drawPath(Path().apply {
            moveTo(w * 0.28f, h * 0.18f)
            lineTo(w * 0.78f, h * 0.5f)
            lineTo(w * 0.28f, h * 0.82f)
            close()
        }, Color.Black, style = Stroke(width = 4f))
        drawPath(Path().apply {
            moveTo(w * 0.28f, h * 0.18f)
            lineTo(w * 0.78f, h * 0.5f)
            lineTo(w * 0.28f, h * 0.82f)
            close()
        }, color)
    }
}

@Composable
private fun PauseIcon(color: Color = LocalContentColor.current) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        drawRect(Color.Black, Offset(w * 0.26f, h * 0.2f), Size(w * 0.18f, h * 0.6f), style = Stroke(width = 4f))
        drawRect(color, Offset(w * 0.26f, h * 0.2f), Size(w * 0.18f, h * 0.6f))
        drawRect(Color.Black, Offset(w * 0.56f, h * 0.2f), Size(w * 0.18f, h * 0.6f), style = Stroke(width = 4f))
        drawRect(color, Offset(w * 0.56f, h * 0.2f), Size(w * 0.18f, h * 0.6f))
    }
}

@Composable
private fun PrevIcon(color: Color = LocalContentColor.current) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        drawRect(Color.Black, Offset(w * 0.14f, h * 0.22f), Size(w * 0.13f, h * 0.56f), style = Stroke(width = 4f))
        drawRect(color, Offset(w * 0.14f, h * 0.22f), Size(w * 0.13f, h * 0.56f))
        drawPath(Path().apply {
            moveTo(w * 0.84f, h * 0.2f)
            lineTo(w * 0.32f, h * 0.5f)
            lineTo(w * 0.84f, h * 0.8f)
            close()
        }, Color.Black, style = Stroke(width = 4f))
        drawPath(Path().apply {
            moveTo(w * 0.84f, h * 0.2f)
            lineTo(w * 0.32f, h * 0.5f)
            lineTo(w * 0.84f, h * 0.8f)
            close()
        }, color)
    }
}

@Composable
private fun NextIcon(color: Color = LocalContentColor.current) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        drawPath(Path().apply {
            moveTo(w * 0.16f, h * 0.2f)
            lineTo(w * 0.68f, h * 0.5f)
            lineTo(w * 0.16f, h * 0.8f)
            close()
        }, Color.Black, style = Stroke(width = 4f))
        drawPath(Path().apply {
            moveTo(w * 0.16f, h * 0.2f)
            lineTo(w * 0.68f, h * 0.5f)
            lineTo(w * 0.16f, h * 0.8f)
            close()
        }, color)
        drawRect(Color.Black, Offset(w * 0.73f, h * 0.22f), Size(w * 0.13f, h * 0.56f), style = Stroke(width = 4f))
        drawRect(color, Offset(w * 0.73f, h * 0.22f), Size(w * 0.13f, h * 0.56f))
    }
}

/** Padlock drawn with Canvas in outline style (matches TvIcon) — the 🔒 emoji
 *  renders yellow on some phones and clashes with the theme. */
@Composable
private fun LockIcon(iconSize: Dp = 20.dp, color: Color = Color.White) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width; val h = size.height
        val sw = w * 0.13f
        // shackle (outline)
        drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.24f, h * 0.08f), size = Size(w * 0.52f, h * 0.46f),
            style = Stroke(sw))
        // body (outline, matches TvIcon stroke style)
        drawRoundRect(color, Offset(w * 0.15f, h * 0.42f), Size(w * 0.7f, h * 0.52f),
            CornerRadius(w * 0.12f), style = Stroke(sw))
        // keyhole
        drawCircle(color, w * 0.07f, Offset(w * 0.5f, h * 0.62f))
        drawRect(color, Offset(w * 0.5f - sw * 0.3f, h * 0.63f),
            Size(sw * 0.6f, h * 0.18f))
    }
}

/** Speaker icon drawn with Canvas (IPTV Smarters style) — no emoji. */
@Composable
private fun SpeakerIcon(
    color: Color = Color.White,
    iconSize: Dp = 24.dp,
    muted: Boolean = false
) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val body = Path().apply {
            moveTo(w * 0.10f, h * 0.42f)
            lineTo(w * 0.38f, h * 0.42f)
            lineTo(w * 0.58f, h * 0.14f)
            lineTo(w * 0.58f, h * 0.86f)
            lineTo(w * 0.38f, h * 0.58f)
            lineTo(w * 0.10f, h * 0.58f)
            close()
        }
        drawPath(body, color)
        if (muted) {
            // X mark
            drawLine(
                color, Offset(w * 0.66f, h * 0.36f), Offset(w * 0.92f, h * 0.64f),
                strokeWidth = w * 0.08f, cap = StrokeCap.Round
            )
            drawLine(
                color, Offset(w * 0.92f, h * 0.36f), Offset(w * 0.66f, h * 0.64f),
                strokeWidth = w * 0.08f, cap = StrokeCap.Round
            )
        } else {
            // sound waves
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(w * 0.60f, h * 0.30f),
                size = Size(w * 0.24f, h * 0.40f),
                style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(w * 0.74f, h * 0.20f),
                size = Size(w * 0.24f, h * 0.60f),
                style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
            )
        }
    }
}

/** Modern sun/brightness icon drawn with Canvas — no emoji. */
@Composable
private fun SunIcon(color: Color = Color.White, iconSize: Dp = 22.dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val cx = w / 2f
        val cy = size.height / 2f
        // center disc
        drawCircle(color, radius = w * 0.20f, center = Offset(cx, cy))
        // 8 rays
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45).toDouble())
            val r1 = w * 0.30f
            val r2 = w * 0.44f
            drawLine(
                color,
                Offset(
                    cx + (r1 * cos(a)).toFloat(),
                    cy + (r1 * sin(a)).toFloat()
                ),
                Offset(
                    cx + (r2 * cos(a)).toFloat(),
                    cy + (r2 * sin(a)).toFloat()
                ),
                strokeWidth = w * 0.07f, cap = StrokeCap.Round
            )
        }
    }
}

/** Transparent controls: touch/remote feedback changes text colour, never adds a box. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.playerControl(
    onLongClick: (() -> Unit)? = null,
    onActive: (Boolean) -> Unit = {},
    onClick: () -> Unit
): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, pressed) { onActive(focused || pressed) }
    return this.onFocusChanged { focused = it.isFocused }
        .combinedClickable(interactionSource = interactions, indication = null,
            role = Role.Button, onClick = onClick, onLongClick = onLongClick)
}

/** Black glyph outline + white/gold fill remains legible over light and dark video. */
@Composable
private fun PlayerLabel(
    label: String,
    color: Color = Color.White,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    val labelModifier = if (LocalGlassContent.current) modifier else
        modifier.glassCapsule(corner = 16.dp).padding(horizontal = 6.dp, vertical = 2.dp)
    Box(labelModifier) {
        PlainText(label, color = Color.Black, fontSize = fontSize, fontWeight = fontWeight,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            style = TextStyle(drawStyle = Stroke(width = 4f, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
        PlainText(label, color = color, fontSize = fontSize, fontWeight = fontWeight,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 1f), 3f)))
    }
}

@Composable
private fun TransportBtn(
    label: String? = null,
    icon: (@Composable () -> Unit)? = null,
    size: Dp = 56.dp,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    GlassContent {
    var active by remember { mutableStateOf(false) }
    val color = if (active) PlayerFocusRing else Color.White
    Box(
        Modifier.size(size).semantics { contentDescription = label ?: "Playback control" }
            .playerControl(onLongClick = onLongClick, onActive = { active = it }, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            if (icon != null) icon()
            else PlayerLabel(label ?: "", color, 22.sp)
        }
    }

    }
}

@Composable
private fun InfoAction(
    label: String,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassContent {
        var active by remember { mutableStateOf(false) }
        Box(
            modifier.heightIn(min = 48.dp).widthIn(min = 44.dp)
                .playerControl(onActive = { active = it }, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.height(if (DzTv.isTv) 34.dp else 30.dp)
                    .glassCapsule(enabled = true)
                    .then(if (active) Modifier.border(1.dp, PlayerFocusRing,
                        RoundedCornerShape(percent = 50)) else Modifier)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                PlayerLabel(label, if (active) PlayerFocusRing else color,
                    if (DzTv.isTv) 13.sp else 12.sp, FontWeight.Medium)
            }
        }
    }
}

/** TV set drawn with Canvas — no emoji. */
@Composable
private fun TvIcon(color: Color = Color.White, iconSize: Dp = 22.dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width; val h = size.height
        val sw = w * 0.09f
        drawRoundRect(color, Offset(w * 0.08f, h * 0.18f),
            Size(w * 0.84f, h * 0.56f), CornerRadius(w * 0.08f),
            style = Stroke(sw))
        drawLine(color, Offset(w * 0.38f, h * 0.74f), Offset(w * 0.62f, h * 0.74f),
            strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.74f), Offset(w * 0.5f, h * 0.86f),
            strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, h * 0.88f), Offset(w * 0.68f, h * 0.88f),
            strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** Rewind (◀◀) drawn with Canvas — plain white, no emoji. */
@Composable
private fun RewindIcon(color: Color = Color.White) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        drawPath(Path().apply {
            moveTo(w * 0.52f, h * 0.2f)
            lineTo(w * 0.16f, h * 0.5f)
            lineTo(w * 0.52f, h * 0.8f)
            close()
        }, color)
        drawPath(Path().apply {
            moveTo(w * 0.86f, h * 0.2f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.86f, h * 0.8f)
            close()
        }, color)
    }
}

/** Fast-forward (▶▶) drawn with Canvas — plain white, no emoji. */
@Composable
private fun FastForwardIcon(color: Color = Color.White) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        drawPath(Path().apply {
            moveTo(w * 0.14f, h * 0.2f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.14f, h * 0.8f)
            close()
        }, color)
        drawPath(Path().apply {
            moveTo(w * 0.48f, h * 0.2f)
            lineTo(w * 0.84f, h * 0.5f)
            lineTo(w * 0.48f, h * 0.8f)
            close()
        }, color)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundBtn(
    label: String = "",
    onLongClick: (() -> Unit)? = null,
    labelColor: Color = Color.White,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    GlassContent {
    Box(
        Modifier.size(44.dp)
            .glassCapsule(corner = 22.dp)
            .clip(CircleShape)
            .background(Color(0xCC1D2A44))
            .tvFocus(corner = 22.dp)
            .focusable()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) icon()
        else Text(
            label, color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold, maxLines = 1
        )
    }

    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, color = Color.White, fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun TrackRow(icon: String, label: String, selected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassContent {
        var active by remember { mutableStateOf(false) }
        Box(modifier.fillMaxWidth().heightIn(min = 48.dp)
            .playerControl(onActive = { active = it }, onClick = onClick),
            contentAlignment = Alignment.CenterStart) {
            Row(Modifier.fillMaxWidth().heightIn(min = if (DzTv.isTv) 34.dp else 24.dp)
                .glassCapsule(selected = selected)
                .then(if (active) Modifier.border(if (DzTv.isTv) 2.dp else 1.dp, PlayerFocusRing,
                    RoundedCornerShape(percent = 50)) else Modifier)
                .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                PlayerLabel(icon, Color.White, 11.sp)
                Spacer(Modifier.width(6.dp))
                PlayerLabel(label, if (active) PlayerFocusRing else Color.White,
                    if (DzTv.isTv) 14.sp else 11.sp,
                    modifier = Modifier.weight(1f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                if (selected) PlayerLabel("✓", Color.White, 11.sp)
            }
        }
    }
}

/** Transparent feature popup: only individual text/action capsules cover the video. */
@Composable
private fun PlayerFeatureDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    initialFocus: FocusRequester? = null
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp - 24).coerceAtLeast(120).dp
    Dialog(onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                it.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                it.setDimAmount(0f)
                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                it.navigationBarColor = android.graphics.Color.BLACK
                if (Build.VERSION.SDK_INT >= 29) it.isNavigationBarContrastEnforced = false
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, false)
                androidx.core.view.WindowInsetsControllerCompat(it, it.decorView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    isAppearanceLightNavigationBars = false
                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        val fallbackFr = remember { FocusRequester() }
        val firstFr = if (DzTv.isTv) initialFocus ?: fallbackFr else fallbackFr
        LaunchedEffect(Unit) {
            delay(100)
            try { firstFr.requestFocus() } catch (_: Exception) {}
        }
        Column(Modifier.widthIn(max = if (DzTv.isTv) 460.dp else 380.dp).fillMaxWidth(0.92f)
            .heightIn(max = maxHeight).padding(8.dp)
            .then(if (!DzTv.isTv || initialFocus == null) Modifier.focusRequester(fallbackFr) else Modifier)
            .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(
                fontSize = if (DzTv.isTv) 15.sp else 14.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(Color.Black, Offset(0f, 1f), 3f))) { title() }
            Box(Modifier.weight(1f, fill = false)) {
                androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(
                    fontSize = if (DzTv.isTv) 12.sp else 11.sp, lineHeight = 16.sp,
                    shadow = Shadow(Color.Black, Offset(0f, 1f), 3f))) { text() }
            }
            Row(Modifier.align(Alignment.End)) { confirmButton() }
        }
    }
}

/** The phone keeps its touch layout; TV requesters attach to actual clickable targets. */
private fun Modifier.tvPanelTarget(requester: FocusRequester): Modifier =
    if (DzTv.isTv) this.focusRequester(requester) else this

/** Fixed TV rows: predictable neighbours, no sideways escape or FlowRow wrap guesses. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TvChoiceGrid(
    labels: List<String>, selectedIndex: Int, initialFocus: FocusRequester,
    columns: Int = 4,
    upTarget: FocusRequester = FocusRequester.Cancel,
    downTarget: FocusRequester = FocusRequester.Default,
    onChoose: (Int) -> Unit
) {
    if (labels.isEmpty()) return
    val entryIndex = selectedIndex.coerceIn(0, labels.lastIndex)
    val targets = remember(labels.size, entryIndex, initialFocus) {
        List(labels.size) { if (it == entryIndex) initialFocus else FocusRequester() }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.indices.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { index ->
                    ScaleModeChoice(labels[index], selectedIndex == index,
                        Modifier.weight(1f).focusRequester(targets[index]).focusProperties {
                            left = if (index > row.first()) targets[index - 1] else FocusRequester.Cancel
                            right = if (index < row.last()) targets[index + 1] else FocusRequester.Cancel
                            up = if (index >= columns) targets[index - columns] else upTarget
                            down = if (index / columns < labels.lastIndex / columns)
                                targets[(index + columns).coerceAtMost(labels.lastIndex)] else downTarget
                        }) { onChoose(index) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TvVideoScaleDialog(
    videoScale: Float, aspectMode: Int,
    onScale: (Float) -> Unit, onMode: (Int) -> Unit, onDismiss: () -> Unit
) {
    val ratioFr = remember { FocusRequester() }
    val scaleFr = remember { FocusRequester() }
    val closeFr = remember { FocusRequester() }
    val percents = (70..140 step 5).toList()
    PlayerFeatureDialog(
        onDismissRequest = onDismiss, initialFocus = ratioFr,
        title = { Text("Video scale mode", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlayerLabel("Ratio", PlayerSecondary, 12.sp)
                TvChoiceGrid((0 until VideoScaleModes.COUNT).map { VideoScaleModes.label(it) },
                    aspectMode, ratioFr, downTarget = scaleFr, onChoose = onMode)
                PlayerLabel("Scale", PlayerSecondary, 12.sp)
                TvChoiceGrid(percents.map { "$it%" },
                    percents.indexOfFirst { abs(videoScale - it / 100f) < 0.001f },
                    scaleFr, columns = 5, upTarget = ratioFr, downTarget = closeFr
                ) { onScale(percents[it] / 100f) }
            }
        },
        confirmButton = {
            ScaleModeChoice("Close", false, Modifier.focusRequester(closeFr).focusProperties {
                up = scaleFr; down = FocusRequester.Cancel
                left = FocusRequester.Cancel; right = FocusRequester.Cancel
            }, onClick = onDismiss)
        }
    )
}

/** Small floating choices only; neither this dialog nor its window dims the video. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoScaleDialog(
    videoScale: Float,
    aspectMode: Int,
    onScale: (Float) -> Unit,
    onMode: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (DzTv.isTv) {
        TvVideoScaleDialog(videoScale, aspectMode, onScale, onMode, onDismiss)
        return
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp - 24).coerceAtLeast(120).dp
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        val firstFr = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(100)
            try { firstFr.requestFocus() } catch (_: Exception) {}
        }
        GlassContent {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth(0.94f)
                    .heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PlayerLabel("Video scale mode", Color.White, 14.sp, FontWeight.Medium)
                PlayerLabel("Ratio", PlayerSecondary, 10.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (0 until VideoScaleModes.COUNT).forEach { mode ->
                        ScaleModeChoice(VideoScaleModes.label(mode), aspectMode == mode,
                            modifier = if (mode == 0) Modifier.focusRequester(firstFr) else Modifier
                        ) { onMode(mode) }
                    }
                }
                PlayerLabel("Scale", PlayerSecondary, 10.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (70..140 step 5).forEach { percent ->
                        val value = percent / 100f
                        ScaleModeChoice("$percent%", abs(videoScale - value) < 0.001f) { onScale(value) }
                    }
                }
                ScaleModeChoice("Close", false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ScaleModeChoice(
    label: String, selected: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var active by remember { mutableStateOf(false) }
    Box(modifier.heightIn(min = 48.dp).widthIn(min = 44.dp)
        .playerControl(onActive = { active = it }, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Box(Modifier.height(if (DzTv.isTv) 34.dp else 24.dp)
            .glassCapsule(selected = selected)
            .then(if (active) Modifier.border(if (DzTv.isTv) 2.dp else 1.dp, PlayerFocusRing,
                RoundedCornerShape(percent = 50)) else Modifier)
            .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            PlayerLabel(label, if (active) PlayerFocusRing else if (selected) Accent else Color.White,
                if (DzTv.isTv) 13.sp else 10.sp, FontWeight.Medium)
        }
    }
}

@Composable
private fun PlayerOptionChip(label: String, selected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    ScaleModeChoice(label, selected, modifier = modifier, onClick = onClick)
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    // borderless text chip — button boxes removed per user request
    Box(
        Modifier.glassCapsule(selected = selected, corner = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Transparent)
            .tvFocus(corner = 20.dp)
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Accent else Dim,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).toInt()
    val m = s / 60
    val h = m / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60)
    else "%02d:%02d".format(m, s)
}


private data class SettingsShortcut(val label: String, val description: String, val action: () -> Unit)

/** One compact, horizontally scrollable shortcut row below the info box. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PlayerSettingsOptions(
    modifier: Modifier = Modifier,
    settingsFirstFr: FocusRequester,
    gearFr: FocusRequester,
    onActionFocus: (FocusRequester) -> Unit,
    speed: Float,
    muted: Boolean,
    aspectMode: Int,
    showCatchup: Boolean,
    onSubtitles: () -> Unit,
    onSleep: () -> Unit,
    onSpeed: () -> Unit,
    onAudio: () -> Unit,
    onExternal: () -> Unit,
    onMute: () -> Unit,
    onAspect: () -> Unit,
    onCatchup: () -> Unit
) {
    val shortcuts = listOf(
        SettingsShortcut("CC", "Subtitles", onSubtitles),
        SettingsShortcut("Zzz", "Sleep timer", onSleep),
        SettingsShortcut(if (speed == 1f) "1×" else "${speed}×", "Playback speed", onSpeed),
        SettingsShortcut("AUD", "Audio tracks", onAudio),
        SettingsShortcut("EXT", "External player", onExternal),
        SettingsShortcut(if (muted) "SND" else "MUTE", if (muted) "Unmute" else "Mute", onMute),
        SettingsShortcut(VideoScaleModes.shortLabel(aspectMode), "Aspect ratio", onAspect)
    ) + (if (showCatchup) listOf(SettingsShortcut("CU", "Catch-up", onCatchup)) else emptyList())
    val shortcutFocus = remember(shortcuts.size, settingsFirstFr) {
        List(shortcuts.size) { if (it == 0) settingsFirstFr else FocusRequester() }
    }
    // A single non-wrapping row; narrow screens scroll sideways instead of adding rows.
    Row(
        modifier.fillMaxWidth().background(Color.Transparent)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shortcuts.forEachIndexed { index, item ->
            SettingsDrawerShortcut(if (DzTv.isTv) item.description else item.label, item.description,
                modifier = Modifier.focusRequester(shortcutFocus[index]).focusProperties {
                    left = if (index > 0) shortcutFocus[index - 1] else FocusRequester.Cancel
                    right = if (index + 1 < shortcuts.size) shortcutFocus[index + 1] else FocusRequester.Cancel
                    up = gearFr
                    down = FocusRequester.Cancel
                }, onClick = {
                    onActionFocus(shortcutFocus[index])
                    item.action()
                })
        }
    }
}

/** Slim visual capsule inside a generous touch/remote target, without a panel backplate. */
@Composable
private fun SettingsDrawerShortcut(
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var active by remember { mutableStateOf(false) }
    val capsuleShape = RoundedCornerShape(percent = 50)
    // Keep the touch/remote target generous while the visible capsule stays slim.
    Box(modifier.heightIn(min = 48.dp).widthIn(min = 44.dp)
        .semantics(mergeDescendants = true) { contentDescription = description }
        .playerControl(onActive = { active = it }, onClick = onClick)
        .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.height(if (DzTv.isTv) 34.dp else 24.dp)
            .clip(capsuleShape)
            .background(Color(0xFF101827).copy(alpha = if (active) 0.62f else 0.46f))
            .background(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (active) 0.20f else 0.14f),
                Color.White.copy(alpha = 0.025f))))
            .border(if (active) 1.3.dp else 0.7.dp,
                Brush.verticalGradient(listOf(
                    if (active) PlayerFocusRing.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.46f),
                    if (active) PlayerFocusRing.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.16f))),
                capsuleShape)
            .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            PlainText(label, color = if (active) PlayerFocusRing else Color.White,
                fontSize = if (DzTv.isTv) 13.sp else 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 1f), 2f)),
                modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/**
 * Right-side channel drawer, extracted from PlayerScreen (which grew too large
 * for the dex verifier). Fully transparent; lists channels with category filter.
 */
@Composable
private fun ChannelDrawer(
    modifier: Modifier = Modifier,
    drawerChannels: List<Channel>,
    drawerCat: String?,
    onCatChange: (String?) -> Unit,
    drawerAccounts: List<PlAccount>,
    drawerAccountId: String,
    drawerLoading: Boolean,
    current: Channel,
    isPortrait: Boolean,
    drawerFirstFr: FocusRequester,
    scope: CoroutineScope,
    onChannelClick: (Channel) -> Unit,
    onClose: () -> Unit,
    onProviderPick: () -> Unit,
    onLoadProvider: (PlAccount) -> Unit
) {
    // The transparent full-screen sibling catches outside taps while the drawer
    // itself remains interactive and visually unobtrusive over the video.
    Box(
        Modifier.fillMaxSize().clickable { onClose() }
    )

    val drawerCats = remember(drawerChannels) { drawerCategories(drawerChannels) }
    val drawerList = remember(drawerChannels, drawerCat) {
        drawerCat?.let { category -> drawerChannels.filter { it.getSmartCat() == category } }
            ?: drawerChannels
    }
    val listState = rememberLazyListState()

    // Preserve the existing open behavior: bring the currently playing item into view.
    LaunchedEffect(drawerChannels, drawerCat) {
        val idx = drawerList.indexOfFirst {
            it.url == current.url && it.name == current.name
        }
        if (idx >= 0) {
            try { listState.scrollToItem(idx) } catch (_: Exception) {}
        }
    }

    val drawerAccountName = remember(drawerAccounts, drawerAccountId) {
        drawerAccounts.find { it.id == drawerAccountId }
            ?.let { if (it.name.isNotEmpty()) it.name else "Playlist" }
            ?: "Playlist"
    }
    val drawerTextStyle = TextStyle(
        shadow = Shadow(
            color = Color(0xCC000000),
            offset = Offset(1f, 2f),
            blurRadius = 6f
        )
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(if (isPortrait) 0.4f else 0.25f)
            .background(Color.Transparent)
    ) {
        // No title/header reservation: the provider selector and compact close
        // control occupy the first available row at the top of the drawer.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.weight(1f)
                    .heightIn(min = 48.dp)
                    .tvFocus(corner = 12.dp)
                    .focusable()
                    .clickable { onProviderPick() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlainText(
                        "Provider",
                        color = Dim,
                        fontSize = 12.sp,
                        style = drawerTextStyle
                    )
                    Spacer(Modifier.width(7.dp))
                    PlainText(
                        drawerAccountName,
                        color = Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = drawerTextStyle,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(5.dp))
                    PlainText("▾", color = Accent, fontSize = 13.sp, style = drawerTextStyle)
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(48.dp)
                    .glassCapsule(corner = 24.dp)
                    .tvFocus(corner = 24.dp)
                    .focusable()
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                PlainText("✕", color = Color.White, fontSize = 16.sp, style = drawerTextStyle)
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                DrawerCategoryChip("All", drawerCat == null) { onCatChange(null) }
            }
            items(drawerCats) { (name, _) ->
                DrawerCategoryChip(name, drawerCat == name) {
                    onCatChange(if (drawerCat == name) null else name)
                }
            }
        }

        if (drawerLoading) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Accent) }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(drawerList, key = { _, channel -> channel.name + channel.url }) { idx, channel ->
                    val selected = channel.url == current.url && channel.name == current.name
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            // Deliberately no CardSel/CardBg or other opaque backplate.
                            .then(if (idx == 0) Modifier.focusRequester(drawerFirstFr) else Modifier)
                            .tvFocus(corner = 8.dp)
                            .focusable()
                            .clickable { onChannelClick(channel) }
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selected) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape).background(Accent)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        PlainText(
                            channel.name,
                            color = if (selected) Accent else Ink,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            style = drawerTextStyle
                        )
                    }
                }
            }
        }
    }
}
