package dev.retrobit.bitkeyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import dev.retrobit.bitkeyboard.core.CornerDirection
import dev.retrobit.bitkeyboard.core.EmojiData
import dev.retrobit.bitkeyboard.core.KeyEvent as BitKeyEvent
import dev.retrobit.bitkeyboard.core.KeySpec
import dev.retrobit.bitkeyboard.core.KeyStyle
import dev.retrobit.bitkeyboard.core.KeyValue
import dev.retrobit.bitkeyboard.core.KeyboardAction
import dev.retrobit.bitkeyboard.core.KeyboardLayout
import dev.retrobit.bitkeyboard.core.Layer
import dev.retrobit.bitkeyboard.core.Layouts
import dev.retrobit.bitkeyboard.core.QwertyGeometry
import dev.retrobit.bitkeyboard.core.GlidePoint
import dev.retrobit.bitkeyboard.core.Suggester
import dev.retrobit.bitkeyboard.settings.SettingsStore
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tampilan keyboard kustom: menggambar tombol (termasuk simbol sudut), bilah
 * saran, popup aksen, jejak geser, dan menangani seluruh interaksi sentuh.
 */
class KeyboardView(
    context: Context,
    private val service: BitKeyboardService,
    private val settings: SettingsStore,
    private val editor: Editor,
) : View(context), Editor.Listener {

    // ------------------------------------------------------------------ //
    // Model tampilan
    // ------------------------------------------------------------------ //
    private class PositionedKey(val spec: KeySpec, val rect: RectF, val row: Int) {
        val value: KeyValue get() = spec.value
    }

    private class PopupState(
        val items: List<KeyValue>,
        val rect: RectF,
        val itemWidth: Float,
        var selected: Int,
    )

    private var theme: KeyboardTheme = KeyboardTheme.DARK
    private var layout: KeyboardLayout = Layouts.lettersDefault()
    private var layer: Layer = Layer.LETTERS
    private var emojiTab: Int = 0

    private val keys = ArrayList<PositionedKey>(80)
    private var barHeight = 0f
    private var keyWidth = 0f
    private var rowHeight = 0f
    private var letterTop = 0f
    private val suggestionRects = ArrayList<RectF>(3)
    private val icons = LinkedHashMap<KeyboardAction, RectF>()
    private val suggestionIcons = listOf(
        KeyboardAction.VOICE_INPUT to "🎙",
        KeyboardAction.OPEN_SETTINGS to "⚙",
        KeyboardAction.HIDE_KEYBOARD to "⌄",
    )

    private var suggestions: List<Suggester.Suggestion> = emptyList()
    private var composingWord: String = ""
    private var dictionaryReady = false

    // Status modifier
    private var shiftOn = false
    private var capsLock = false
    private var ctrlOn = false
    private var altOn = false
    private var fnOn = false

    // Status sentuh
    private var downKey: PositionedKey? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var cornerDirection: CornerDirection? = null
    private var gliding = false
    private var glidePoints = ArrayList<GlidePoint>(96)
    private var popup: PopupState? = null
    private var suggestionPressed = -1
    private var suggestionLongPressed = false
    private var lastSpaceSteps = 0
    private var previewVisible = false

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null
    private var suggestionLongPressRunnable: Runnable? = null

    private var lastShiftTap = 0L

    // ------------------------------------------------------------------ //
    // Paint
    // ------------------------------------------------------------------ //
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val actionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val underlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val trailPath = Path()
    private val tempRect = RectF()

    init {
        isClickable = true
        isFocusable = false
        editor.listener = this
        emojiTab = settings.emojiTab
        syncSettings()
    }

    // ------------------------------------------------------------------ //
    // Pengaturan & layout
    // ------------------------------------------------------------------ //
    fun syncSettings() {
        theme = KeyboardTheme.resolve(settings, isNightMode(resources.configuration))
        emojiTab = settings.emojiTab
        rebuildLayout()
        applyPaints()
        requestLayout()
        invalidate()
    }

    private fun applyPaints() {
        keyTextPaint.color = theme.text
        keyTextPaint.textSize = sp(20f)
        cornerPaint.color = theme.hint
        cornerPaint.textSize = sp(8.5f)
        actionTextPaint.color = theme.actionText
        actionTextPaint.textSize = sp(15f)
        hintPaint.color = theme.hint
        hintPaint.textSize = sp(12f)
        dividerPaint.color = theme.divider
        trailPaint.color = theme.trail
        trailPaint.strokeWidth = dp(5f)
        trailPaint.alpha = 210
        popupPaint.color = theme.popupBackground
        previewPaint.color = theme.popupBackground
    }

    private fun rebuildLayout() {
        layout = buildLayout(layer)
    }

    private fun buildLayout(value: Layer): KeyboardLayout = when (value) {
        Layer.LETTERS -> Layouts.letters(settings.numberRow, settings.functionRow)
        Layer.SYMBOLS -> Layouts.symbols()
        Layer.SYMBOLS_ALT -> Layouts.symbolsAlt()
        Layer.SCIENTIFIC -> Layouts.scientific()
        Layer.SCIENTIFIC_ALT -> Layouts.scientificAlt()
        Layer.EMOJI -> EmojiData.layout(emojiTab, settings.emojiRecent)
    }

    fun setLayer(value: Layer) {
        if (layer == value) return
        layer = value
        cancelTouch()
        relayout()
    }

    private fun relayout() {
        rebuildLayout()
        if (width > 0) {
            keyWidth = width / layout.columns
            rebuildGeometry(width.toFloat())
        }
        requestLayout()
        invalidate()
    }

    fun currentLayer(): Layer = layer

    /** Reset modifier dan layer saat keyboard dibuka untuk kolom input baru. */
    fun resetForNewInput() {
        shiftOn = false
        capsLock = false
        ctrlOn = false
        altOn = false
        fnOn = false
        layer = Layer.LETTERS
        rebuildLayout()
        if (width > 0) {
            keyWidth = width / layout.columns
            rebuildGeometry(width.toFloat())
        }
        requestLayout()
        invalidate()
    }

    fun onDictionaryReady() {
        dictionaryReady = true
        editor.updateSuggestions()
        invalidate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncSettings()
    }

    // ------------------------------------------------------------------ //
    // Ukuran
    // ------------------------------------------------------------------ //
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val bar = if (settings.showSuggestionBar) dp(42f) else 0f
        val row = dp(settings.keyHeightDp.toFloat())
        var rows = 0f
        for (r in layout.rows) rows += row * r.height
        val height = (bar + rows + dp(5f)).roundToInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        barHeight = if (settings.showSuggestionBar) dp(42f) else 0f
        rowHeight = dp(settings.keyHeightDp.toFloat())
        keyWidth = w / layout.columns
        rebuildGeometry(w.toFloat())
    }

    private fun rebuildGeometry(width: Float) {
        keys.clear()
        suggestionRects.clear()
        icons.clear()
        var top = barHeight
        letterTop = barHeight
        val letterStart = layout.letterRowRange.firstOrNull()
        if (letterStart != null) {
            for (i in 0 until letterStart) {
                letterTop += rowHeight * layout.rows[i].height
            }
        }
        top = barHeight
        for ((rowIndex, rowSpec) in layout.rows.withIndex()) {
            val rowH = rowHeight * rowSpec.height
            var x = rowSpec.offset * keyWidth
            // Baris yang tidak memenuhi lebar keyboard (mis. tab emoji) dibuat rata tengah.
            val rowUnits = rowSpec.keys.sumOf { it.width.toDouble() }.toFloat()
            if (rowUnits < layout.columns) x += (layout.columns - rowUnits) * keyWidth / 2f
            for (spec in rowSpec.keys) {
                val w = spec.width * keyWidth
                val gap = dp(2.5f)
                keys += PositionedKey(
                    spec,
                    RectF(x + gap, top + gap, x + w - gap, top + rowH - gap),
                    rowIndex,
                )
                x += w
            }
            top += rowH
        }
        if (settings.showSuggestionBar) {
            val iconSize = dp(34f)
            val iconGap = dp(2f)
            var x = width - dp(6f) - (iconSize + iconGap) * suggestionIcons.size
            for ((action, _) in suggestionIcons) {
                icons[action] = RectF(x, dp(4f), x + iconSize, barHeight - dp(4f))
                x += iconSize + iconGap
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Gambar
    // ------------------------------------------------------------------ //
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        drawSuggestionBar(canvas)
        drawKeys(canvas)
        drawTrail(canvas)
        drawPopup(canvas)
        drawPreview(canvas)
    }

    private fun drawSuggestionBar(canvas: Canvas) {
        if (!settings.showSuggestionBar) return
        fillPaint.color = theme.suggestionBackground
        canvas.drawRect(0f, 0f, width.toFloat(), barHeight, fillPaint)
        fillPaint.color = theme.divider
        canvas.drawRect(0f, barHeight - dp(1f), width.toFloat(), barHeight, fillPaint)

        val rightLimit = (icons.values.maxOfOrNull { it.left } ?: width.toFloat()) - dp(6f)
        val left = dp(6f)
        val usable = rightLimit - left
        suggestionRects.clear()

        if (suggestions.isEmpty()) {
            val message = when {
                !dictionaryReady && settings.suggestionsEnabled -> "Memuat kamus Bahasa Indonesia…"
                settings.glideEnabled && layer == Layer.LETTERS -> "Geser jari di atas huruf untuk mengetik satu kata"
                else -> ""
            }
            if (message.isNotEmpty()) {
                hintPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(message, left, barHeight / 2f + sp(4.2f), hintPaint)
            }
        } else {
            val count = suggestions.size
            val slot = usable / count
            for (i in 0 until count) {
                val rect = RectF(left + slot * i, dp(2f), left + slot * (i + 1), barHeight - dp(2f))
                suggestionRects += rect
                val item = suggestions[i]
                val paint = if (i == 0) keyTextPaint else actionTextPaint
                paint.color = if (i == 0) theme.text else theme.hint
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = sp(if (i == 0) 16f else 15f)
                val label = trimToWidth(item.word, paint, rect.width() - dp(8f))
                canvas.drawText(label, rect.centerX(), rect.centerY() + sp(5f), paint)
                if (i == 0) {
                    underlinePaint.color = theme.accent
                    underlinePaint.strokeWidth = dp(2f)
                    val w = min(paint.measureText(label), rect.width() - dp(8f))
                    canvas.drawLine(
                        rect.centerX() - w / 2f, rect.bottom - dp(7f),
                        rect.centerX() + w / 2f, rect.bottom - dp(7f),
                        underlinePaint,
                    )
                }
            }
            keyTextPaint.textSize = sp(20f)
            keyTextPaint.color = theme.text
        }

        for ((action, rect) in icons) {
            popupPaint.color = if (action == KeyboardAction.VOICE_INPUT) {
                withAlpha(theme.accent, 40)
            } else {
                withAlpha(theme.key, 90)
            }
            canvas.drawRoundRect(rect, dp(9f), dp(9f), popupPaint)
            hintPaint.color = if (action == KeyboardAction.VOICE_INPUT) theme.accent else theme.hint
            hintPaint.textSize = sp(if (action == KeyboardAction.VOICE_INPUT) 17f else 16f)
            hintPaint.textAlign = Paint.Align.CENTER
            val glyph = suggestionIcons.firstOrNull { it.first == action }?.second ?: ""
            canvas.drawText(glyph, rect.centerX(), rect.centerY() + sp(5.5f), hintPaint)
        }
        hintPaint.color = theme.hint
        hintPaint.textSize = sp(12f)
    }

    private fun drawKeys(canvas: Canvas) {
        for (pk in keys) {
            val spec = pk.spec
            val rect = pk.rect
            val pressed = downKey === pk && !gliding && popup == null
            val modifierActive = when (val v = spec.value) {
                is KeyValue.ActionValue -> when (v.action) {
                    KeyboardAction.SHIFT -> shiftActive()
                    KeyboardAction.CTRL -> ctrlOn
                    KeyboardAction.ALT -> altOn
                    KeyboardAction.FN -> fnOn
                    else -> false
                }
                else -> false
            }
            val bg = when {
                pressed -> theme.keyPressed
                modifierActive -> theme.accent
                spec.style == KeyStyle.ACTION -> theme.actionKey
                spec.style == KeyStyle.SPACE -> theme.spaceKey
                spec.style == KeyStyle.FUNCTION -> theme.functionKey
                spec.style == KeyStyle.MODIFIER -> theme.accent
                else -> theme.key
            }
            keyPaint.color = bg
            val radius = dp(7f)
            canvas.drawRoundRect(rect, radius, radius, keyPaint)

            val textValue = spec.value as? KeyValue.TextValue
            if (layer == Layer.EMOJI && textValue != null) {
                if (textValue.text.isBlank()) continue
                val paint = keyTextPaint
                paint.color = theme.text
                paint.textSize = sp(23f)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(textValue.text, rect.centerX(), rect.centerY() + sp(8f), paint)
                paint.textSize = sp(20f)
                continue
            }

            if (settings.cornerSymbols && spec.style == KeyStyle.NORMAL) {
                drawCorners(canvas, pk)
            }

            var label = labelFor(spec)
            val actionValue = spec.value as? KeyValue.ActionValue
            if (actionValue?.action == KeyboardAction.SHIFT) label = if (capsLock) "⇪" else "⇧"
            val paint = if (spec.style == KeyStyle.ACTION) actionTextPaint else keyTextPaint
            paint.color = when {
                modifierActive -> theme.actionText
                spec.style == KeyStyle.ACTION -> theme.actionText
                spec.style == KeyStyle.MODIFIER -> theme.actionText
                spec.style == KeyStyle.FUNCTION -> theme.hint
                spec.style == KeyStyle.SPACE -> theme.hint
                else -> theme.text
            }
            paint.textAlign = Paint.Align.CENTER
            val isTextLabel = label.length > 2
            paint.textSize = when {
                spec.style == KeyStyle.FUNCTION || spec.style == KeyStyle.SPACE -> sp(13f)
                isTextLabel -> sp(13f)
                else -> sp(20f)
            }
            canvas.drawText(label, rect.centerX(), rect.centerY() + paint.textSize * 0.36f, paint)
            if (spec.style == KeyStyle.MODIFIER) {
                // tidak ada tambahan
            }
        }
        keyTextPaint.color = theme.text
        keyTextPaint.textSize = sp(20f)
    }

    private fun drawCorners(canvas: Canvas, pk: PositionedKey) {
        val corners = pk.spec.corners
        if (corners.isEmpty()) return
        val inset = dp(4f)
        val baselineTop = pk.rect.top + dp(10.5f)
        val baselineBottom = pk.rect.bottom - dp(3.5f)
        cornerPaint.color = theme.hint
        cornerPaint.textSize = sp(8.2f)
        for (i in corners.indices) {
            val value = corners[i] ?: continue
            val label = cornerLabel(value)
            if (label.isEmpty()) continue
            cornerPaint.textAlign = if (i == 0 || i == 2) Paint.Align.LEFT else Paint.Align.RIGHT
            val x = if (i == 0 || i == 2) pk.rect.left + inset else pk.rect.right - inset
            val y = if (i < 2) baselineTop else baselineBottom
            canvas.drawText(label, x, y, cornerPaint)
        }
    }

    private fun drawTrail(canvas: Canvas) {
        if (!gliding || glidePoints.size < 2) return
        trailPath.reset()
        val first = glidePoints[0]
        trailPath.moveTo(gridToX(first), gridToY(first))
        for (i in 1 until glidePoints.size) {
            val p = glidePoints[i]
            trailPath.lineTo(gridToX(p), gridToY(p))
        }
        canvas.drawPath(trailPath, trailPaint)
        val last = glidePoints.last()
        keyPaint.color = theme.trail
        keyPaint.alpha = 220
        canvas.drawCircle(gridToX(last), gridToY(last), dp(5f), keyPaint)
        keyPaint.alpha = 255
    }

    private fun drawPopup(canvas: Canvas) {
        val state = popup ?: return
        popupPaint.color = theme.popupBackground
        canvas.drawRoundRect(state.rect, dp(10f), dp(10f), popupPaint)
        for (i in state.items.indices) {
            val itemRect = RectF(
                state.rect.left + dp(4f) + i * state.itemWidth,
                state.rect.top + dp(4f),
                state.rect.left + dp(4f) + (i + 1) * state.itemWidth,
                state.rect.bottom - dp(4f),
            )
            if (i == state.selected) {
                keyPaint.color = theme.accent
                canvas.drawRoundRect(itemRect, dp(7f), dp(7f), keyPaint)
            }
            val label = cornerLabel(state.items[i])
            keyTextPaint.color = if (i == state.selected) theme.actionText else theme.text
            keyTextPaint.textSize = sp(19f)
            keyTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(label, itemRect.centerX(), itemRect.centerY() + sp(6.5f), keyTextPaint)
        }
        keyTextPaint.color = theme.text
        keyTextPaint.textSize = sp(20f)
    }

    private fun drawPreview(canvas: Canvas) {
        if (!previewVisible || downKey == null || popup != null || gliding) return
        val pk = downKey!!
        val label = cornerDirection?.let { dir ->
            pk.spec.corner(dir)?.let { cornerLabel(it) }
        } ?: labelFor(pk.spec)
        if (label.isEmpty()) return
        val w = max(pk.rect.width() * 1.25f, dp(46f))
        val h = dp(52f)
        var left = pk.rect.centerX() - w / 2f
        left = left.coerceIn(dp(3f), width - w - dp(3f))
        var top = pk.rect.top - h - dp(6f)
        if (top < barHeight) top = pk.rect.bottom + dp(6f)
        tempRect.set(left, top, left + w, top + h)
        previewPaint.color = theme.popupBackground
        canvas.drawRoundRect(tempRect, dp(10f), dp(10f), previewPaint)
        popupPaint.color = theme.accent
        popupPaint.style = Paint.Style.STROKE
        popupPaint.strokeWidth = dp(1.2f)
        popupPaint.alpha = 90
        canvas.drawRoundRect(tempRect, dp(10f), dp(10f), popupPaint)
        popupPaint.style = Paint.Style.FILL
        popupPaint.alpha = 255
        keyTextPaint.color = theme.text
        keyTextPaint.textSize = sp(if (label.length > 2) 16f else 24f)
        keyTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, tempRect.centerX(), tempRect.centerY() + keyTextPaint.textSize * 0.36f, keyTextPaint)
        keyTextPaint.textSize = sp(20f)
    }

    // ------------------------------------------------------------------ //
    // Sentuhan
    // ------------------------------------------------------------------ //
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event.x, event.y)
            MotionEvent.ACTION_MOVE -> handleMove(event.x, event.y)
            MotionEvent.ACTION_UP -> handleUp(event.x, event.y)
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
        return true
    }

    private fun handleDown(x: Float, y: Float) {
        cancelTouch(keepLayer = true)
        downX = x
        downY = y
        lastX = x
        lastY = y
        downTime = System.currentTimeMillis()

        // Bilah saran lebih dulu.
        if (settings.showSuggestionBar) {
            for (i in suggestionRects.indices) {
                if (suggestionRects[i].contains(x, y)) {
                    suggestionPressed = i
                    suggestionLongPressed = false
                    scheduleSuggestionLongPress(i)
                    invalidate()
                    return
                }
            }
            for ((action, rect) in icons) {
                if (rect.contains(x, y)) {
                    performClickSound()
                    handleAction(action)
                    return
                }
            }
        }

        val pk = findKey(x, y) ?: return
        downKey = pk
        previewVisible = true
        cornerDirection = null
        gliding = false
        glidePoints = ArrayList(96)
        popup = null
        lastSpaceSteps = 0
        performClickSound()
        performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)

        // Tekan lama: popup aksen.
        if (pk.spec.longPress.isNotEmpty()) {
            val runnable = Runnable {
                if (downKey === pk && !gliding) {
                    showPopup(pk, lastX)
                    previewVisible = false
                    invalidate()
                }
            }
            longPressRunnable = runnable
            handler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
        }
        if (pk.spec.repeat) scheduleRepeat(pk)
        invalidate()
    }

    private fun handleMove(x: Float, y: Float) {
        lastX = x
        lastY = y
        if (suggestionPressed >= 0) {
            val rect = suggestionRects.getOrNull(suggestionPressed)
            if (rect != null && !rect.contains(x, y) && hypot(x - downX, y - downY) > dp(24f)) {
                suggestionPressed = -1
                cancelSuggestionLongPress()
                invalidate()
            }
            return
        }
        val pk = downKey ?: return

        if (popup != null) {
            updatePopupSelection(x)
            invalidate()
            return
        }

        val dx = x - downX
        val dy = y - downY
        val distance = hypot(dx, dy)

        // Geser pada spasi: pindahkan kursor.
        val pressedAction = pk.spec.value as? KeyValue.ActionValue
        if (!gliding && pressedAction?.action == KeyboardAction.SPACE) {
            if (abs(dx) > keyWidth * 0.55f) {
                val steps = (dx / (keyWidth * 0.52f)).roundToInt()
                var delta = steps - lastSpaceSteps
                if (delta != 0) {
                    if (abs(delta) > 4) delta = delta.coerceIn(-4, 4)
                    repeat(delta) { editor.moveCursor(if (delta > 0) 1 else -1) }
                    lastSpaceSteps = steps
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            return
        }

        // Pilih sudut selama jari masih dekat tombol asal.
        if (!gliding && pk.spec.corners.isNotEmpty()) {
            val dir = cornerDirection(pk, x, y)
            if (dir != cornerDirection) {
                cornerDirection = dir
                invalidate()
            }
        }

        // Masuk mode glide saat jari berpindah ke tombol lain.
        val hover = findKey(x, y)
        val leftOrigin = !pk.rect.contains(x, y)
        val letterStart = pk.spec.value is KeyValue.CharValue
        if (!gliding && settings.glideEnabled && layer == Layer.LETTERS &&
            isLetterRow(pk.row) && letterStart && hover !== pk && leftOrigin &&
            distance > keyWidth * 0.55f && !ctrlOn && !altOn
        ) {
            startGlide(pk)
        }

        if (gliding) {
            if (cornerDirection != null) cornerDirection = null
            val point = pixelToGrid(x, y)
            val last = glidePoints.lastOrNull()
            if (last == null || hypot(point.x - last.x, point.y - last.y) > 0.035f) {
                glidePoints.add(point)
            }
            previewVisible = false
            invalidate()
        }
    }

    private fun handleUp(x: Float, y: Float) {
        lastX = x
        lastY = y
        cancelTimers()

        if (suggestionPressed >= 0) {
            val index = suggestionPressed
            suggestionPressed = -1
            val item = suggestions.getOrNull(index)
            val longPressed = suggestionLongPressed
            suggestionLongPressed = false
            if (item != null) {
                if (longPressed) editor.learnWord(item.word) else editor.commitSuggestion(item.word)
            }
            invalidate()
            return
        }

        val state = popup
        if (state != null) {
            updatePopupSelection(x)
            val selected = state.selected
            val value = state.items.getOrNull(selected)
            popup = null
            if (value != null) {
                performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                dispatch(value)
            }
            resetTouchState()
            invalidate()
            return
        }

        val pk = downKey ?: run {
            resetTouchState()
            return
        }

        if (gliding) {
            finishGlide(x, y, pk)
            resetTouchState()
            invalidate()
            return
        }

        val distance = hypot(x - downX, y - downY)
        val dir = if (distance > keyWidth * 0.28f) cornerDirection(pk, x, y) else null
        val cornerValue = dir?.let { pk.spec.corner(it) }
        val spaceValue = pk.spec.value as? KeyValue.ActionValue
        val draggedOnSpace = lastSpaceSteps != 0 &&
            spaceValue?.action == KeyboardAction.SPACE
        when {
            // Setelah menggeser kursor di spasi, jangan ketik spasi.
            draggedOnSpace -> Unit
            cornerValue != null -> dispatch(cornerValue)
            else -> dispatch(pk.spec.value)
        }
        resetTouchState()
        invalidate()
    }

    private fun startGlide(from: PositionedKey) {
        gliding = true
        previewVisible = false
        cornerDirection = null
        glidePoints = ArrayList(128)
        glidePoints.add(pixelToGrid(downX, downY))
        glidePoints.add(pixelToGrid(lastX, lastY))
        cancelLongPressTimer()
        performHaptic(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun finishGlide(x: Float, y: Float, pk: PositionedKey) {
        glidePoints.add(pixelToGrid(x, y))
        val distance = hypot(x - downX, y - downY)
        val arc = glideArcLength()
        val dir = cornerDirection(pk, x, y)
        val cornerValue = dir?.let { pk.spec.corner(it) }

        // Geseran pendek yang masih berada di area tombol dianggap corner.
        if (distance < keyWidth * 1.05f && arc < keyWidth * 2.2f && cornerValue != null) {
            dispatch(cornerValue)
            return
        }
        if (glidePoints.size < 3 || arc < keyWidth * 1.2f) {
            dispatch(pk.spec.value)
            return
        }
        if (!editor.isActiveInput()) return
        val decoded = editor.decodeGlide(glidePoints, 5)
        if (decoded.isEmpty()) {
            service.toast("Glide tidak dikenali, coba lagi")
            return
        }
        val best = decoded[0]
        val autoCap = shiftOn || capsLock
        editor.commitGlideWord(best.word, autoCap)
        invalidate()
    }

    private fun glideArcLength(): Float {
        var total = 0f
        for (i in 1 until glidePoints.size) {
            val a = glidePoints[i - 1]
            val b = glidePoints[i]
            total += hypot(b.x - a.x, b.y - a.y) * keyWidth
        }
        return total
    }

    private fun cornerDirection(pk: PositionedKey, x: Float, y: Float): CornerDirection? {
        val dx = x - pk.rect.centerX()
        val dy = y - pk.rect.centerY()
        if (hypot(dx, dy) < keyWidth * 0.22f) return null
        val dir = when {
            dx < 0 && dy < 0 -> CornerDirection.NW
            dx >= 0 && dy < 0 -> CornerDirection.NE
            dx < 0 -> CornerDirection.SW
            else -> CornerDirection.SE
        }
        return if (pk.spec.corner(dir) != null) dir else null
    }

    private fun resetTouchState() {
        downKey = null
        popup = null
        previewVisible = false
        gliding = false
        cornerDirection = null
        glidePoints = ArrayList(0)
        suggestionPressed = -1
        suggestionLongPressed = false
        lastSpaceSteps = 0
        cancelTimers()
    }

    private fun cancelTouch(keepLayer: Boolean = true) {
        resetTouchState()
        cancelSuggestionLongPress()
        invalidate()
    }

    // ------------------------------------------------------------------ //
    // Aksi tombol
    // ------------------------------------------------------------------ //
    private fun dispatch(value: KeyValue) {
        when (value) {
            is KeyValue.CharValue -> {
                val c = applyShift(value.char)
                if (ctrlOn || altOn) {
                    editor.sendCtrlLetter(c.lowercaseChar(), ctrlOn, altOn)
                } else {
                    editor.commitChar(c)
                }
                ctrlOn = false
                altOn = false
                if (layer == Layer.SYMBOLS || layer == Layer.SYMBOLS_ALT) {
                    setLayer(Layer.LETTERS)
                }
            }
            is KeyValue.TextValue -> {
                if (value.text.isNotEmpty()) {
                    if (layer == Layer.EMOJI) {
                        settings.addRecentEmoji(value.text)
                    }
                    editor.commitPlainText(value.text)
                    if (layer == Layer.EMOJI) {
                        rebuildLayout()
                        requestLayout()
                    }
                }
            }
            is KeyValue.EventValue -> {
                editor.sendKeyEvent(value.event.androidKeyCode, metaState())
                ctrlOn = false
                altOn = false
            }
            is KeyValue.ActionValue -> handleAction(value.action, value.payload)
        }
        invalidate()
    }

    private fun handleAction(action: KeyboardAction, payload: String? = null) {
        when (action) {
            KeyboardAction.SHIFT -> toggleShift()
            KeyboardAction.CAPS_LOCK -> {
                capsLock = !capsLock
                shiftOn = capsLock
            }
            KeyboardAction.CTRL -> ctrlOn = !ctrlOn
            KeyboardAction.ALT -> altOn = !altOn
            KeyboardAction.FN -> fnOn = !fnOn
            KeyboardAction.SWITCH_LAYER -> setLayer(Layer.fromId(payload))
            KeyboardAction.EMOJI_TAB -> {
                val tab = payload?.toIntOrNull() ?: 0
                emojiTab = tab
                settings.emojiTab = tab
                relayout()
            }
            KeyboardAction.BACKSPACE -> editor.deleteBackward()
            KeyboardAction.DELETE_WORD -> editor.deleteWord()
            KeyboardAction.ENTER -> editor.commitEnter()
            KeyboardAction.SPACE -> editor.commitSpace()
            KeyboardAction.TAB -> editor.sendKeyEvent(BitKeyEvent.TAB.androidKeyCode, metaState())
            KeyboardAction.ESCAPE -> editor.sendKeyEvent(BitKeyEvent.ESCAPE.androidKeyCode, metaState())
            KeyboardAction.ARROW_LEFT -> editor.sendKeyEvent(BitKeyEvent.DPAD_LEFT.androidKeyCode, metaState())
            KeyboardAction.ARROW_RIGHT -> editor.sendKeyEvent(BitKeyEvent.DPAD_RIGHT.androidKeyCode, metaState())
            KeyboardAction.ARROW_UP -> editor.sendKeyEvent(BitKeyEvent.DPAD_UP.androidKeyCode, metaState())
            KeyboardAction.ARROW_DOWN -> editor.sendKeyEvent(BitKeyEvent.DPAD_DOWN.androidKeyCode, metaState())
            KeyboardAction.MOVE_WORD_LEFT -> editor.moveCursor(-1, byWord = true)
            KeyboardAction.MOVE_WORD_RIGHT -> editor.moveCursor(1, byWord = true)
            KeyboardAction.SELECT_ALL -> editor.performAction(Editor.Action.SELECT_ALL)
            KeyboardAction.COPY -> editor.performAction(Editor.Action.COPY)
            KeyboardAction.CUT -> editor.performAction(Editor.Action.CUT)
            KeyboardAction.PASTE -> editor.performAction(Editor.Action.PASTE)
            KeyboardAction.UNDO -> editor.performAction(Editor.Action.UNDO)
            KeyboardAction.REDO -> editor.performAction(Editor.Action.REDO)
            KeyboardAction.VOICE_INPUT -> service.startVoiceInput()
            KeyboardAction.OPEN_SETTINGS -> service.openSettings()
            KeyboardAction.HIDE_KEYBOARD -> service.requestHideSelf(0)
            KeyboardAction.SHOW_IME_PICKER -> service.showImePicker()
            KeyboardAction.COMMIT_TEXT -> Unit
        }
        invalidate()
    }

    private fun toggleShift() {
        val now = System.currentTimeMillis()
        if (now - lastShiftTap < 350) {
            capsLock = !capsLock
            shiftOn = capsLock
        } else {
            shiftOn = !shiftOn
            if (!shiftOn) capsLock = false
        }
        lastShiftTap = now
        performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun applyShift(c: Char): Char {
        if (!c.isLetter()) return c
        return if (shiftOn || capsLock) c.uppercaseChar() else c.lowercaseChar()
    }

    private fun metaState(): Int {
        var meta = 0
        if (ctrlOn) meta = meta or android.view.KeyEvent.META_CTRL_ON
        if (altOn) meta = meta or android.view.KeyEvent.META_ALT_ON
        if (shiftOn) meta = meta or android.view.KeyEvent.META_SHIFT_ON
        return meta
    }

    // ------------------------------------------------------------------ //
    // Popup aksen
    // ------------------------------------------------------------------ //
    private fun showPopup(pk: PositionedKey, x: Float) {
        val items = pk.spec.longPress
        if (items.isEmpty()) return
        val itemWidth = dp(42f)
        val width = itemWidth * items.size + dp(8f)
        val height = dp(50f)
        var left = pk.rect.centerX() - width / 2f
        left = left.coerceIn(dp(3f), this.width - width - dp(3f))
        var top = pk.rect.top - height - dp(8f)
        if (top < barHeight + dp(2f)) top = pk.rect.bottom + dp(8f)
        val rect = RectF(left, top, left + width, top + height)
        val state = PopupState(items, rect, itemWidth, 0)
        popup = state
        updatePopupSelection(x)
        performHaptic(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun updatePopupSelection(x: Float) {
        val state = popup ?: return
        val local = x - state.rect.left - dp(4f)
        val index = (local / state.itemWidth).toInt().coerceIn(0, state.items.size - 1)
        if (index != state.selected) {
            state.selected = index
            performHaptic(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // ------------------------------------------------------------------ //
    // Timer
    // ------------------------------------------------------------------ //
    private fun scheduleRepeat(pk: PositionedKey) {
        val runnable = object : Runnable {
            override fun run() {
                if (downKey === pk && !gliding) {
                    dispatch(pk.spec.value)
                    handler.postDelayed(this, 55L)
                }
            }
        }
        repeatRunnable = runnable
        handler.postDelayed(runnable, 380L)
    }

    private fun scheduleSuggestionLongPress(index: Int) {
        val runnable = Runnable {
            if (suggestionPressed == index) {
                suggestionLongPressed = true
                performHaptic(HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }
        }
        suggestionLongPressRunnable = runnable
        handler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun cancelTimers() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
        cancelLongPressTimer()
    }

    private fun cancelLongPressTimer() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun cancelSuggestionLongPress() {
        suggestionLongPressRunnable?.let { handler.removeCallbacks(it) }
        suggestionLongPressRunnable = null
    }

    override fun onDetachedFromWindow() {
        cancelTimers()
        cancelSuggestionLongPress()
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------------ //
    // Callback editor
    // ------------------------------------------------------------------ //
    override fun onSuggestionsChanged(suggestions: List<Suggester.Suggestion>, composing: String) {
        this.suggestions = suggestions
        this.composingWord = composing
        if (!settings.shownWelcome && composing.isNotEmpty()) {
            settings.shownWelcome = true
        }
        invalidate()
    }

    override fun onShiftChanged(shift: Boolean) {
        if (!capsLock) {
            if (shiftOn != shift) {
                shiftOn = shift
                invalidate()
            }
        } else if (!shift) {
            // tetap caps lock
        }
    }

    override fun onToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ //
    // Utilitas
    // ------------------------------------------------------------------ //
    private fun findKey(x: Float, y: Float): PositionedKey? {
        for (i in keys.indices.reversed()) {
            if (keys[i].rect.contains(x, y)) return keys[i]
        }
        return null
    }

    private fun isLetterRow(row: Int): Boolean =
        layout.letterRowRange.firstOrNull() != null && row in layout.letterRowRange

    private fun pixelToGrid(x: Float, y: Float): GlidePoint =
        GlidePoint(x / keyWidth, (y - letterTop) / rowHeight)

    private fun gridToX(p: GlidePoint): Float = p.x * keyWidth

    private fun gridToY(p: GlidePoint): Float = letterTop + p.y * rowHeight

    private fun labelFor(spec: KeySpec): String {
        spec.label?.let { return it }
        return when (val v = spec.value) {
            is KeyValue.CharValue -> {
                val c = applyShift(v.char)
                c.toString()
            }
            is KeyValue.TextValue -> v.text
            is KeyValue.EventValue -> v.event.name
            is KeyValue.ActionValue -> when (v.action) {
                KeyboardAction.SHIFT -> if (capsLock) "⇪" else "⇧"
                KeyboardAction.BACKSPACE -> "⌫"
                KeyboardAction.ENTER -> "⏎"
                KeyboardAction.SPACE -> "spasi"
                KeyboardAction.SWITCH_LAYER -> Layer.fromId(v.payload).displayName
                else -> v.action.name
            }
        }
    }

    private fun cornerLabel(value: KeyValue): String = when (value) {
        is KeyValue.CharValue -> value.char.toString()
        is KeyValue.TextValue -> value.text
        is KeyValue.EventValue -> value.event.name
        is KeyValue.ActionValue -> when (value.action) {
            KeyboardAction.MOVE_WORD_LEFT -> "⇤"
            KeyboardAction.MOVE_WORD_RIGHT -> "⇥"
            else -> value.action.name
        }
    }

    private fun trimToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

    private fun performClickSound() {
        if (settings.soundEnabled) {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                am.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, 0.6f)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun performHaptic(feedback: Int) {
        if (!settings.vibrateEnabled) return
        try {
            @Suppress("DEPRECATION")
            performHapticFeedback(feedback, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        } catch (ignored: Exception) {
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun shiftActive(): Boolean = shiftOn || capsLock
}
