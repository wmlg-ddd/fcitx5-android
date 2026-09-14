/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.stats

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.stats.db.StatsDao
import org.fcitx.fcitx5.android.data.stats.db.StatsDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects local typing statistics from committed text.
 *
 * Everything stays on the device; password fields are never recorded.
 */
object TypingStats {

    /**
     * Time since the last commit is counted as active typing time if it does
     * not exceed this interval, otherwise the commit starts a new burst.
     */
    private const val BURST_INTERVAL_MILLIS = 4000L
    private const val MAX_WORD_LENGTH = 10
    const val TOP_COUNT = 50

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private lateinit var dao: StatsDao
    private var enabled = true

    /**
     * Uptime of the last commit, for burst (speed) accounting
     */
    private var lastCommitElapsed = 0L

    fun init(context: Context) {
        val db = Room.databaseBuilder(context, StatsDatabase::class.java, "statsdb")
            // allow wipe the database instead of crashing when downgrade
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
        dao = db.statsDao()
        enabled = AppPrefs.getInstance().stats.statsEnabled.getValue()
        AppPrefs.getInstance().stats.statsEnabled.registerOnChangeListener { _, v ->
            enabled = v
        }
    }

    /**
     * Record a commit. Called on every committed text from the input service.
     *
     * @param isPassword whether the committed text goes into a password field
     */
    fun onCommit(text: String, isPassword: Boolean) {
        if (!enabled || isPassword) return
        val trimmed = text.trim()
        val chars = trimmed.count { !it.isWhitespace() }.toLong()
        if (chars == 0L) return
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastCommitElapsed
        lastCommitElapsed = now
        val activeMillis = if (delta in 1 until BURST_INTERVAL_MILLIS) delta else 0L
        val date = dateFormat.format(Date())
        val charCounts = HashMap<String, Long>()
        trimmed.forEach { c ->
            if (!c.isWhitespace()) {
                val key = c.toString()
                charCounts[key] = (charCounts[key] ?: 0L) + 1L
            }
        }
        val wordCounts = HashMap<String, Long>()
        trimmed.split(Regex("\\s+")).forEach { word ->
            if (word.isNotEmpty() && word.length <= MAX_WORD_LENGTH) {
                wordCounts[word] = (wordCounts[word] ?: 0L) + 1L
            }
        }
        scope.launch {
            dao.addDaily(date, chars, 1L, activeMillis)
            dao.addChars(charCounts)
            dao.addWords(wordCounts)
        }
    }

    suspend fun getRecentDaily(limit: Int) = dao.getRecentDaily(limit)

    suspend fun getTopChars(limit: Int = TOP_COUNT) = dao.getTopChars(limit)

    suspend fun getTopWords(limit: Int = TOP_COUNT) = dao.getTopWords(limit)
}
