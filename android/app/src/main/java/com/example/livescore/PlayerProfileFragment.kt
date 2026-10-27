package com.example.livescore

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.livescore.databinding.FragmentPlayerProfileBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlayerProfileFragment : Fragment() {

    private var _binding: FragmentPlayerProfileBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(playerId: Long) = PlayerProfileFragment().apply {
            arguments = Bundle().apply { putLong("playerId", playerId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerProfileBinding.inflate(inflater, container, false)
        val playerId = arguments?.getLong("playerId") ?: return binding.root
        loadDetail(playerId)
        return binding.root
    }

    private fun loadDetail(playerId: Long) {
        binding.pbLoading.visibility = View.VISIBLE
        RetrofitClient.apiService.getPlayerDetail(playerId)
            .enqueue(object : Callback<PlayerDetailData> {
                override fun onResponse(
                    call: Call<PlayerDetailData>, response: Response<PlayerDetailData>
                ) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                    val d = response.body() ?: return
                    renderProfile(d)
                }
                override fun onFailure(call: Call<PlayerDetailData>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                }
            })
    }

    private fun renderProfile(d: PlayerDetailData) {
        // ① 키 + 나이
        binding.tvHeight.text = d.height ?: "-"
        binding.tvAge.text    = if (d.age != null) "${d.age}세" else "-"

        // ② 세부 정보
        binding.tvBirthDate.text   = formatBirthDate(d.birthDate)
        binding.tvNationality.text = d.nationality ?: "-"
        binding.tvSquadNumber.text = d.squadNumber?.toString() ?: "-"

        // ③ 이번 시즌 성적
        val s = d.currentSeasonStats
        if (s != null) {
            // 리그 로고
            val logoUrl = leagueLogoUrl(s.leagueName)
            Glide.with(this)
                .load(logoUrl)
                .into(binding.ivSeasonLeagueLogo)

            // 헤더: 리그명  25/26
            val yr = s.season
            val seasonStr = "${yr}/${(yr + 1).toString().takeLast(2)}"
            binding.tvSeasonHeader.text = "${s.leagueName}  $seasonStr"

            binding.tvSeasonApps.text    = s.appearances.toString()
            binding.tvSeasonGoals.text   = s.goals.toString()
            binding.tvSeasonAssists.text = s.assists.toString()

            if (s.rating != null) {
                binding.tvSeasonRating.text = "%.2f".format(s.rating)
                binding.tvSeasonRating.setTextColor(ratingColor(s.rating))
            } else {
                binding.tvSeasonRating.text = "-"
                binding.tvSeasonRating.setTextColor(Color.parseColor("#6B7280"))
            }
        } else {
            binding.tvSeasonHeader.text  = "이번 시즌 데이터 없음"
            binding.tvSeasonApps.text    = "-"
            binding.tvSeasonGoals.text   = "-"
            binding.tvSeasonAssists.text = "-"
            binding.tvSeasonRating.text  = "-"
            binding.tvSeasonRating.setTextColor(Color.parseColor("#6B7280"))
        }
    }

    private fun formatBirthDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "-"
        return try {
            val p = raw.split("-")
            "${p[0]}년 ${p[1]}월 ${p[2]}일"
        } catch (e: Exception) { raw }
    }

    private fun leagueLogoUrl(name: String?) = when (name) {
        "Premier League" -> "https://media.api-sports.io/football/leagues/39.png"
        "La Liga"        -> "https://media.api-sports.io/football/leagues/140.png"
        "Bundesliga"     -> "https://media.api-sports.io/football/leagues/78.png"
        "Serie A"        -> "https://media.api-sports.io/football/leagues/135.png"
        "Ligue 1"        -> "https://media.api-sports.io/football/leagues/61.png"
        else             -> null
    }

    private fun ratingColor(r: Double) = when {
        r >= 8.0 -> Color.parseColor("#0891B2")
        r >= 7.0 -> Color.parseColor("#16A34A")
        r >= 6.0 -> Color.parseColor("#D97706")
        else     -> Color.parseColor("#DC2626")
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}