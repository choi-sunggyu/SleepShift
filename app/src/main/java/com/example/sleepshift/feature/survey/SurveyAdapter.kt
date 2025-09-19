package com.example.sleepshift.feature.survey

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SurveyAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SurveyFragment1()
            1 -> SurveyFragment2()
            2 -> SurveyFragment3()
            3 -> SurveyFragment4()
            4 -> SurveyFragment5()
            else -> SurveyFragment1()
        }
    }
}