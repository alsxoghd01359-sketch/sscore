package com.example.livescore // 본인 프로젝트 패키지명에 맞게 확인하세요!

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.livescore.databinding.ItemDateBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DateAdapter(
    private val onItemClick: (LocalDate, Int) -> Unit
) : RecyclerView.Adapter<DateAdapter.DateViewHolder>() {

    private val baseDate = LocalDate.now()
    private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd")
    private val dayFormatter = DateTimeFormatter.ofPattern("E", Locale.KOREAN)

    val centerPosition = Int.MAX_VALUE / 2
    private var selectedPosition = centerPosition

    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousPosition)
        notifyItemChanged(selectedPosition)
    }

    inner class DateViewHolder(private val binding: ItemDateBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(position: Int) {
            val offset = (position - centerPosition).toLong()
            val targetDate = baseDate.plusDays(offset)

            val dateStr = targetDate.format(dateFormatter)
            val dayStr = targetDate.format(dayFormatter)

            binding.tvDate.text = dateStr
            binding.tvDay.text = dayStr

            if (position == selectedPosition) {
                binding.tvDate.setTextColor(Color.parseColor("#20E07A"))
                binding.tvDay.setTextColor(Color.parseColor("#20E07A"))
            } else {
                binding.tvDate.setTextColor(Color.parseColor("#FFFFFF"))
                binding.tvDay.setTextColor(Color.parseColor("#888888"))
            }

            binding.root.setOnClickListener {
                setSelectedPosition(position)
                onItemClick(targetDate, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val binding = ItemDateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        holder.bind(position)
    }

    // 🌟 핵심 수정: 아이템 전체 개수도 무한대(약 21억개)로 반환하도록 설정
    override fun getItemCount(): Int {
        return Int.MAX_VALUE
    }
}