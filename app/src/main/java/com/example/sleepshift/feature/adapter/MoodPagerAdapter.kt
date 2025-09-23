package com.example.sleepshift.feature.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.sleepshift.R

class MoodPagerAdapter : RecyclerView.Adapter<MoodPagerAdapter.MoodViewHolder>() {

    // 7개의 팬더 감정 이미지와 텍스트
    private val moodList = listOf(
        MoodItem(R.drawable.panda_happy, "기쁨"),
        MoodItem(R.drawable.panda_excited, "신남"),
        MoodItem(R.drawable.panda_calm, "평온"),
        MoodItem(R.drawable.panda_tired, "피곤"),
        MoodItem(R.drawable.panda_sad, "슬픔"),
        MoodItem(R.drawable.panda_angry, "화남"),
        MoodItem(R.drawable.panda_anxious, "불안")
    )

    data class MoodItem(
        val imageRes: Int,
        val moodName: String
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mood_panda, parent, false)
        return MoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: MoodViewHolder, position: Int) {
        holder.bind(moodList[position])
    }

    override fun getItemCount(): Int = moodList.size

    fun getMoodAt(position: Int): MoodItem = moodList[position]

    class MoodViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val pandaImageView: ImageView = itemView.findViewById(R.id.imgPandaMood)

        fun bind(moodItem: MoodItem) {
            pandaImageView.setImageResource(moodItem.imageRes)
        }
    }
}