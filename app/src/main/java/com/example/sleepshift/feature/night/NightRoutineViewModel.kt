package com.example.sleepshift.feature.night

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.sleepshift.data.UserPreferenceRepository
import com.example.sleepshift.util.AlarmScheduler
import com.example.sleepshift.util.BedtimeRewardCalculator
import com.example.sleepshift.util.NightRoutineConstants

class NightRoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserPreferenceRepository(application)
    private val alarmScheduler = AlarmScheduler(application)

    private val _coinCount = MutableLiveData<Int>()
    val coinCount: LiveData<Int> = _coinCount

    private val _alarmTime = MutableLiveData<String>()
    val alarmTime: LiveData<String> = _alarmTime

    private val _showInsufficientCoinsDialog = MutableLiveData<Int>() // 부족한 코인 수
    val showInsufficientCoinsDialog: LiveData<Int> = _showInsufficientCoinsDialog

    private val _showAlarmChangeSuccess = MutableLiveData<Pair<Int, String>>() // (남은 코인, 시간)
    val showAlarmChangeSuccess: LiveData<Pair<Int, String>> = _showAlarmChangeSuccess

    private val _showRewardMessage = MutableLiveData<String>()
    val showRewardMessage: LiveData<String> = _showRewardMessage

    private var isAlarmTimeChanged = false

    init {
        loadData()
    }

    private fun loadData() {
        _coinCount.value = repository.getPawCoinCount()
        _alarmTime.value = getCurrentAlarmTime()
    }

    private fun getCurrentAlarmTime(): String {
        return repository.getTodayAlarmTime()  // ⭐ 오늘 설정한 알람 시간
            ?: repository.getTargetWakeTime()    // 목표 기상 시간
            ?: "07:00"                            // 기본값
    }

    /**
     * 알람 변경 가능 여부 확인
     */
    fun canChangeAlarm(): Boolean {
        val currentCoins = _coinCount.value ?: 0
        val required = NightRoutineConstants.ALARM_CHANGE_COST

        if (currentCoins < required) {
            _showInsufficientCoinsDialog.value = required - currentCoins
            return false
        }
        return true
    }

    /**
     * 알람 시간 변경 및 코인 차감
     */
    fun changeAlarmTime(newTime: String, hour: Int, minute: Int) {
        val currentTime = _alarmTime.value ?: return

        if (newTime == currentTime) {
            return
        }

        // 코인 차감
        val currentCoins = _coinCount.value ?: 0
        val newCoins = currentCoins - NightRoutineConstants.ALARM_CHANGE_COST

        repository.setPawCoinCount(newCoins)
        repository.setTodayAlarmTime(newTime)

        _coinCount.value = newCoins
        _alarmTime.value = newTime

        // 알람 설정
        alarmScheduler.scheduleAlarm(hour, minute)

        // 플래그 설정
        isAlarmTimeChanged = true

        _showAlarmChangeSuccess.value = newCoins to newTime
    }

    /**
     * 수면 체크인 처리
     */
    fun processSleepCheckIn(moodName: String, moodPosition: Int) {
        // 취침 보상 계산
        val targetBedtime = repository.getTodayBedtime() ?: repository.getAvgBedtime()
        val rewardResult = BedtimeRewardCalculator.calculateReward(targetBedtime)

        // 보상 지급
        if (rewardResult.rewardAmount > 0) {
            val currentCoins = _coinCount.value ?: 0
            val newCoins = currentCoins + rewardResult.rewardAmount

            repository.setPawCoinCount(newCoins)
            repository.setEarnedBedtimeRewardToday(true)

            _coinCount.value = newCoins
            _showRewardMessage.value = "${rewardResult.message} 곰젤리 +${rewardResult.rewardAmount} (잔여: ${newCoins}개)"
        } else {
            _showRewardMessage.value = rewardResult.message
        }

        // 감정 데이터 저장
        repository.saveMoodData(moodName, moodPosition)
    }

    fun getCoinCount(): Int = _coinCount.value ?: 0
}