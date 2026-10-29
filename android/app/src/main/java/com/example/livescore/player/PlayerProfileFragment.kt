package com.example.livescore.player

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.livescore.databinding.FragmentPlayerProfileBinding
import com.example.livescore.*
import com.example.livescore.common.renderState

class PlayerProfileFragment : Fragment() {

    private var _binding: FragmentPlayerProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: PlayerDetailViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = playerViewModel()
        binding.errorView.btnRetry.setOnClickListener { vm.detail.retry() }
        vm.detail.ensureLoaded()
        vm.detail.state.observe(viewLifecycleOwner) { state ->
            renderState(state, binding.pbLoading, binding.errorView) { renderProfile(it) }
        }
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