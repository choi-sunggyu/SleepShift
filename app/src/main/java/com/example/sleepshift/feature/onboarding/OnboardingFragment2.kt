package com.example.sleepshift.feature.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.example.sleepshift.R

class OnboardingFragment2 : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    private fun setupViews(view: View) {
        setupClickListeners(view)
    }

    private fun setupClickListeners(view: View) {
        // fragment_onboarding2.xml에서 TextView에 직접 ID가 설정되어 있으므로 TextView를 찾음
        val btnNext = view.findViewById<TextView>(R.id.btnNext)

        btnNext.setOnClickListener {
            navigateToNextOnboarding()
        }
    }

    private fun navigateToNextOnboarding() {
        (activity as? OnboardingActivity)?.moveToNextPage()
    }
}