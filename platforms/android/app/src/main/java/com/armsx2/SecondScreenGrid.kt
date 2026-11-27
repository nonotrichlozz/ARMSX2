// SPDX-License-Identifier: GPL-3.0-or-later
// ARMSX2 fork addition (see PANNEAU-ECRAN-BAS.md): the second-screen tiles placed freely on a grid,
// and the editor that moves, resizes and removes them on the panel itself.
package com.armsx2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.armsx2.runtime.MainActivityRuntime
import kotlin.math.roundToInt

/** One tile on the panel: its top-left cell and its size, in cells. */
data class PlacedTile(val tile: SecondScreenTile, val col: Int, val row: Int, val w: Int, val h: Int) {
    fun overlaps(o: PlacedTile) =
        col < o.col + o.w && o.col < col + w && row < o.row + o.h && o.row < row + h

    fun fitsGrid() = col >= 0 && row >= 0 && w >= 1 && h >= 1 &&
        col + w <= SecondScreenLayout.COLS && row + h <= SecondScreenLayout.ROWS
}

/**
 * Which tiles the panel shows, and where.
 *
 * Upstream kept an ordered list that a GridLayout filled left to right, so tiles of different
 * sizes left holes and the only control was moving a tile earlier or later. Here every tile keeps
 * the cells it was put in.
 *
 * One layout is shared by every game. A game gets its own once the panel is edited while it runs,
 * unless "Même disposition pour tous les jeux" ([shared]) is on.
 */
object SecondScreenLayout {
    const val COLS = 6
    const val ROWS = 6

    private const val PREF_GRID = "secondScreen.grid"
    private const val PREF_GAME = "secondScreen.grid.game."
    private const val PREF_SHARED = "secondScreen.grid.shared"

    /** Pause, save and load along the top; FPS, frame time, photo and resolution under them; the
     *  cover with the game's title under it down the right. Fills the grid, so no holes. */
    val DEFAULT = listOf(
        PlacedTile(SecondScreenTile.PAUSE, 0, 0, 2, 2),
        PlacedTile(SecondScreenTile.SAVE, 2, 0, 2, 1),
        PlacedTile(SecondScreenTile.LOAD, 2, 1, 2, 1),
        PlacedTile(SecondScreenTile.FPS, 0, 2, 2, 2),
        PlacedTile(SecondScreenTile.FRAME_TIME, 2, 2, 2, 2),
        PlacedTile(SecondScreenTile.SCREENSHOT, 0, 4, 2, 2),
        PlacedTile(SecondScreenTile.RESOLUTION, 2, 4, 2, 2),
        PlacedTile(SecondScreenTile.COVER, 4, 0, 2, 4),
        PlacedTile(SecondScreenTile.TITLE, 4, 4, 2, 2),
    )

    /** Bumped on every change, so the settings screen re-reads the layout. */
    val generation = mutableIntStateOf(0)

    /** "Même disposition pour tous les jeux". Off: a game can keep a layout of its own. */
    val shared = mutableStateOf(false)

    fun load() {
        shared.value = runCatching { MainActivityRuntime.prefs.getBoolean(PREF_SHARED, false) }
            .getOrDefault(false)
    }

    /** The running game's key (the one per-game settings use); null in the library. */
    private fun gameKey(): String? {
        val state = MainActivityRuntime.eState.value
        if (state != EmuState.RUNNING && state != EmuState.PAUSED) return null
        return MainActivityRuntime.currentGame.value?.settingsKey?.takeIf { it.isNotBlank() }
    }

    /** Whether an edit made now goes to the running game's own layout rather than the shared one. */
    fun editsThisGame(): Boolean = !shared.value && gameKey() != null

    private fun targetPref(): String =
        gameKey()?.takeUnless { shared.value }?.let { PREF_GAME + it } ?: PREF_GRID

    /** What the panel shows: the running game's own layout when it has one, else the shared one. */
    fun current(): List<PlacedTile> = runCatching {
        val prefs = MainActivityRuntime.prefs
        val own = gameKey()?.takeUnless { shared.value }?.let { prefs.getString(PREF_GAME + it, null) }
        parse(own ?: prefs.getString(PREF_GRID, null))
    }.getOrNull() ?: DEFAULT

    fun tiles(): List<SecondScreenTile> = current().map { it.tile }

    fun set(list: List<PlacedTile>) {
        runCatching { MainActivityRuntime.prefs.edit().putString(targetPref(), format(list)).apply() }
        generation.intValue++
        SecondScreen.layoutChanged()
    }

    /** Puts [tile] in the first free place, as big as there is room for. False when nothing fits. */
    fun add(tile: SecondScreenTile): Boolean {
        val cur = current()
        if (cur.any { it.tile == tile }) return true
        val sizes = if (tile == SecondScreenTile.COVER) listOf(2 to 4, 2 to 3, 2 to 2, 1 to 2, 1 to 1)
        else listOf(2 to 1, 1 to 1)
        for ((w, h) in sizes) for (row in 0..ROWS - h) for (col in 0..COLS - w) {
            val p = PlacedTile(tile, col, row, w, h)
            if (cur.none { it.overlaps(p) }) {
                set(cur + p)
                return true
            }
        }
        return false
    }

    /** The settings chips: off if it is on, else on in the first free place. False when full. */
    fun toggle(tile: SecondScreenTile): Boolean {
        val cur = current()
        if (cur.none { it.tile == tile }) return add(tile)
        set(cur.filterNot { it.tile == tile })
        return true
    }

    fun reset() = set(DEFAULT)

    fun setShared(on: Boolean) {
        if (on == shared.value) return
        runCatching {
            val prefs = MainActivityRuntime.prefs
            // Turned on during a game that has its own layout: that layout becomes everyone's,
            // since it is the one on screen when the player chose to share it.
            if (on) gameKey()?.let { prefs.getString(PREF_GAME + it, null) }
                ?.let { prefs.edit().putString(PREF_GRID, it).apply() }
            prefs.edit().putBoolean(PREF_SHARED, on).apply()
        }
        shared.value = on
        generation.intValue++
        SecondScreen.layoutChanged()
    }

    private fun format(list: List<PlacedTile>) =
        list.joinToString(",") { "${it.tile.id}:${it.col}:${it.row}:${it.w}:${it.h}" }

    /** Unknown tiles, broken entries, and anything off the grid or on top of another are dropped. */
    internal fun parse(raw: String?): List<PlacedTile>? {
        raw ?: return null
        val byId = SecondScreenTile.entries.associateBy { it.id }
        val out = ArrayList<PlacedTile>()
        for (item in raw.split(',')) {
            val f = item.split(':')
            val tile = byId[f[0]] ?: continue
            val n = f.drop(1).mapNotNull { it.toIntOrNull() }
            if (n.size != 4) continue
            val p = PlacedTile(tile, n[0], n[1], n[2], n[3])
            if (p.fitsGrid() && out.none { it.tile == tile || it.overlaps(p) }) out += p
        }
        return out
    }
}

/**
 * The panel's tiles, each laid out in the cells it was given.
 *
 * In edit mode the grid takes every touch: drag a tile to move it, drag its ◢ corner to resize it,
 * touch its ✕ to take it off. Both snap to the cells. A move or resize that would cover another
 * tile shows red while dragging and is refused on release: the tile goes back and flashes red.
 */
@SuppressLint("ViewConstructor")
class PanelGrid(context: Context, private val accent: Int) : ViewGroup(context) {

    /** Called with the new layout after an accepted edit. */
    var onEdit: (List<PlacedTile>) -> Unit = {}

    var items: List<PlacedTile> = emptyList()
        private set
    private val views = LinkedHashMap<SecondScreenTile, View>()

    var editing = false
        set(value) {
            field = value
            endDrag()
        }

    private val dp = resources.displayMetrics.density
    private val gap = 4 * dp
    private val radius = 14 * dp

    init {
        setWillNotDraw(false)
    }

    /** Shows [list], taking each tile's view from [viewOf] (views are reused across calls). */
    fun show(list: List<PlacedTile>, viewOf: (SecondScreenTile) -> View) {
        endDrag()
        val next = list.map { viewOf(it.tile) }
        removeAllViews()
        views.clear()
        items = list
        list.forEachIndexed { i, p ->
            val v = next[i]
            (v.parent as? ViewGroup)?.removeView(v)
            views[p.tile] = v
            addView(v)
        }
        invalidate()
    }

    /** Cell edges for a box of [totalW] x [totalH]: one function for measure and layout, so a
     *  tile is measured at exactly the size it is then laid out at. */
    private fun cellRect(col: Int, row: Int, w: Int, h: Int, out: Rect, totalW: Int = width, totalH: Int = height): Rect {
        val cw = (totalW - paddingLeft - paddingRight) / SecondScreenLayout.COLS.toFloat()
        val ch = (totalH - paddingTop - paddingBottom) / SecondScreenLayout.ROWS.toFloat()
        out.set(
            (paddingLeft + col * cw + gap).roundToInt(),
            (paddingTop + row * ch + gap).roundToInt(),
            (paddingLeft + (col + w) * cw - gap).roundToInt(),
            (paddingTop + (row + h) * ch - gap).roundToInt(),
        )
        return out
    }

    private fun rectOf(p: PlacedTile, out: Rect = Rect()) = cellRect(p.col, p.row, p.w, p.h, out)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalW = MeasureSpec.getSize(widthMeasureSpec)
        val totalH = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(totalW, totalH)
        val r = Rect()
        items.forEach { p ->
            cellRect(p.col, p.row, p.w, p.h, r, totalW, totalH)
            views[p.tile]?.measure(
                MeasureSpec.makeMeasureSpec(r.width().coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(r.height().coerceAtLeast(0), MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val rect = Rect()
        items.forEach { p ->
            rectOf(p, rect)
            views[p.tile]?.layout(rect.left, rect.top, rect.right, rect.bottom)
        }
    }

    // ---- Editing ------------------------------------------------------------------------------

    private enum class Grab { NONE, MOVE, RESIZE, REMOVE }

    private var grab = Grab.NONE
    private var grabbed: PlacedTile? = null
    private var downX = 0f
    private var downY = 0f
    private var ghost: PlacedTile? = null
    private var ghostFree = true
    private var refused: PlacedTile? = null
    private var refusedUntil = 0L

    /** Side of the ✕ and ◢ touch zones: a finger's width, less on a small tile. */
    private fun knob(w: Float, h: Float) = minOf(36 * dp, w / 2.5f, h / 2.5f)

    private fun inRemove(r: Rect, x: Float, y: Float): Boolean {
        val k = knob(r.width().toFloat(), r.height().toFloat())
        return x < r.left + k && y < r.top + k
    }

    private fun inResize(r: Rect, x: Float, y: Float): Boolean {
        val k = knob(r.width().toFloat(), r.height().toFloat())
        return x > r.right - k && y > r.bottom - k
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = editing

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!editing) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val r = Rect()
                val hit = items.lastOrNull { rectOf(it, r).contains(ev.x.toInt(), ev.y.toInt()) }
                grabbed = hit
                downX = ev.x
                downY = ev.y
                grab = if (hit == null) Grab.NONE else {
                    rectOf(hit, r)
                    when {
                        inRemove(r, ev.x, ev.y) -> Grab.REMOVE
                        inResize(r, ev.x, ev.y) -> Grab.RESIZE
                        else -> Grab.MOVE
                    }
                }
                if (hit != null && grab != Grab.REMOVE) views[hit.tile]?.translationZ = 8 * dp
            }
            MotionEvent.ACTION_MOVE -> {
                val p = grabbed ?: return true
                if (grab != Grab.MOVE && grab != Grab.RESIZE) return true
                val dx = ev.x - downX
                val dy = ev.y - downY
                val cw = (width - paddingLeft - paddingRight) / SecondScreenLayout.COLS.toFloat()
                val ch = (height - paddingTop - paddingBottom) / SecondScreenLayout.ROWS.toFloat()
                val g = if (grab == Grab.MOVE) p.copy(
                    col = (p.col + (dx / cw).roundToInt()).coerceIn(0, SecondScreenLayout.COLS - p.w),
                    row = (p.row + (dy / ch).roundToInt()).coerceIn(0, SecondScreenLayout.ROWS - p.h),
                ) else p.copy(
                    w = (p.w + (dx / cw).roundToInt()).coerceIn(1, SecondScreenLayout.COLS - p.col),
                    h = (p.h + (dy / ch).roundToInt()).coerceIn(1, SecondScreenLayout.ROWS - p.row),
                )
                ghost = g
                ghostFree = items.none { it.tile != p.tile && it.overlaps(g) }
                if (grab == Grab.MOVE) views[p.tile]?.apply {
                    translationX = dx
                    translationY = dy
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val p = grabbed
                val g = ghost
                if (p != null && grab == Grab.REMOVE) {
                    if (inRemove(rectOf(p), ev.x, ev.y)) commit(items.filterNot { it.tile == p.tile })
                } else if (p != null && g != null && g != p) {
                    if (ghostFree) {
                        commit(items.map { if (it.tile == p.tile) g else it })
                    } else {
                        refused = p
                        refusedUntil = SystemClock.uptimeMillis() + 800
                        postInvalidateDelayed(820)
                        say("Place occupée : la case revient où elle était", error = true)
                    }
                }
                endDrag()
            }
            MotionEvent.ACTION_CANCEL -> endDrag()
        }
        return true
    }

    private fun commit(list: List<PlacedTile>) {
        val current = HashMap(views)
        show(list) { current.getValue(it) }
        onEdit(list)
    }

    private fun endDrag() {
        grabbed?.let { p ->
            views[p.tile]?.apply {
                translationX = 0f
                translationY = 0f
                translationZ = 0f
            }
        }
        grabbed = null
        ghost = null
        grab = Grab.NONE
        invalidate()
    }

    // ---- Messages -----------------------------------------------------------------------------

    private var message: String? = null
    private var messageError = false
    private var messageUntil = 0L

    /** A short line along the bottom of the grid: help on entering edit mode, or why an edit failed. */
    fun say(text: String, error: Boolean = false, ms: Long = 2500) {
        message = text
        messageError = error
        messageUntil = SystemClock.uptimeMillis() + ms
        invalidate()
        postInvalidateDelayed(ms + 20)
    }

    // ---- Drawing ------------------------------------------------------------------------------

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp
        color = 0x33FFFFFF
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * dp
        color = accent
        pathEffect = DashPathEffect(floatArrayOf(7 * dp, 5 * dp), 0f)
    }
    private val freePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (accent and 0x00FFFFFF) or 0x66000000 }
    private val takenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99E5484D.toInt() }
    private val refusedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4 * dp
        color = 0xFFE5484D.toInt()
    }
    private val removePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5484D.toInt() }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2.5f * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val messageBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val messageText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15 * dp
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rect = Rect()
    private val box = RectF()

    /** The empty cells, under the tiles, so it is clear what a tile snaps to. */
    override fun onDraw(canvas: Canvas) {
        if (!editing) return
        for (row in 0 until SecondScreenLayout.ROWS) for (col in 0 until SecondScreenLayout.COLS) {
            box.set(cellRect(col, row, 1, 1, rect))
            canvas.drawRoundRect(box, radius, radius, guidePaint)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (editing) {
            ghost?.let { g ->
                box.set(rectOf(g, rect))
                canvas.drawRoundRect(box, radius, radius, if (ghostFree) freePaint else takenPaint)
            }
            items.forEach { p ->
                val v = views[p.tile]
                box.set(rectOf(p, rect))
                box.offset(v?.translationX ?: 0f, v?.translationY ?: 0f)
                canvas.drawRoundRect(box, radius, radius, framePaint)
                val k = knob(box.width(), box.height())
                // ✕, top left: take the tile off.
                val cx = box.left + k / 2
                val cy = box.top + k / 2
                val rr = k * 0.42f
                canvas.drawCircle(cx, cy, rr, removePaint)
                val a = rr * 0.45f
                canvas.drawLine(cx - a, cy - a, cx + a, cy + a, markPaint)
                canvas.drawLine(cx - a, cy + a, cx + a, cy - a, markPaint)
                // ◢, bottom right: resize.
                path.reset()
                path.moveTo(box.right, box.bottom - k * 0.85f)
                path.lineTo(box.right, box.bottom)
                path.lineTo(box.right - k * 0.85f, box.bottom)
                path.close()
                canvas.drawPath(path, handlePaint)
            }
            refused?.takeIf { SystemClock.uptimeMillis() < refusedUntil }?.let { p ->
                box.set(rectOf(p, rect))
                canvas.drawRoundRect(box, radius, radius, refusedPaint)
            }
        }
        val text = message
        if (text != null && SystemClock.uptimeMillis() < messageUntil) {
            val pad = 16 * dp
            val h = 40 * dp
            val tw = messageText.measureText(text).coerceAtMost(width - 2 * pad - 8 * dp)
            box.set((width - tw) / 2 - pad, height - 8 * dp - h, (width + tw) / 2 + pad, height - 8 * dp)
            messageBg.color = if (messageError) 0xF2B3261E.toInt() else 0xEB101318.toInt()
            canvas.drawRoundRect(box, h / 2, h / 2, messageBg)
            val baseline = box.centerY() - (messageText.descent() + messageText.ascent()) / 2
            canvas.drawText(text, width / 2f, baseline, messageText)
        }
    }
}
