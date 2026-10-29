package com.example.livescore.search

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.livescore.R
import com.example.livescore.SearchResultData
import com.example.livescore.databinding.ItemSearchResultBinding

class SearchResultAdapter(
    private val onClick: (SearchResultData) -> Unit
) : ListAdapter<SearchResultData, SearchResultAdapter.VH>(Diff()) {

    inner class VH(val b: ItemSearchResultBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SearchResultData) {
            // 한글 이름이 있으면 영어 이름 옆에 함께 보여 준다 (예: Arsenal (아스널))
            b.tvName.text    = if (item.nameKo != null) "${item.name} (${item.nameKo})" else item.name
            b.tvSubName.text = item.subName ?: ""

            when (item.type) {
                "PLAYER" -> {
                    Glide.with(b.ivLogo)
                        .load(item.logoUrl)
                        .transform(CircleCrop())
                        .placeholder(R.drawable.bg_player_circle)
                        .into(b.ivLogo)
                    b.tvType.text = "선수"
                    b.tvType.setTextColor(Color.parseColor("#F2711C"))
                }
                "TEAM" -> {
                    Glide.with(b.ivLogo).load(item.logoUrl).into(b.ivLogo)
                    b.tvType.text = "구단"
                    b.tvType.setTextColor(Color.parseColor("#2563EB"))
                }
                "LEAGUE" -> {
                    Glide.with(b.ivLogo).load(item.logoUrl).into(b.ivLogo)
                    b.tvType.text = "리그"
                    b.tvType.setTextColor(Color.parseColor("#CA8A04"))
                }
            }

            b.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class Diff : DiffUtil.ItemCallback<SearchResultData>() {
        override fun areItemsTheSame(a: SearchResultData, b: SearchResultData) =
            a.type == b.type && a.id == b.id
        override fun areContentsTheSame(a: SearchResultData, b: SearchResultData) = a == b
    }
}