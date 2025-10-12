package com.example.sleepshift.util

import java.util.Calendar

data class BedtimeRewardResult(
    val rewardAmount: Int,
    val message: String,
    val timeDifferenceMinutes: Int
)

object BedtimeRewardCalculator {

    /**
     * 취침 시간 보상 계산
     */
    fun calculateReward(targetBedtime: String): BedtimeRewardResult {
        val bedtimeParts = targetBedtime.split(":")
        val bedtimeHour = bedtimeParts.getOrNull(0)?.toIntOrNull() ?: 23
        val bedtimeMinute = bedtimeParts.getOrNull(1)?.toIntOrNull() ?: 0

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        var bedtimeInMinutes = bedtimeHour * 60 + bedtimeMinute
        var currentTimeInMinutes = currentHour * 60 + currentMinute

        // 자정 넘김 처리
        if (bedtimeHour in 0..4) {
            bedtimeInMinutes += 1440
        }
        if (currentHour in 0..4) {
            currentTimeInMinutes += 1440
        }

        val timeDifference = currentTimeInMinutes - bedtimeInMinutes

        val (reward, message) = when {
            // 완벽한 시간 (60분 전 ~ 정각)
            timeDifference in -NightRoutineConstants.EARLY_TOLERANCE_MINUTES..0 -> {
                NightRoutineConstants.PERFECT_BEDTIME_REWARD to
                        "✨ 완벽한 취침 시간!"
            }

            // 괜찮은 시간 (정각 ~ 60분 후)
            timeDifference in 1..NightRoutineConstants.LATE_TOLERANCE_MINUTES -> {
                NightRoutineConstants.GOOD_BEDTIME_REWARD to
                        "😊 취침 완료!"
            }

            // 너무 이르거나 늦음
            timeDifference < -NightRoutineConstants.EARLY_TOLERANCE_MINUTES -> {
                0 to "너무 일찍 체크인했습니다"
            }

            else -> {
                val minutesLate = timeDifference - NightRoutineConstants.LATE_TOLERANCE_MINUTES
                0 to "취침 시간($targetBedtime)을 ${minutesLate}분 넘겼습니다"
            }
        }

        return BedtimeRewardResult(reward, message, timeDifference)
    }
}