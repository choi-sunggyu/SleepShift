package com.example.sleepshift.feature.survey

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SurveyAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 6  // ⭐ 6으로 변경

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SurveyFragment0()  // ⭐ 이름 입력 Fragment 추가
            1 -> SurveyFragment1()  // 취침/기상 시간
            2 -> SurveyFragment2()
            3 -> SurveyFragment3()
            4 -> SurveyFragment4()
            5 -> SurveyFragment5()  // 완료
            else -> SurveyFragment0()
        }
    }
}