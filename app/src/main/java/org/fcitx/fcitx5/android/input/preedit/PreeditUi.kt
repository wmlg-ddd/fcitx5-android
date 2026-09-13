/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.preedit

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.Handler
import android.os.Looper
import android.text.Spanned
import android.text.SpannedString
import android.text.style.DynamicDrawableSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.text.buildSpannedString
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout

open class PreeditUi(
    override val ctx: Context,
    private val theme: Theme,
    private val setupTextView: (TextView.() -> Unit)? = null,
    /**
     * Invoked when the user taps on the (preedit) upper text, with the tapped position
     * (in Java char offsets) inside the preedit string, clamped to [0, preedit.length].
     * The engine cursor can then be moved there by sending Left/Right key events.
     */
    private val onPreeditTapped: ((Int) -> Unit)? = null
) : Ui {

    class CursorSpan(ctx: Context, @ColorInt color: Int, metrics: Paint.FontMetricsInt) :
        DynamicDrawableSpan() {
        private val drawable = ShapeDrawable(RectShape()).apply {
            paint.color = color
            setBounds(0, metrics.ascent, ctx.dp(1), metrics.bottom)
        }

        override fun getDrawable() = drawable
    }

    private val cursorSpan by lazy {
        CursorSpan(ctx, theme.keyTextColor, upView.paint.fontMetricsInt)
    }

    // transparent caret used during the blink-off phase, keeping the caret char in place
    // so the text layout doesn't shift while blinking
    private val hiddenCursorSpan by lazy {
        CursorSpan(ctx, Color.TRANSPARENT, upView.paint.fontMetricsInt)
    }

    private fun createTextView() = textView {
        setTextColor(theme.keyTextColor)
        textSize = 16f
        setupTextView?.invoke(this)
    }

    private val upView = createTextView()

    private val downView = createTextView()

    var visible = false
        private set

    override val root: View = verticalLayout {
        add(upView, lParams())
        add(downView, lParams())
    }

    init {
        if (onPreeditTapped != null) {
            upView.setOnTouchListener { v, event -> onTouch(v, event) }
        }
    }

    private val blinkHandler = Handler(Looper.getMainLooper())
    private var caretVisible = true
    private var lastInputPanel: FcitxEvent.InputPanelEvent.Data? = null

    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (!visible) return
            caretVisible = !caretVisible
            render()
            blinkHandler.postDelayed(this, BlinkInterval)
        }
    }

    private fun updateTextView(view: TextView, str: CharSequence, visible: Boolean) {
        view.text = str
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun update(inputPanel: FcitxEvent.InputPanelEvent.Data) {
        lastInputPanel = inputPanel
        caretVisible = true
        blinkHandler.removeCallbacks(blinkRunnable)
        render()
        if (visible) {
            blinkHandler.postDelayed(blinkRunnable, BlinkInterval)
        }
    }

    private fun render() {
        val inputPanel = lastInputPanel ?: return
        val activeBkg = theme.genericActiveBackgroundColor
        val upString: SpannedString
        val upCursor: Int
        if (inputPanel.auxUp.isEmpty()) {
            upString = inputPanel.preedit.toSpannedString(activeBkg)
            upCursor = inputPanel.preedit.cursor
        } else {
            upString = buildSpannedString {
                append(inputPanel.auxUp.toSpannedString(activeBkg))
                append(inputPanel.preedit.toSpannedString(activeBkg))
            }
            upCursor = inputPanel.preedit.cursor.let {
                if (it < 0) it
                else inputPanel.auxUp.length + it
            }
        }
        val downString = inputPanel.auxDown.toSpannedString(activeBkg)
        val hasUp = upString.isNotEmpty()
        val hasDown = downString.isNotEmpty()
        visible = hasUp || hasDown
        if (!visible) {
            updateTextView(upView, "", false)
            updateTextView(downView, "", false)
            return
        }
        val upStringWithCursor = if (upCursor < 0 || upCursor == upString.length) {
            upString
        } else buildSpannedString {
            if (upCursor > 0) append(upString, 0, upCursor)
            append('|')
            setSpan(
                if (caretVisible) cursorSpan else hiddenCursorSpan,
                upCursor, upCursor + 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            append(upString, upCursor, upString.length)
        }
        updateTextView(upView, upStringWithCursor, hasUp)
        updateTextView(downView, downString, hasDown)
    }

    private fun onTouch(v: View, event: MotionEvent): Boolean {
        // must consume the events, otherwise no ACTION_UP would be
        // dispatched to this view afterwards
        if (event.action == MotionEvent.ACTION_UP) {
            val layout = upView.layout ?: return true
            val line = layout.getLineForVertical(event.y.toInt())
            // convert from view coordinates to layout coordinates
            val x = event.x - upView.totalPaddingLeft + upView.scrollX
            val displayOffset = layout.getOffsetForHorizontal(line, x)
            val target = toPreeditPosition(displayOffset)
            if (target >= 0) {
                onPreeditTapped?.invoke(target)
            }
        }
        return true
    }

    /**
     * Map an offset in the displayed upper text back to a position inside the preedit string,
     * compensating for the [auxUp] prefix and the inserted caret char.
     */
    private fun toPreeditPosition(displayOffset: Int): Int {
        val data = lastInputPanel ?: return -1
        val preeditLen = data.preedit.length
        if (preeditLen == 0) return -1
        val auxUpLen = data.auxUp.length
        val upCursor = if (data.auxUp.isEmpty()) data.preedit.cursor
        else data.preedit.cursor.let { if (it < 0) it else auxUpLen + it }
        val hasCaretChar = upCursor in 0 until (auxUpLen + preeditLen)
        val srcOffset = if (hasCaretChar && displayOffset > upCursor) displayOffset - 1 else displayOffset
        return (srcOffset - auxUpLen).coerceIn(0, preeditLen)
    }

    companion object {
        const val BlinkInterval = 500L
    }
}
