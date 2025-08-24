package com.example.sleepshift.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ✅ 파일 최상단(클래스 밖)에 1번만 선언
private val Context.dataStore by preferencesDataStore(name = "sleep_shift_prefs")

class DataStoreManager(private val ctx: Context) {
    companion object {
        // ✅ 온보딩 여부 키
        val ONBOARDING = booleanPreferencesKey("onboarding_completed")

        // (기타 키들)
        val REASON = stringPreferencesKey("reason")
        val BEFORE_BED = stringPreferencesKey("before_bed")
        val REPORTED_SLEEP_TIME = stringPreferencesKey("reported_sleep_time")
        val CURRENT_BEDTIME = stringPreferencesKey("current_bedtime")
        val TARGET_WAKE = stringPreferencesKey("target_wake")
        val TARGET_SLEEP_MIN = intPreferencesKey("target_sleep_min")
        val TARGET_BEDTIME = stringPreferencesKey("target_bedtime")
        val PROGRESS_BEDTIME = stringPreferencesKey("progress_bedtime")
        val NEXT_ALARM_EPOCH = longPreferencesKey("next_alarm_epoch")
        val STEP_MIN = intPreferencesKey("step_min")
    }

    // ✅ 온보딩 완료 여부 Flow
    val onboardingCompleted: Flow<Boolean> =
        ctx.dataStore.data.map { it[ONBOARDING] ?: false }

    // (기타 Flow들)
    val currentBedtime: Flow<String?> = ctx.dataStore.data.map { it[CURRENT_BEDTIME] }
    val targetWakeTime: Flow<String?> = ctx.dataStore.data.map { it[TARGET_WAKE] }
    val targetSleepDurationMinutes: Flow<Int?> = ctx.dataStore.data.map { it[TARGET_SLEEP_MIN] }
    val targetBedtime: Flow<String?> = ctx.dataStore.data.map { it[TARGET_BEDTIME] }
    val progressBedtime: Flow<String?> = ctx.dataStore.data.map { it[PROGRESS_BEDTIME] }
    val nextAlarmEpoch: Flow<Long?> = ctx.dataStore.data.map { it[NEXT_ALARM_EPOCH] }
    val stepMinutes: Flow<Int> = ctx.dataStore.data.map { it[STEP_MIN] ?: 30 }

    // ✅ 온보딩 완료 세터
    suspend fun setOnboardingCompleted(v: Boolean) =
        ctx.dataStore.edit { it[ONBOARDING] = v }

    // (기타 세터들)
    suspend fun setReason(v: String) = ctx.dataStore.edit { it[REASON] = v }
    suspend fun setBeforeBedText(v: String) = ctx.dataStore.edit { it[BEFORE_BED] = v }
    suspend fun setReportedSleepTime(v: String) = ctx.dataStore.edit { it[REPORTED_SLEEP_TIME] = v }
    suspend fun setCurrentBedtime(v: String) = ctx.dataStore.edit { it[CURRENT_BEDTIME] = v }
    suspend fun setTargetWakeTime(v: String) = ctx.dataStore.edit { it[TARGET_WAKE] = v }
    suspend fun setTargetSleepDurationMinutes(v: Int) = ctx.dataStore.edit { it[TARGET_SLEEP_MIN] = v }
    suspend fun setTargetBedtime(v: String) = ctx.dataStore.edit { it[TARGET_BEDTIME] = v }
    suspend fun setProgressBedtime(v: String) = ctx.dataStore.edit { it[PROGRESS_BEDTIME] = v }
    suspend fun setNextAlarmEpoch(v: Long) = ctx.dataStore.edit { it[NEXT_ALARM_EPOCH] = v }
    suspend fun setStepMinutes(v: Int) = ctx.dataStore.edit { it[STEP_MIN] = v }
}
