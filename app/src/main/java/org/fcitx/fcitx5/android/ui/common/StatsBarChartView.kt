/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import java.util.Locale

/**
 * Simple bar chart without external dependencies.
 * Entries are drawn left to right in the given order.
 */
class StatsBarChartView(context: Context) : View(context) {

    private var entries: List<Pair<String, Long>> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.GRAY
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
        color = Color.GRAY
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
        color = Color.GRAY
    }

    fun setColors(bar: Int, text: Int) {
        barPaint.color = bar
        valuePaint.color = text
        labelPaint.color = text
        invalidate()
    }

    fun setEntries(entries: List<Pair<String, Long>>) {
        this.entries = entries
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val labelHeight = dp(14f)
        val valueHeight = dp(13f)
        val chartHeight = h - labelHeight - valueHeight
        val max = entries.maxOf { it.second }.coerceAtLeast(1L)
        val slot = w / entries.size
        val barWidth = slot * 0.55f
        val labelStep = (entries.size + 7) / 8
        entries.forEachIndexed { i, (label, value) ->
            val cx = slot * i + slot / 2f
            val barHeight = if (value <= 0) 0f
            else (value.toFloat() / max * (chartHeight - dp(2f))).coerceAtLeast(dp(2f))
            if (barHeight > 0f) {
                canvas.drawRect(
                    cx - barWidth / 2f,
                    valueHeight + chartHeight - barHeight,
                    cx + barWidth / 2f,
                    valueHeight + chartHeight,
                    barPaint
                )
                canvas.drawText(compact(value), cx, valueHeight - dp(2f), valuePaint)
            }
            if (i % labelStep == 0 || i == entries.size - 1) {
                canvas.drawText(label, cx, h - dp(2f), labelPaint)
            }
        }
    }

    private fun compact(value: Long): String = when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
        value >= 10_000 -> String.format(Locale.US, "%.1fk", value / 1_000f)
        else -> value.toString()
    }

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
