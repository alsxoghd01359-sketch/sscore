package com.example.livescore

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.livescore.databinding.FragmentTeamSquadBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamSquadFragment : Fragment() {

    private var _binding: FragmentTeamSquadBinding? = null
    private val binding get() = _binding!!

    private var teamId = 0

    companion object {
        fun newInstance(teamId: Int) = TeamSquadFragment().apply {
            arguments = Bundle().apply { putInt("teamId", teamId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamSquadBinding.inflate(inflater, container, false)
        teamId = arguments?.getInt("teamId") ?: return binding.root
        loadSquad()
        return binding.root
    }

    private fun loadSquad() {
        binding.pbSquadLoading.visibility = View.VISIBLE

        RetrofitClient.apiService.getTeamSquad(teamId)
            .enqueue(object : Callback<SquadData> {
                override fun onResponse(call: Call<SquadData>, response: Response<SquadData>) {
                    if (!isAdded || _binding == null) return
                    binding.pbSquadLoading.visibility = View.GONE
                    val squad = response.body() ?: return
                    renderGroup(binding.layoutGK, squad.goalkeepers)
                    renderGroup(binding.layoutDF, squad.defenders)
                    renderGroup(binding.layoutMF, squad.midfielders)
                    renderGroup(binding.layoutFW, squad.attackers)
                }
                override fun onFailure(call: Call<SquadData>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    binding.pbSquadLoading.visibility = View.GONE
                }
            })
    }

    private fun renderGroup(container: LinearLayout, players: List<SquadPlayerData>) {
        container.removeAllViews()
        if (players.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE

        players.forEachIndexed { idx, player ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_squad_player, container, false)

            // 프로필 사진
            val ivPhoto = row.findViewById<ImageView>(R.id.ivSquadPhoto)
            Glide.with(this)
                .load(player.photoUrl)
                .transform(FaceCropTransformation(), CircleCrop())
                .placeholder(R.drawable.bg_player_circle)
                .into(ivPhoto)

            // 등번호 - 이름
            val numStr = player.number?.toString() ?: "-"
            row.findViewById<TextView>(R.id.tvSquadNumberName).text = "$numStr  ${player.name}"

            // 국적
            row.findViewById<TextView>(R.id.tvSquadNationality).text = player.nationality ?: "-"

            // 나이
            row.findViewById<TextView>(R.id.tvSquadAge).text =
                if (player.age != null) "${player.age}" else "-"

            // 선수 클릭 → 선수 세부 정보 페이지
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener {
                startActivity(
                    Intent(requireContext(), PlayerDetailActivity::class.java).apply {
                        putExtra(PlayerDetailActivity.EXTRA_PLAYER_ID, player.playerId)
                    }
                )
            }

            // 구분선 (마지막 행 제외)
            if (idx < players.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                    ).also { it.setMargins(dp(14), 0, dp(14), 0) }
                    setBackgroundColor(Color.parseColor("#E6E8EB"))
                }
                container.addView(row)
                container.addView(divider)
            } else {
                container.addView(row)
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}