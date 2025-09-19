package com.example.sleepshift.feature.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.example.sleepshift.R

class OnboardingFragment1 : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding1, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    private fun setupViews(view: View) {
        setupClickListeners(view)
    }

    private fun setupClickListeners(view: View) {
        // CardView 전체 영역을 클릭 가능하게 설정
        val btnNext = view.findViewById<CardView>(R.id.btnNext)

        btnNext.setOnClickListener {
            navigateToNextOnboarding()
        }

        // CardView 안의 TextView도 클릭 가능하게 설정 (더 확실한 방법)
        btnNext.isClickable = true
        btnNext.isFocusable = true
    }

    private fun navigateToNextOnboarding() {
        (activity as? OnboardingActivity)?.moveToNextPage()
    }
}