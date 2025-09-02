package com.example.sleepshift.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sleepStore by preferencesDataStore(name = "sleep_shift_prefs")
private val gson = Gson()

class SleepRepository(private val ctx: Context) {

    // 고정 키
    private val SETTINGS_JSON = stringPreferencesKey("sleep_settings_json")
    private val PROGRESS_JSON = stringPreferencesKey("sleep_progress_json")
    private val MORNING_JSON  = stringPreferencesKey("morning_routine_json")

    // 일자별 키 생성
    private fun dailyRecordKey(date: String) = stringPreferencesKey("daily_record_$date")
    private fun checkinRecordKey(date: String) = stringPreferencesKey("checkin_record_$date")

    // ----- Settings -----
    suspend fun getSettings(): SleepSettings? =
        ctx.sleepStore.data.map { it[SETTINGS_JSON] }.first()?.let {
            runCatching { gson.fromJson(it, SleepSettings::class.java) }.getOrNull()
        }

    suspend fun setSettings(s: SleepSettings) {
        ctx.sleepStore.edit { it[SETTINGS_JSON] = gson.toJson(s) }
    }

    // ----- Progress -----
    suspend fun getProgress(): SleepProgress? =
        ctx.sleepStore.data.map { it[PROGRESS_JSON] }.first()?.let {
            runCatching { gson.fromJson(it, SleepProgress::class.java) }.getOrNull()
        }

    suspend fun setProgress(p: SleepProgress) {
        ctx.sleepStore.edit { it[PROGRESS_JSON] = gson.toJson(p) }
    }

    // ----- Daily / Checkin -----
    suspend fun getDailyRecord(date: String): DailyRecord? =
        ctx.sleepStore.data.map { it[dailyRecordKey(date)] }.first()?.let {
            runCatching { gson.fromJson(it, DailyRecord::class.java) }.getOrNull()
        }

    suspend fun setDailyRecord(date: String, r: DailyRecord) {
        ctx.sleepStore.edit { it[dailyRecordKey(date)] = gson.toJson(r) }
    }

    suspend fun getCheckinRecord(date: String): CheckinRecord? =
        ctx.sleepStore.data.map { it[checkinRecordKey(date)] }.first()?.let {
            runCatching { gson.fromJson(it, CheckinRecord::class.java) }.getOrNull()
        }

    suspend fun setCheckinRecord(date: String, r: CheckinRecord) {
        ctx.sleepStore.edit { it[checkinRecordKey(date)] = gson.toJson(r) }
    }

    // ----- Morning Routine (최근 1개만) -----
    suspend fun getMorningRoutine(): MorningRoutine? =
        ctx.sleepStore.data.map { it[MORNING_JSON] }.first()?.let {
            runCatching { gson.fromJson(it, MorningRoutine::class.java) }.getOrNull()
        }

    suspend fun setMorningRoutine(m: MorningRoutine) {
        ctx.sleepStore.edit { it[MORNING_JSON] = gson.toJson(m) }
    }
}
