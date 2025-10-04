package com.example.sleepshift.feature.survey

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.sleepshift.R
import com.google.android.material.textfield.TextInputEditText

class SurveyFragment0 : Fragment() {

    private lateinit var etUserName: TextInputEditText
    private lateinit var btnNext: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_survey0, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etUserName = view.findViewById(R.id.etUserName)
        btnNext = view.findViewById(R.id.btnNext)

        // ⭐ SharedPreferences에서 저장된 이름 불러오기 (있다면)
        val sharedPref = requireActivity().getSharedPreferences("SleepShiftPrefs", Context.MODE_PRIVATE)
        val savedName = sharedPref.getString("user_name", "") ?: ""

        if (savedName.isNotEmpty()) {
            etUserName.setText(savedName)
        }

        btnNext.setOnClickListener {
            val name = etUserName.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(context, "이름을 입력해주세요", Toast.LENGTH_SHORT).show()
                etUserName.requestFocus()
                return@setOnClickListener
            }

            if (name.length < 2) {
                Toast.makeText(context, "이름은 2자 이상 입력해주세요", Toast.LENGTH_SHORT).show()
                etUserName.requestFocus()
                return@setOnClickListener
            }

            // ⭐ SurveyActivity에 이름 저장
            val surveyActivity = activity as? SurveyActivity
            surveyActivity?.userName = name
            android.util.Log.d("SurveyFragment0", "사용자 이름: $name")

            // 다음 페이지로
            surveyActivity?.moveToNextPage()
        }
    }
}