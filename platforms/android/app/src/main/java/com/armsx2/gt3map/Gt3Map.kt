// SPDX-FileCopyrightText: 2026 nonotrichlozz, contributors to the ARMSX2 fork
// SPDX-License-Identifier: GPL-3.0+
//
// Gran Turismo 3: the in-game mini-map, cropped from the game picture and shown enlarged on the
// second display (Retroid Pocket Duo). See GT3-CARTE.md at the repository root.
//
// Isolated on purpose: outside this file there are only a few lines marked "GT3 map fork" in
// SecondScreenTiles.kt (the tile entry), SecondScreen.kt (tile size + the optional full-screen
// mode) and AppTab.kt (the settings block).
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

    // Default crop, in % of the game surface (black bars included): x 4→29 %, y 18→35 %.
    private val DEFAULTS = intArrayOf(4, 29, 18, 35)
    private const val DEFAULT_WHITE_PERMILLE = 5

    /** Old mode: the map replaces the whole panel. Off by default; the map is a panel tile now. */
    val replacePanel = mutableStateOf(false)
    val gt3Only = mutableStateOf(true)
    val smooth = mutableStateOf(false)
    /** 0 small, 1 medium, 2 large. */
    val size = mutableIntStateOf(2)
    /** x0, x1, y0, y1 in percent of the game surface. */
    val crop = Array(4) { mutableIntStateOf(DEFAULTS[it]) }
    /** Map shown only if at least this many ‰ of the crop is near-white (the track outline). 0 = always. */
    val whitePermille = mutableIntStateOf(DEFAULT_WHITE_PERMILLE)

    private const val CAPTURE_MS = 50L   // ~20 captures/s: every 2–3 game frames
    private const val WATCH_MS = 1000L
    private const val EDIT_GRACE_MS = 3000L
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
            size.intValue = prefs.getInt("gt3map.size", 2).coerceIn(0, 2)
            whitePermille.intValue = prefs.getInt("gt3map.whitePermille", DEFAULT_WHITE_PERMILLE)
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

    /** While the zone is being adjusted, show the raw crop even if no outline is detected. */
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
        IntSliderRow(
            label = "Détection du tracé (‰ de blanc)",
            value = whitePermille.intValue,
            min = 0,
            max = 50,
            description = "Sous ce seuil de pixels blancs, la case affiche « Pas de carte ». 0 = toujours afficher.",
            valueFormatter = { "$it ‰" },
            onReset = if (whitePermille.intValue != DEFAULT_WHITE_PERMILLE) ({
                whitePermille.intValue = DEFAULT_WHITE_PERMILLE; saveInt("gt3map.whitePermille", DEFAULT_WHITE_PERMILLE); Unit
            }) else null,
            onChange = { whitePermille.intValue = it; saveInt("gt3map.whitePermille", it) },
        )
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

    /** The crop in buffer pixels, or null if it is empty. */
    internal fun cropRect(w: Int, h: Int): Rect? {
        val r = Rect(
            w * crop[0].intValue / 100, h * crop[2].intValue / 100,
            w * crop[1].intValue / 100, h * crop[3].intValue / 100,
        )
        return r.takeIf { it.width() > 0 && it.height() > 0 }
    }

    /**
     * Is the mini-map on screen? GT3 draws the track outline in white, so count near-white pixels
     * (every 2nd pixel in both directions). Too few: menus, garage, loading. Too many (> 40 %): a
     * white menu background, not a track.
     * ponytail: plain brightness test; if menus fool it, also require the outline's shape.
     */
    internal fun hasTrackOutline(px: IntArray, w: Int, h: Int, permille: Int): Boolean {
        if (permille <= 0) return true
        var white = 0
        var total = 0
        for (y in 0 until h step 2) for (x in 0 until w step 2) {
            val c = px[y * w + x]
            total++
            if (minOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF) >= 200) white++
        }
        return total > 0 && white * 1000 >= total * permille && white * 10 < total * 4
    }

    private class MapView(context: Context, private val inPanel: Boolean) : View(context) {
        // Two bitmaps: PixelCopy fills one while the other is on screen, so the map never tears.
        private var front: Bitmap? = null
        private var back: Bitmap? = null
        private var pixels = IntArray(0)
        private var hasMap = false
        private var busy = false
        private var running = false
        private val paint = Paint()
        private val dst = RectF()
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 14 * resources.displayMetrics.density
        }

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
            val frame = holder.surfaceFrame   // buffer size, which can differ from the view size
            val src = cropRect(frame.width(), frame.height()) ?: return
            val bmp = back?.takeIf { it.width == src.width() && it.height == src.height() }
                ?: Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888)
            busy = true
            runCatching {
                PixelCopy.request(sv, src, bmp, { result ->
                    busy = false
                    if (result == PixelCopy.SUCCESS && running) {
                        back = front
                        front = bmp
                        hasMap = editing() || detect(bmp)
                        invalidate()
                    }
                }, handler)
            }.onFailure { busy = false }   // surface went away between the check and the call
        }

        private fun detect(b: Bitmap): Boolean {
            val n = b.width * b.height
            if (pixels.size != n) pixels = IntArray(n)
            b.getPixels(pixels, 0, b.width, 0, 0, b.width, b.height)
            return hasTrackOutline(pixels, b.width, b.height, whitePermille.intValue)
        }

        override fun onDraw(canvas: Canvas) {
            val b = front
            if (b == null || !hasMap) {
                // Plain ground and a discreet label instead of whatever the crop shows in menus.
                canvas.drawText("Pas de carte", width / 2f, height / 2f, textPaint)
                return
            }
            paint.isFilterBitmap = smooth.value
            val scale = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
            val w = b.width * scale
            val h = b.height * scale
            dst.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            canvas.drawBitmap(b, null, dst, paint)
        }
    }
}
