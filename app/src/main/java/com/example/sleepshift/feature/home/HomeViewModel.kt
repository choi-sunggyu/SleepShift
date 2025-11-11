package com.example.sleepshift.feature.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.sleepshift.data.UserPreferenceRepository
import com.example.sleepshift.util.ConsecutiveSuccessManager
import com.example.sleepshift.util.DateCalculator
import com.example.sleepshift.util.Constants

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserPreferenceRepository(application)
    private val consecutiveSuccessManager = ConsecutiveSuccessManager(application)

    private val _currentDay = MutableLiveData<Int>()
    val currentDay: LiveData<Int> = _currentDay

    private val _bedtime = MutableLiveData<String>()
    val bedtime: LiveData<String> = _bedtime

    private val _coinCount = MutableLiveData<Int>()
    val coinCount: LiveData<Int> = _coinCount

    private val _currentStreak = MutableLiveData<Int>()
    val currentStreak: LiveData<Int> = _currentStreak

    private val _showStreakCompletion = MutableLiveData<Int>()
    val showStreakCompletion: LiveData<Int> = _showStreakCompletion

    private val _showStreakBroken = MutableLiveData<Unit>()
    val showStreakBroken: LiveData<Unit> = _showStreakBroken

    init {
        initializeApp()
    }

    private fun initializeApp() {
        // 앱 설치일 설정 (처음만)
        if (repository.getAppInstallDate() == 0L) {
            repository.setAppInstallDate(DateCalculator.getTodayMidnightTimestamp())
            Log.d("HomeViewModel", "앱 설치일 설정됨")
        }

        // 처음 실행시 초기 코인
        if (repository.isFirstRun()) {
            repository.setPawCoinCount(Constants.INITIAL_COIN_COUNT)
            repository.setFirstRunCompleted()
            Log.d("HomeViewModel", "초기 코인 ${Constants.INITIAL_COIN_COUNT}개 지급")
        }

        updateAllData()
    }

    fun updateAllData() {
        _currentDay.value = calculateCurrentDay()
        _bedtime.value = getCurrentBedtime()
        _coinCount.value = repository.getPawCoinCount()
        _currentStreak.value = consecutiveSuccessManager.getCurrentStreak()

        Log.d("HomeViewModel", "데이터 업데이트 - Day: ${_currentDay.value}, 코인: ${_coinCount.value}, 연속: ${_currentStreak.value}일")
    }

    private fun calculateCurrentDay(): Int {
        val installDate = repository.getAppInstallDate()
        return DateCalculator.calculateDaysSinceInstall(installDate)
    }

    private fun getCurrentBedtime(): String {
        // 오늘 취침 시간 있으면 그거, 없으면 평균 사용
        return repository.getTodayBedtime() ?: repository.getAvgBedtime()
    }

    fun checkDailyProgress() {
        val today = DateCalculator.getTodayDateString()
        val lastCheck = repository.getLastDailyCheck()

        Log.d("HomeViewModel", "일일 체크 - 오늘: $today, 마지막체크: $lastCheck")

        // 오늘 처음 체크하는거면
        if (lastCheck != today) {
            // 어제까지 체크했었으면 어제 결과 확인
            if (lastCheck.isNotEmpty()) {
                checkYesterdayResult()
            }

            repository.setLastDailyCheck(today)
            Log.d("HomeViewModel", "일일 체크 날짜 업데이트: $today")
        }

        // 현재 연속일 업데이트
        _currentStreak.value = consecutiveSuccessManager.getCurrentStreak()
    }

    private fun checkYesterdayResult() {
        val yesterday = DateCalculator.getYesterdayDateString()

        // 어제 취침/기상 성공했는지 확인
        val bedtimeOk = repository.getBedtimeSuccess(yesterday)
        val wakeOk = repository.getWakeSuccess(yesterday)
        val yesterdaySuccess = bedtimeOk && wakeOk

        Log.d("HomeViewModel", "어제($yesterday) 결과 - 취침: $bedtimeOk, 기상: $wakeOk")

        if (yesterdaySuccess) {
            // 성공! 연속일 증가
            val streakBefore = consecutiveSuccessManager.getCurrentStreak()

            // 이미 3일이면 달성 후 리셋
            if (streakBefore >= Constants.STREAK_COMPLETION_DAYS) {
                _showStreakCompletion.value = repository.getTotalStreakCompletions()
                Log.d("HomeViewModel", "3일 연속 달성!")
            }

            Log.d("HomeViewModel", "어제 성공 - 연속일 체크")
        } else {
            // 실패 - 연속 끊김
            val hadStreak = consecutiveSuccessManager.getCurrentStreak() > 0
            if (hadStreak) {
                _showStreakBroken.value = Unit
                Log.d("HomeViewModel", "연속 실패 - 리셋")
            }
        }
    }

    fun addPawCoins(amount: Int) {
        repository.addPawCoins(amount)
        _coinCount.value = repository.getPawCoinCount()
        Log.d("HomeViewModel", "코인 +$amount (총: ${_coinCount.value})")
    }

    fun usePawCoins(amount: Int): Boolean {
        val success = repository.usePawCoins(amount)
        if (success) {
            _coinCount.value = repository.getPawCoinCount()
            Log.d("HomeViewModel", "코인 -$amount (총: ${_coinCount.value})")
        } else {
            Log.d("HomeViewModel", "코인 부족 - 사용 실패")
        }
        return success
    }

    fun isSurveyCompleted(): Boolean {
        return repository.isSurveyCompleted()
    }

    fun shouldSetupAlarm(): Boolean {
        val lastSleep = repository.getLastSleepCheckinDate()
        val today = DateCalculator.getTodayDateString()
        val yesterday = DateCalculator.getYesterdayDateString()

        // 어제나 오늘 수면 체크인 안했으면 알람 설정 필요
        val needSetup = lastSleep != yesterday && lastSleep != today

        Log.d("HomeViewModel", "알람 설정 필요? $needSetup (마지막 수면: $lastSleep)")

        return needSetup
    }
}