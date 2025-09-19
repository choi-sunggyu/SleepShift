package com.example.sleepshift.feature.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.sleepshift.R

class OnboardingFragment3 : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding3, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    private fun setupViews(view: View) {
        setupClickListeners(view)
    }

    private fun setupClickListeners(view: View) {
        // fragment_onboarding3.xml에서 ID가 btnGetStarted이므로 해당 ID로 찾음
        val btnGetStarted = view.findViewById<TextView>(R.id.btnGetStarted)

        btnGetStarted.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        // 마지막 페이지에서 온보딩 완료
        (activity as? OnboardingActivity)?.finishOnboarding()
    }
}