/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.stats

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.stats.TypingStats
import org.fcitx.fcitx5.android.data.stats.db.DailyStatsEntity
import org.fcitx.fcitx5.android.ui.common.StatsBarChartView
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.core.wrapInScrollView
import splitties.views.setPaddingDp
import splitties.views.textResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TypingStatsFragment : Fragment() {

    private var rangeDays = 7

    private lateinit var todayChars: TextView
    private lateinit var todaySpeed: TextView
    private lateinit var totalSummary: TextView
    private lateinit var charsChart: StatsBarChartView
    private lateinit var speedChart: StatsBarChartView
    private lateinit var topChars: TextView
    private lateinit var topWords: TextView
    private lateinit var range7: Button
    private lateinit var range30: Button

    private val dateParser = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = verticalLayoutRoot().wrapInScrollView()

    private fun verticalLayoutRoot() = requireContext().verticalLayout {
        setPaddingDp(16)

        val statsEnabled = AppPrefs.getInstance().stats.statsEnabled
        add(
            horizontalLayout {
                add(
                    textView {
                        textResource = R.string.stats_enable
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    },
                    lParams(width = matchParent, height = wrapContent, weight = 1f)
                )
                add(
                    view(::SwitchCompat) {
                        isChecked = statsEnabled.getValue()
                        setOnCheckedChangeListener { _, checked ->
                            statsEnabled.setValue(checked)
                        }
                    },
                    lParams(width = wrapContent, height = wrapContent)
                )
            },
            lParams(width = matchParent, height = wrapContent)
        )

        add(
            textView {
                textResource = R.string.stats_privacy_hint
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            },
            lParams(width = matchParent, height = wrapContent)
        )

        add(sectionTitle(R.string.stats_today), lParams())
        todayChars = summaryRow(R.string.stats_chars)
        todaySpeed = summaryRow(R.string.stats_speed)
        totalSummary = textView { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }
        add(totalSummary, lParams(width = matchParent, height = wrapContent))

        add(
            horizontalLayout {
                range7 = view(::Button) { textResource = R.string.stats_last_7_days }
                add(range7, lParams(width = wrapContent, height = wrapContent))
                range30 = view(::Button) { textResource = R.string.stats_last_30_days }
                add(range30, lParams(width = wrapContent, height = wrapContent))
                range7.setOnClickListener {
                    rangeDays = 7
                    reload()
                }
                range30.setOnClickListener {
                    rangeDays = 30
                    reload()
                }
            },
            lParams(width = matchParent, height = wrapContent)
        )

        add(sectionTitle(R.string.stats_daily_chars), lParams())
        charsChart = StatsBarChartView(context).also { applyChartColors(it) }
        add(charsChart, lParams(width = matchParent, height = dp(160)))

        add(sectionTitle(R.string.stats_daily_speed), lParams())
        speedChart = StatsBarChartView(context).also { applyChartColors(it) }
        add(speedChart, lParams(width = matchParent, height = dp(160)))

        add(sectionTitle(R.string.stats_top_chars), lParams())
        topChars = textView { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }
        add(topChars, lParams(width = matchParent, height = wrapContent))

        add(sectionTitle(R.string.stats_top_words), lParams())
        topWords = textView { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }
        add(topWords, lParams(width = matchParent, height = wrapContent))
    }

    private fun android.widget.LinearLayout.sectionTitle(res: Int): TextView = textView {
        textResource = res
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        val pad = dp(8f).toInt()
        setPadding(0, pad * 2, 0, pad)
    }

    private fun android.widget.LinearLayout.summaryRow(res: Int): TextView {
        val value = textView {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        add(
            horizontalLayout {
                add(
                    textView {
                        textResource = res
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    },
                    lParams(width = matchParent, height = wrapContent, weight = 1f)
                )
                add(value, lParams(width = wrapContent, height = wrapContent))
            },
            lParams(width = matchParent, height = wrapContent)
        )
        return value
    }

    private fun applyChartColors(chart: StatsBarChartView) {
        fun color(attr: Int): Int {
            val tv = TypedValue()
            requireContext().theme.resolveAttribute(attr, tv, true)
            return tv.data
        }
        chart.setColors(
            bar = color(androidx.appcompat.R.attr.colorPrimary),
            text = color(android.R.attr.textColorSecondary)
        )
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        range7.isEnabled = rangeDays != 7
        range30.isEnabled = rangeDays != 30
        viewLifecycleOwner.lifecycleScope.launch {
            val daily = withContext(Dispatchers.IO) {
                TypingStats.getRecentDaily(rangeDays + 10)
            }
            val chars = withContext(Dispatchers.IO) { TypingStats.getTopChars() }
            val words = withContext(Dispatchers.IO) { TypingStats.getTopWords() }
            render(daily, chars, words)
        }
    }

    private fun render(
        daily: List<DailyStatsEntity>,
        chars: List<org.fcitx.fcitx5.android.data.stats.db.CharStatsEntity>,
        words: List<org.fcitx.fcitx5.android.data.stats.db.WordStatsEntity>
    ) {
        val byDate = daily.associateBy { it.date }
        val today = LocalDate.now()
        val days = (rangeDays - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val rows = days.map { date ->
            val key = date.format(dateParser)
            byDate[key] ?: DailyStatsEntity(key, 0, 0, 0)
        }
        val todayRow = rows.last()
        todayChars.text = todayRow.chars.toString()
        todaySpeed.text = speedOf(todayRow).let { if (it >= 0) it.toString() else "–" }
        totalSummary.text = getString(
            R.string.stats_total_summary,
            daily.sumOf { it.chars },
            daily.size
        )
        charsChart.setEntries(rows.map { label(it) to it.chars })
        speedChart.setEntries(
            rows.map { label(it) to speedOf(it).toLong().coerceAtLeast(0L) }
        )
        topChars.text = if (chars.isEmpty()) {
            getString(R.string.stats_no_data)
        } else buildTagged(chars.take(20).map { it.char to it.count })
        topWords.text = if (words.isEmpty()) {
            getString(R.string.stats_no_data)
        } else buildTagged(words.take(20).map { it.word to it.count })
    }

    private fun label(row: DailyStatsEntity): String {
        val date = LocalDate.parse(row.date, dateParser)
        return "${date.monthValue}/${date.dayOfMonth}"
    }

    /** weighted chars per minute; -1 when there is no active time yet */
    private fun speedOf(row: DailyStatsEntity): Int {
        if (row.activeMillis <= 0L) return if (row.chars > 0) -1 else 0
        return (row.chars * 60000L / row.activeMillis).toInt()
    }

    private fun buildTagged(items: List<Pair<String, Long>>): CharSequence {
        val sb = SpannableStringBuilder()
        items.forEachIndexed { i, (text, count) ->
            if (i > 0) sb.append("   ")
            sb.append(text)
            val start = sb.length
            sb.append(" ($count)")
            sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, 0)
        }
        return sb
    }
}
