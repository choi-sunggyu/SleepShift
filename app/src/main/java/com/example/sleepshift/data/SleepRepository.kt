package com.example.sleepshift.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sleepyStore by preferencesDataStore(name = "sleepy_store")

class SleepRepository(private val ctx: Context) {
    private val gson = Gson()

    // 고정 키
    private val KEY_SETTINGS = stringPreferencesKey("sleep_settings")
    private val KEY_PROGRESS = stringPreferencesKey("sleep_progress")
    private val KEY_MORNING = stringPreferencesKey("morning_routine")

    // 날짜별 동적 키
    private fun dailyKey(date: String) = stringPreferencesKey("dailyRecord_$date")
    private fun checkKey(date: String) = stringPreferencesKey("checkinRecord_$date")

    // -------- Settings --------
    suspend fun saveSettings(settings: SleepSettings) {
        ctx.sleepyStore.edit { it[KEY_SETTINGS] = gson.toJson(settings) }
    }
    suspend fun getSettings(): SleepSettings? {
        val json = ctx.sleepyStore.data.map { it[KEY_SETTINGS] }.first() ?: return null
        return runCatching { gson.fromJson(json, SleepSettings::class.java) }.getOrNull()
    }

    // -------- Progress --------
    suspend fun saveProgress(progress: SleepProgress) {
        ctx.sleepyStore.edit { it[KEY_PROGRESS] = gson.toJson(progress) }
    }
    suspend fun getProgress(): SleepProgress? {
        val json = ctx.sleepyStore.data.map { it[KEY_PROGRESS] }.first() ?: return null
        return runCatching { gson.fromJson(json, SleepProgress::class.java) }.getOrNull()
    }

    // -------- Daily Record (per-day) --------
    suspend fun saveDailyRecord(dateYmd: String, rec: DailyRecord) {
        ctx.sleepyStore.edit { it[dailyKey(dateYmd)] = gson.toJson(rec) }
    }
    suspend fun getDailyRecord(dateYmd: String): DailyRecord? {
        val json = ctx.sleepyStore.data.map { it[dailyKey(dateYmd)] }.first() ?: return null
        return runCatching { gson.fromJson(json, DailyRecord::class.java) }.getOrNull()
    }

    // -------- Check-in Record (per-day) --------
    suspend fun saveCheckinRecord(dateYmd: String, rec: CheckinRecord) {
        ctx.sleepyStore.edit { it[checkKey(dateYmd)] = gson.toJson(rec) }
    }
    suspend fun getCheckinRecord(dateYmd: String): CheckinRecord? {
        val json = ctx.sleepyStore.data.map { it[checkKey(dateYmd)] }.first() ?: return null
        return runCatching { gson.fromJson(json, CheckinRecord::class.java) }.getOrNull()
    }

    // -------- Morning Routine (오늘 1개만) --------
    suspend fun saveMorningRoutine(m: MorningRoutine) {
        ctx.sleepyStore.edit { it[KEY_MORNING] = gson.toJson(m) }
    }
    suspend fun getMorningRoutine(): MorningRoutine? {
        val json = ctx.sleepyStore.data.map { it[KEY_MORNING] }.first() ?: return null
        return runCatching { gson.fromJson(json, MorningRoutine::class.java) }.getOrNull()
    }

    // -------- 유틸: 전체 초기화 --------
    suspend fun clearAll() {
        ctx.sleepyStore.edit { it.clear() }
    }
}
