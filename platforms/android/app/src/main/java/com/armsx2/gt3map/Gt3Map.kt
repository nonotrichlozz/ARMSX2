// SPDX-FileCopyrightText: 2026 nonotrichlozz, contributors to the ARMSX2 fork
// SPDX-License-Identifier: GPL-3.0+
//
// Gran Turismo 3: the in-game mini-map, cropped from the game picture and shown enlarged on the
// second display (Retroid Pocket Duo). See GT3-CARTE.md at the repository root.
//
// Isolated on purpose: outside this file there are only a few lines marked "GT3 map fork" in
// SecondScreenTiles.kt (the tile entry), SecondScreen.kt (tile size, cover fallback, the optional
// full-screen mode) and AppTab.kt (the settings block).
package com.armsx2.gt3map

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.PixelCopy
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.armsx2.EmuState
import com.armsx2.SecondScreen
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.settings.IntSliderRow
import com.armsx2.ui.settings.SegmentedRow
import com.armsx2.ui.settings.ToggleRow
import kr.co.iefriends.pcsx2.NativeApp

object Gt3Map {
    const val GT3_SERIAL = "SCES-50294"

    // Manual crop, in % of the game surface (black bars included). Measured on a real capture
    // (4:3 game centred on the Duo's 16:9 screen): map at x 15–33 %, y 19–33 %, plus a margin.
    private val DEFAULTS = intArrayOf(14, 34, 17, 35)

    /** Old mode: the map replaces the whole panel. Off by default; the map is a panel tile now. */
    val replacePanel = mutableStateOf(false)
    val gt3Only = mutableStateOf(true)
    val smooth = mutableStateOf(false)
    /** Find the map by itself instead of using the manual zone. */
    val autoFrame = mutableStateOf(true)
    /** Draw only the track and the car dots, on black, instead of the game picture. */
    val blackBackground = mutableStateOf(true)
    /** 0 small, 1 medium, 2 large. */
    val size = mutableIntStateOf(2)
    /** x0, x1, y0, y1 in percent of the game surface (manual mode). */
    val crop = Array(4) { mutableIntStateOf(DEFAULTS[it]) }

    private const val CAPTURE_MS = 50L   // ~20 captures/s: every 2–3 game frames
    private const val WATCH_MS = 1000L
    private const val EDIT_GRACE_MS = 3000L
    /** Captures are scaled down to at most this width: still above the PS2's own 640 px. */
    private const val MAX_CAPTURE_W = 640
    /** No dot for this long: no map on screen (menus, garage, loading). */
    private const val NO_DOTS_HIDE_MS = 2000L
    // Auto mode, in fractions of the searched area (top-left quarter of the surface): the box
    // shown around the car dots until the track outline has been found (car still standing).
    private const val MIN_W = 0.34f
    private const val MIN_H = 0.29f
    private const val MARGIN = 0.08f
    private const val SMOOTHING = 0.3f   // share of the way to the new box per analysis (10/s)

    private val handler = Handler(Looper.getMainLooper())
    private var watching = false
    private var lastActive = false
    private var lastEdit = 0L

    /** A game is running and it is one the map applies to. Refreshed once a second. */
    @Volatile var gameMatches = false
        private set

    private val prefs get() = MainActivityRuntime.prefs

    /** Called from SecondScreen.load(). */
    fun load() {
        runCatching {
            replacePanel.value = prefs.getBoolean("gt3map.replacePanel", false)
            gt3Only.value = prefs.getBoolean("gt3map.gt3Only", true)
            smooth.value = prefs.getBoolean("gt3map.smooth", false)
            autoFrame.value = prefs.getBoolean("gt3map.autoFrame", true)
            blackBackground.value = prefs.getBoolean("gt3map.blackBackground", true)
            size.intValue = prefs.getInt("gt3map.size", 2).coerceIn(0, 2)
            crop.forEachIndexed { i, s -> s.intValue = prefs.getInt("gt3map.crop$i", DEFAULTS[i]) }
        }
        if (!watching) { watching = true; handler.post(watch) }
    }

    /** Should the second display show the map INSTEAD of the panel (old mode), right now? */
    fun active(): Boolean = replacePanel.value && gameMatches

    private fun computeGameMatches(): Boolean {
        val state = MainActivityRuntime.eState.value
        if (state != EmuState.RUNNING && state != EmuState.PAUSED) return false
        if (!gt3Only.value) return true
        val serial = runCatching { NativeApp.getGameSerial() }.getOrNull().orEmpty()
        return serial.equals(GT3_SERIAL, ignoreCase = true)
    }

    /** Game start/stop does not re-run SecondScreen's display logic, so watch for the switch. */
    private val watch = object : Runnable {
        override fun run() {
            gameMatches = computeGameMatches()
            val now = active()
            if (now != lastActive) { lastActive = now; SecondScreen.rebuild() }
            handler.postDelayed(this, WATCH_MS)
        }
    }

    private fun save(key: String, v: Boolean) = runCatching { prefs.edit().putBoolean(key, v).apply() }
    private fun saveInt(key: String, v: Int) = runCatching { prefs.edit().putInt(key, v).apply() }

    fun setCrop(i: Int, value: Int) {
        // Keep left < right and top < bottom.
        val v = when (i) {
            0, 2 -> value.coerceIn(0, crop[i + 1].intValue - 1)
            else -> value.coerceIn(crop[i - 1].intValue + 1, 100)
        }
        crop[i].intValue = v
        lastEdit = SystemClock.uptimeMillis()
        saveInt("gt3map.crop$i", v)
    }

    /** While the zone is being adjusted, show the raw crop even if no car dot is seen. */
    private fun editing() = SystemClock.uptimeMillis() - lastEdit < EDIT_GRACE_MS

    // ---- Tile size in the panel grid -------------------------------------------------------

    /** Columns the tile spans: small 1, medium 2, large the whole row. */
    fun tileSpan(columns: Int): Int = when (size.intValue) {
        0 -> 1
        1 -> 2
        else -> columns
    }.coerceIn(1, columns.coerceAtLeast(1))

    /** Tile height: a quarter, a third or half of the second display's height. */
    fun tileHeightPx(displayHeight: Int): Int =
        displayHeight / when (size.intValue) { 0 -> 4; 1 -> 3; else -> 2 }

    // ---- Settings --------------------------------------------------------------------------

    /** The settings block, shown in App settings under the panel layout editor. */
    @Composable
    fun Settings() {
        Text("Carte GT3", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Ajoutez la case « Carte GT3 » dans la disposition du panneau ci-dessus.",
            style = MaterialTheme.typography.bodySmall,
        )
        SegmentedRow(
            label = "Taille de la case Carte GT3",
            options = listOf("Petite", "Moyenne", "Grande"),
            selectedIndex = size.intValue,
            onChange = { size.intValue = it; saveInt("gt3map.size", it); SecondScreen.rebuild() },
        )
        ToggleRow(
            label = "Carte GT3 : uniquement pour GT3 ($GT3_SERIAL)",
            value = gt3Only.value,
            description = "Activé : la case est masquée dans les autres jeux.",
            onChange = { gt3Only.value = it; save("gt3map.gt3Only", it) },
        )
        ToggleRow(
            label = "Carte GT3 lissée",
            value = smooth.value,
            description = "Désactivé : pixels nets.",
            onChange = { smooth.value = it; save("gt3map.smooth", it) },
        )
        ToggleRow(
            label = "Cadrage automatique",
            value = autoFrame.value,
            description = "Trouve la carte toute seule. Désactivé : zone réglée à la main ci-dessous.",
            onChange = { autoFrame.value = it; save("gt3map.autoFrame", it) },
        )
        ToggleRow(
            label = "Carte GT3 sur fond noir",
            value = blackBackground.value,
            description = "Seulement le tracé et les voitures. Désactivé : l'image du jeu. Le tracé apparaît une fois la voiture en mouvement.",
            onChange = { blackBackground.value = it; save("gt3map.blackBackground", it) },
        )
        if (!autoFrame.value) {
            val names = arrayOf("Zone : gauche", "Zone : droite", "Zone : haut", "Zone : bas")
            names.forEachIndexed { i, name ->
                IntSliderRow(
                    label = name,
                    value = crop[i].intValue,
                    min = 0,
                    max = 100,
                    description = if (i == 0) "En % de l'image. Réglez en course : la case se met à jour en direct sur le 2ᵉ écran." else null,
                    valueFormatter = { "$it %" },
                    onReset = if (crop[i].intValue != DEFAULTS[i]) ({ setCrop(i, DEFAULTS[i]) }) else null,
                    onChange = { setCrop(i, it) },
                )
            }
        }
        ToggleRow(
            label = "Carte GT3 en plein écran (à la place du panneau)",
            value = replacePanel.value,
            description = "Ancien mode : pendant GT3, tout le 2ᵉ écran montre la carte.",
            onChange = { replacePanel.value = it; save("gt3map.replacePanel", it) },
        )
    }

    // ---- Views -----------------------------------------------------------------------------

    /** The panel tile. Hidden (GONE, so the grid closes up) when no matching game is running. */
    fun tileView(context: Context): View = MapView(context, inPanel = true).apply {
        val d = resources.displayMetrics.density
        background = GradientDrawable().apply { cornerRadius = 14 * d; setColor(0x66000000) }
        clipToOutline = true
    }

    /** Old mode: the whole second display. */
    class Screen(context: Context, display: Display) : Presentation(context, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setCancelable(false)   // same as SecondScreen's panel: BACK must not close it
            setContentView(MapView(context, inPanel = false).apply { setBackgroundColor(Color.BLACK) })
        }
    }

    /** Where to capture, in buffer pixels: the top-left quarter (auto) or the manual zone. */
    internal fun sourceRect(w: Int, h: Int): Rect? {
        val r = if (autoFrame.value) Rect(0, 0, w / 2, h / 2) else Rect(
            w * crop[0].intValue / 100, h * crop[2].intValue / 100,
            w * crop[1].intValue / 100, h * crop[3].intValue / 100,
        )
        return r.takeIf { it.width() > 0 && it.height() > 0 }
    }

    /**
     * GT3's car markers: saturated yellow and green dots. Measured on real race captures, where
     * nothing else in the top-left quarter (sky, clouds, stands, the orange "6me") matches.
     */
    internal fun isCarDot(c: Int): Boolean {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r >= 190 && g >= 190 && b <= 100) || (g >= 170 && g - r >= 40 && g - b >= 90)
    }

    /**
     * Finds the track outline and the car dots in one capture (plain pixel work, no views).
     *
     * The outline is a faint see-through grey, as bright as the walls and clouds behind it, so
     * colour alone cannot find it. Two things do, measured on real captures: it is a thin line
     * lighter than both its sides, and it never moves while the scenery behind it does. So each
     * pixel scores "thin light line here" over time and only lines that stay put survive; then
     * the line touching the car dots is the track. While the car stands still the scenery stays
     * too, so the outline only comes out clean once the car moves.
     */
    internal class Analyzer {
        var w = 0
            private set
        var h = 0
            private set
        private var lum = IntArray(0)
        private var score = IntArray(0)        // 0..1000, how steadily a thin light line is here
        private var track = BooleanArray(0)    // pixels of the outline found this time
        private var queue = IntArray(0)
        private var dotPx = IntArray(0)
        private var dotCount = 0
        /** Bounding boxes in capture pixels, null when not found. */
        var dots: Rect? = null
            private set
        var trackBox: Rect? = null
            private set

        fun analyse(px: IntArray, w: Int, h: Int) {
            val n = w * h
            if (w != this.w || h != this.h) {
                this.w = w; this.h = h
                lum = IntArray(n); score = IntArray(n); track = BooleanArray(n); queue = IntArray(n); dotPx = IntArray(n)
            }
            // Dots, and brightness.
            dotCount = 0
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
            for (i in 0 until n) {
                val c = px[i]
                lum[i] = (((c shr 16) and 0xFF) + 2 * ((c shr 8) and 0xFF) + (c and 0xFF)) shr 2
                if (isCarDot(c)) {
                    dotPx[dotCount++] = i
                    val x = i % w; val y = i / w
                    if (x < x0) x0 = x
                    if (x > x1) x1 = x
                    if (y < y0) y0 = y
                    if (y > y1) y1 = y
                }
            }
            dots = if (dotCount >= 3) Rect(x0, y0, x1 + 1, y1 + 1) else null

            // "Thin light line" test, kept up over time: +20 per hit, -2 % per capture, so a line
            // must stay ~2 s to count and scenery that stops matching fades in ~1 s.
            val k = maxOf(2, w * 4 / 640)   // the line is ~4 px wide at 640 px
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                var hit = false
                if (x >= k && x < w - k && y >= k && y < h - k) {
                    val c = lum[i]
                    val p = px[i]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val sat = maxOf(r, g, b) - minOf(r, g, b)
                    if (c > 60 && sat < 40) {
                        val hor = c - maxOf(lum[i - k], lum[i + k])
                        val ver = c - maxOf(lum[i - k * w], lum[i + k * w])
                        hit = hor > 18 || ver > 18
                    }
                }
                score[i] = score[i] - score[i] / 50 + (if (hit) 20 else 0)
                track[i] = false
            }

            // The track: steady lines connected (gaps of up to 2 px allowed) to a car dot.
            trackBox = null
            val d = dots ?: return
            var head = 0
            var tail = 0
            val reach = k + 2
            for (y in maxOf(0, d.top - reach) until minOf(h, d.bottom + reach))
                for (x in maxOf(0, d.left - reach) until minOf(w, d.right + reach)) {
                    val i = y * w + x
                    if (score[i] >= 600 && !track[i]) { track[i] = true; queue[tail++] = i }
                }
            if (tail == 0) return
            x0 = w; y0 = h; x1 = -1; y1 = -1
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                for (dy in -2..2) for (dx in -2..2) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (!track[j] && score[j] >= 600) { track[j] = true; queue[tail++] = j }
                }
            }
            trackBox = Rect(x0, y0, x1 + 1, y1 + 1)
        }

        /** The black-background picture: the track in light grey and the dots in their colours. */
        fun draw(px: IntArray, out: IntArray) {
            val n = w * h
            java.util.Arrays.fill(out, 0, n, Color.BLACK)
            for (i in 0 until n) if (track[i]) {
                out[i] = TRACK_COLOR
                // 1 px thicker, so it still reads once enlarged.
                if (i % w + 1 < w) out[i + 1] = TRACK_COLOR
                if (i + w < n) out[i + w] = TRACK_COLOR
            }
            for (j in 0 until dotCount) {
                val i = dotPx[j]
                val c = px[i] or 0xFF000000.toInt()
                out[i] = c
                if (i % w + 1 < w) out[i + 1] = c
                if (i + w < n) out[i + w] = c
            }
        }
    }

    private const val TRACK_COLOR = 0xFFE6E6E6.toInt()

    /**
     * The box to show, in capture pixels: [found] at least [minW]×[minH], plus an 8 % margin,
     * kept inside the [w]×[h] capture.
     */
    internal fun targetBox(found: Rect, minW: Float, minH: Float, w: Int, h: Int): RectF {
        val bw = (maxOf(found.width().toFloat(), minW) * (1 + 2 * MARGIN)).coerceAtMost(w.toFloat())
        val bh = (maxOf(found.height().toFloat(), minH) * (1 + 2 * MARGIN)).coerceAtMost(h.toFloat())
        val cx = found.exactCenterX().coerceIn(bw / 2, w - bw / 2)
        val cy = found.exactCenterY().coerceIn(bh / 2, h - bh / 2)
        return RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2)
    }

    /** Analysis runs here, off the UI thread and off the emulator's threads. */
    private val worker: Handler by lazy {
        Handler(android.os.HandlerThread("gt3map").apply { start() }.looper)
    }

    /** What the view draws: a finished capture, swapped in whole. */
    private class Frame(val picture: Bitmap, val box: Rect, val hasMap: Boolean)

    private class MapView(context: Context, private val inPanel: Boolean) : View(context) {
        // Two of each buffer: one being filled on the worker while the other is on screen.
        private val captures = arrayOfNulls<Bitmap>(2)
        private val pictures = arrayOfNulls<Bitmap>(2)
        private var slot = 0
        private var pixels = IntArray(0)
        private var out = IntArray(0)
        private val analyzer = Analyzer()
        @Volatile private var busy = false
        @Volatile private var running = false
        @Volatile private var frame: Frame? = null
        private var lastDotsAt = 0L
        private val box = RectF()   // smoothed, capture pixels; empty = not set yet
        private val paint = Paint()
        private val dst = RectF()

        private val tick = object : Runnable {
            override fun run() {
                if (!running) return
                if (inPanel) visibility = if (gameMatches) VISIBLE else GONE
                if (visibility == VISIBLE) capture()
                handler.postDelayed(this, CAPTURE_MS)
            }
        }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); running = true; handler.post(tick) }
        override fun onDetachedFromWindow() { running = false; handler.removeCallbacks(tick); super.onDetachedFromWindow() }

        private fun capture() {
            if (busy) return
            val sv = MainActivityRuntime.surface.value ?: return
            val holder = sv.holder
            if (!holder.surface.isValid) return
            val fr = holder.surfaceFrame   // buffer size, which can differ from the view size
            val src = sourceRect(fr.width(), fr.height()) ?: return
            // PixelCopy scales into the bitmap, so a big source costs no more to analyse.
            val scale = minOf(1f, MAX_CAPTURE_W.toFloat() / src.width())
            val bw = (src.width() * scale).toInt().coerceAtLeast(1)
            val bh = (src.height() * scale).toInt().coerceAtLeast(1)
            val s = slot
            val bmp = captures[s]?.takeIf { it.width == bw && it.height == bh }
                ?: Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { captures[s] = it }
            busy = true
            runCatching {
                PixelCopy.request(sv, src, bmp, { result ->
                    if (result == PixelCopy.SUCCESS && running) runCatching { process(bmp, s) }
                    busy = false
                }, worker)
            }.onFailure { busy = false }   // surface went away between the check and the call
        }

        /** On the worker thread. */
        private fun process(bmp: Bitmap, s: Int) {
            val w = bmp.width
            val h = bmp.height
            if (w != analyzer.w || h != analyzer.h) box.setEmpty()   // mode or zone changed
            if (pixels.size != w * h) { pixels = IntArray(w * h); out = IntArray(w * h) }
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            analyzer.analyse(pixels, w, h)
            val now = SystemClock.uptimeMillis()
            val dots = analyzer.dots
            if (dots != null) lastDotsAt = now

            // The box: the track, else the map's usual size around the dots, else the last one.
            if (!autoFrame.value) {
                box.set(0f, 0f, w.toFloat(), h.toFloat())
            } else {
                val track = analyzer.trackBox
                val target = when {
                    track != null -> targetBox(Rect(track).apply { if (dots != null) union(dots) }, 0f, 0f, w, h)
                    dots != null -> targetBox(dots, MIN_W * w, MIN_H * h, w, h)
                    else -> null
                }
                if (target != null) {
                    if (box.isEmpty) box.set(target) else box.set(
                        box.left + (target.left - box.left) * SMOOTHING,
                        box.top + (target.top - box.top) * SMOOTHING,
                        box.right + (target.right - box.right) * SMOOTHING,
                        box.bottom + (target.bottom - box.bottom) * SMOOTHING,
                    )
                }
            }

            val picture = if (blackBackground.value) {
                analyzer.draw(pixels, out)
                val p = pictures[s]?.takeIf { it.width == w && it.height == h }
                    ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { pictures[s] = it }
                p.setPixels(out, 0, w, 0, 0, w, h)
                p
            } else bmp
            val shown = Rect().also { box.roundOut(it); it.intersect(0, 0, w, h) }
            val hasMap = editing() || now - lastDotsAt < NO_DOTS_HIDE_MS
            frame = Frame(picture, shown, hasMap && !shown.isEmpty)
            slot = 1 - s
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val f = frame ?: return
            // No map (menus, garage, loading): leave the plain tile ground, not bits of the picture.
            if (!f.hasMap) return
            if (blackBackground.value) canvas.drawColor(Color.BLACK)
            paint.isFilterBitmap = smooth.value
            val src = f.box
            val scale = minOf(width.toFloat() / src.width(), height.toFloat() / src.height())
            val w = src.width() * scale
            val h = src.height() * scale
            dst.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            canvas.drawBitmap(f.picture, src, dst, paint)
        }
    }
}
