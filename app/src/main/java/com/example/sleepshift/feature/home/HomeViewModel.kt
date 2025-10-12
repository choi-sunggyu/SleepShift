package com.example.sleepshift.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.sleepshift.data.UserPreferenceRepository
import com.example.sleepshift.util.ConsecutiveSuccessManager
import com.example.sleepshift.util.DateCalculator
import com.example.sleepshift.util.Constants

class HomeViewModel(application: Application) : AndroidViewModel(application) { //비즈니스 로직

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
        // 앱 설치일 설정
        if (repository.getAppInstallDate() == 0L) {
            repository.setAppInstallDate(DateCalculator.getTodayMidnightTimestamp())
        }

        // 초기 코인 설정
        if (repository.isFirstRun()) {
            repository.setPawCoinCount(Constants.INITIAL_COIN_COUNT)
            repository.setFirstRunCompleted()
        }

        updateAllData()
    }

    fun updateAllData() {
        _currentDay.value = calculateCurrentDay()
        _bedtime.value = getCurrentBedtime()
        _coinCount.value = repository.getPawCoinCount()
        _currentStreak.value = consecutiveSuccessManager.getCurrentStreak()
    }

    private fun calculateCurrentDay(): Int {
        val installDate = repository.getAppInstallDate()
        return DateCalculator.calculateDaysSinceInstall(installDate)
    }

    private fun getCurrentBedtime(): String {
        return repository.getTodayBedtime() ?: repository.getAvgBedtime()
    }

    fun checkDailyProgress() {
        val today = DateCalculator.getTodayDateString()
        val lastCheck = repository.getLastDailyCheck()

        if (lastCheck != today) {
            consecutiveSuccessManager.resetDailyData()

            if (lastCheck.isNotEmpty()) {
                checkYesterdaySuccess()
            }

            repository.setLastDailyCheck(today)
        }

        updateTodayProgress()
    }

    private fun checkYesterdaySuccess() {
        val success = consecutiveSuccessManager.checkTodaySuccess()

        if (success) {
            val streakBefore = consecutiveSuccessManager.getCurrentStreak()
            consecutiveSuccessManager.recordSuccess()
            val streakAfter = consecutiveSuccessManager.getCurrentStreak()

            if (streakBefore == Constants.STREAK_COMPLETION_DAYS && streakAfter == 0) {
                _showStreakCompletion.value = repository.getTotalStreakCompletions()
            }
        } else {
            consecutiveSuccessManager.recordFailure()
            _showStreakBroken.value = Unit
        }

        _currentStreak.value = consecutiveSuccessManager.getCurrentStreak()
    }

    private fun updateTodayProgress() {
        consecutiveSuccessManager.checkTodaySuccess()
    }

    fun addPawCoins(amount: Int) {
        repository.addPawCoins(amount)
        _coinCount.value = repository.getPawCoinCount()
    }

    fun usePawCoins(amount: Int): Boolean {
        val success = repository.usePawCoins(amount)
        if (success) {
            _coinCount.value = repository.getPawCoinCount()
        }
        return success
    }

    fun recordBedtime() {
        consecutiveSuccessManager.recordBedtime(System.currentTimeMillis())
    }

    fun isSurveyCompleted(): Boolean = repository.isSurveyCompleted()

    fun shouldSetupAlarm(): Boolean {
        val lastSleepCheckin = repository.getLastSleepCheckinDate()
        val today = DateCalculator.getTodayDateString()
        val yesterday = DateCalculator.getYesterdayDateString()

        return lastSleepCheckin != yesterday && lastSleepCheckin != today
    }
}