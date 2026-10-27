package com.example.livescore

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.livescore.databinding.FragmentStandingsBinding
import retrofit2.*

class StandingsFragment : Fragment() {
    private var _binding: FragmentStandingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStandingsBinding.inflate(inflater, container, false)

        val leagueId = arguments?.getInt("leagueId") ?: 0
        val season = arguments?.getInt("season") ?: 2024
        val homeId = arguments?.getInt("homeId") ?: 0
        val awayId = arguments?.getInt("awayId") ?: 0

        loadStandings(leagueId, season, homeId, awayId)
        return binding.root
    }

    private fun loadStandings(leagueId: Int, season: Int, homeId: Int, awayId: Int) {
        RetrofitClient.apiService.getStandings(leagueId, season).enqueue(object : Callback<List<StandingData>> {
            override fun onResponse(call: Call<List<StandingData>>, response: Response<List<StandingData>>) {
                if (response.isSuccessful) {
                    val standings = response.body() ?: return
                    binding.rvStandings.layoutManager = LinearLayoutManager(context)
                    binding.rvStandings.adapter = StandingsAdapter(
                        standings, homeId, awayId
                    ) { standing ->
                        val intent = Intent(requireContext(), TeamDetailActivity::class.java).apply {
                            putExtra("teamId",     standing.teamId)
                            putExtra("teamName",   standing.teamName)
                            putExtra("leagueId",   leagueId)
                            putExtra("leagueName", leagueIdToName(leagueId))
                            putExtra("season",     season)
                        }
                        startActivity(intent)
                    }
                }
            }
            override fun onFailure(call: Call<List<StandingData>>, t: Throwable) {}
        })
    }

    private fun leagueIdToName(id: Int) = when (id) {
        39  -> "프리미어리그"
        140 -> "라리가"
        78  -> "분데스리가"
        135 -> "세리에 A"
        61  -> "리그 1"
        else -> "리그 $id"
    }

    companion object {
        fun newInstance(leagueId: Int, season: Int, homeId: Int, awayId: Int) = StandingsFragment().apply {
            arguments = Bundle().apply {
                putInt("leagueId", leagueId)
                putInt("season", season)
                putInt("homeId", homeId)
                putInt("awayId", awayId)
            }
        }
    }
}