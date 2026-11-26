// SPDX-FileCopyrightText: 2026 nonotrichlozz, contributors to the ARMSX2 fork
// SPDX-License-Identifier: GPL-3.0+
//
// Gran Turismo 3: the in-game mini-map, cropped from the game picture and shown enlarged on the
// second display (Retroid Pocket Duo). See GT3-CARTE.md at the repository root.
//
// Isolated on purpose: the only hooks outside this file are a few lines in SecondScreen.kt
// (which already finds the second display and owns the Presentation) and one call in AppTab.kt.
package com.armsx2.gt3map

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.PixelCopy
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.armsx2.EmuState
import com.armsx2.SecondScreen
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.settings.IntSliderRow
import com.armsx2.ui.settings.ToggleRow
import kr.co.iefriends.pcsx2.NativeApp

object Gt3Map {
    const val GT3_SERIAL = "SCES-50294"

    // Default crop, in % of the game surface (black bars included): x 4→29 %, y 18→35 %.
    private val DEFAULTS = intArrayOf(4, 29, 18, 35)

    val enabled = mutableStateOf(false)
    val gt3Only = mutableStateOf(true)
    val smooth = mutableStateOf(false)
    /** x0, x1, y0, y1 in percent of the game surface. */
    val crop = Array(4) { mutableIntStateOf(DEFAULTS[it]) }

    private const val CAPTURE_MS = 50L   // ~20 captures/s: every 2–3 game frames
    private const val WATCH_MS = 1000L
    private val handler = Handler(Looper.getMainLooper())
    private var lastActive = false
    private var watching = false

    private val prefs get() = MainActivityRuntime.prefs

    /** Called from SecondScreen.load(). */
    fun load() {
        runCatching {
            enabled.value = prefs.getBoolean("gt3map.enabled", false)
            gt3Only.value = prefs.getBoolean("gt3map.gt3Only", true)
            smooth.value = prefs.getBoolean("gt3map.smooth", false)
            crop.forEachIndexed { i, s -> s.intValue = prefs.getInt("gt3map.crop$i", DEFAULTS[i]) }
        }
        if (!watching) { watching = true; handler.post(watch) }
    }

    /** Should the second display show the map instead of the usual panel, right now? */
    fun active(): Boolean {
        if (!enabled.value) return false
        val state = MainActivityRuntime.eState.value
        if (state != EmuState.RUNNING && state != EmuState.PAUSED) return false
        if (!gt3Only.value) return true
        val serial = runCatching { NativeApp.getGameSerial() }.getOrNull().orEmpty()
        return serial.equals(GT3_SERIAL, ignoreCase = true)
    }

    /** Game start/stop does not re-run SecondScreen's display logic, so watch for the switch. */
    private val watch = object : Runnable {
        override fun run() {
            val now = active()
            if (now != lastActive) { lastActive = now; SecondScreen.rebuild() }
            handler.postDelayed(this, WATCH_MS)
        }
    }

    private fun save(key: String, v: Boolean) = runCatching { prefs.edit().putBoolean(key, v).apply() }

    fun setCrop(i: Int, value: Int) {
        // Keep left < right and top < bottom.
        val v = when (i) {
            0, 2 -> value.coerceIn(0, crop[i + 1].intValue - 1)
            else -> value.coerceIn(crop[i - 1].intValue + 1, 100)
        }
        crop[i].intValue = v
        runCatching { prefs.edit().putInt("gt3map.crop$i", v).apply() }
    }

    /** The settings block, shown under "Second screen" in App settings. */
    @Composable
    fun Settings() {
        ToggleRow(
            label = "Carte GT3 sur le 2ᵉ écran",
            value = enabled.value,
            description = "Affiche la mini-carte de Gran Turismo 3 en grand à la place du panneau.",
            onChange = { enabled.value = it; save("gt3map.enabled", it) },
        )
        if (!enabled.value) return
        ToggleRow(
            label = "Uniquement pour GT3 ($GT3_SERIAL)",
            value = gt3Only.value,
            description = "Désactivé : la zone est copiée quel que soit le jeu.",
            onChange = { gt3Only.value = it; save("gt3map.gt3Only", it) },
        )
        ToggleRow(
            label = "Carte lissée",
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
                description = if (i == 0) "En % de l'image. Réglez en course : le 2ᵉ écran se met à jour en direct." else null,
                valueFormatter = { "$it %" },
                onReset = if (crop[i].intValue != DEFAULTS[i]) ({ setCrop(i, DEFAULTS[i]) }) else null,
                onChange = { setCrop(i, it) },
            )
        }
    }

    /** The second-display window: black background, the cropped map scaled to fit. */
    class Screen(context: Context, display: Display) : Presentation(context, display) {
        private lateinit var view: MapView
        // Two bitmaps: PixelCopy fills one while the other is on screen, so the map never tears.
        private var front: Bitmap? = null
        private var back: Bitmap? = null
        private var busy = false
        private var running = false

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setCancelable(false)   // same as SecondScreen's panel: BACK must not close it
            view = MapView(context)
            setContentView(view)
        }

        override fun onStart() { super.onStart(); running = true; handler.post(tick) }
        override fun onStop() { running = false; handler.removeCallbacks(tick); super.onStop() }

        private val tick = object : Runnable {
            override fun run() {
                if (!running) return
                capture()
                handler.postDelayed(this, CAPTURE_MS)
            }
        }

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
                        view.bitmap = bmp
                        view.invalidate()
                    }
                }, handler)
            }.onFailure { busy = false }   // surface went away between the check and the call
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

    private class MapView(context: Context) : View(context) {
        var bitmap: Bitmap? = null
        private val paint = Paint()
        private val dst = RectF()

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            val b = bitmap ?: return
            paint.isFilterBitmap = smooth.value
            val scale = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
            val w = b.width * scale
            val h = b.height * scale
            dst.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            canvas.drawBitmap(b, null, dst, paint)
        }
    }
}
