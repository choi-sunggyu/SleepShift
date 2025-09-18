package com.example.sleepshift.feature.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        // 첫 번째 페이지 초기화 코드
        setupViews(view)
    }

    private fun setupViews(view: View) {
        // 뷰 초기화 및 이벤트 리스너 설정
        // 예: 애니메이션, 텍스트 설정 등
    }
}