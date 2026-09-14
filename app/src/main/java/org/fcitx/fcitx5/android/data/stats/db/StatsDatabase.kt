/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.stats.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    /**
     * Date in `yyyy-MM-dd`
     */
    @PrimaryKey val date: String,
    /**
     * Total committed non-whitespace characters
     */
    val chars: Long,
    /**
     * Total commit count
     */
    val commits: Long,
    /**
     * Accumulated active typing duration in milliseconds
     */
    val activeMillis: Long
)

@Entity(tableName = "char_stats")
data class CharStatsEntity(
    @PrimaryKey val char: String,
    val count: Long
)

@Entity(tableName = "word_stats")
data class WordStatsEntity(
    @PrimaryKey val word: String,
    val count: Long
)

@Dao
interface StatsDao {

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getDaily(date: String): DailyStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDaily(entry: DailyStatsEntity)

    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentDaily(limit: Int): List<DailyStatsEntity>

    @Query("SELECT * FROM char_stats WHERE char = :char")
    suspend fun getChar(char: String): CharStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChar(entry: CharStatsEntity)

    @Query("SELECT * FROM char_stats ORDER BY count DESC LIMIT :limit")
    suspend fun getTopChars(limit: Int): List<CharStatsEntity>

    @Query("SELECT * FROM word_stats WHERE word = :word")
    suspend fun getWord(word: String): WordStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(entry: WordStatsEntity)

    @Query("SELECT * FROM word_stats ORDER BY count DESC LIMIT :limit")
    suspend fun getTopWords(limit: Int): List<WordStatsEntity>

    @Transaction
    suspend fun addDaily(date: String, chars: Long, commits: Long, activeMillis: Long) {
        val row = getDaily(date)
        insertDaily(
            if (row == null) DailyStatsEntity(date, chars, commits, activeMillis)
            else row.copy(
                chars = row.chars + chars,
                commits = row.commits + commits,
                activeMillis = row.activeMillis + activeMillis
            )
        )
    }

    @Transaction
    suspend fun addChars(chars: Map<String, Long>) {
        chars.forEach { (c, n) ->
            val row = getChar(c)
            insertChar(if (row == null) CharStatsEntity(c, n) else row.copy(count = row.count + n))
        }
    }

    @Transaction
    suspend fun addWords(words: Map<String, Long>) {
        words.forEach { (w, n) ->
            val row = getWord(w)
            insertWord(if (row == null) WordStatsEntity(w, n) else row.copy(count = row.count + n))
        }
    }
}

@Database(
    entities = [DailyStatsEntity::class, CharStatsEntity::class, WordStatsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StatsDatabase : RoomDatabase() {
    abstract fun statsDao(): StatsDao
}
