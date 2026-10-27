package com.example.livescore

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.livescore.databinding.FragmentLeagueStandingsBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LeagueStandingsFragment : Fragment() {

    private var _binding: FragmentLeagueStandingsBinding? = null
    private val binding get() = _binding!!
    private var leagueId = 39
    private var currentSeason = 2025

    private val seasons = (2025 downTo 2015).toList()

    companion object {
        fun newInstance(leagueId: Int) = LeagueStandingsFragment().apply {
            arguments = Bundle().apply { putInt("leagueId", leagueId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeagueStandingsBinding.inflate(inflater, container, false)
        leagueId = arguments?.getInt("leagueId") ?: 39
        setupSeasonSpinner()
        loadStandings()
        return binding.root
    }

    private fun setupSeasonSpinner() {
        val labels = seasons.map { "$it/${(it+1).toString().takeLast(2)}" }
        val adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, labels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerSeason.adapter = adapter
        binding.spinnerSeason.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentSeason = seasons[pos]
                loadStandings()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun loadStandings() {
        binding.pbLoading.visibility = View.VISIBLE
        RetrofitClient.apiService.getStandings(leagueId, currentSeason)
            .enqueue(object : Callback<List<StandingData>> {
                override fun onResponse(
                    call: Call<List<StandingData>>, response: Response<List<StandingData>>
                ) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                    val list = response.body() ?: return
                    binding.rvStandings.layoutManager = LinearLayoutManager(requireContext())
                    binding.rvStandings.adapter = StandingsAdapter(list, 0, 0) { standing ->
                        startActivity(
                            Intent(requireContext(), TeamDetailActivity::class.java).apply {
                                putExtra(TeamDetailActivity.EXTRA_TEAM_ID,   standing.teamId)
                                putExtra(TeamDetailActivity.EXTRA_TEAM_NAME, standing.teamName)
                                putExtra(TeamDetailActivity.EXTRA_LEAGUE_ID, leagueId)
                            }
                        )
                    }
                }
                override fun onFailure(call: Call<List<StandingData>>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                }
            })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}