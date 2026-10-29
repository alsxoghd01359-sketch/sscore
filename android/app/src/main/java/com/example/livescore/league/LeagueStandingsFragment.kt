package com.example.livescore.league

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
import com.example.livescore.*
import com.example.livescore.common.renderState
import com.example.livescore.team.TeamDetailActivity

class LeagueStandingsFragment : Fragment() {

    private var _binding: FragmentLeagueStandingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: LeagueDetailViewModel
    private val leagueId get() = vm.leagueId
    private val selectedSeason get() = vm.selectedSeason.value ?: 0

    companion object {
        fun newInstance(leagueId: Int) = LeagueStandingsFragment().apply {
            arguments = Bundle().apply { putInt(ARG_LEAGUE_ID, leagueId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeagueStandingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = leagueViewModel()
        binding.spinnerSeason.bindSeasons(viewLifecycleOwner, vm)
        binding.errorView.btnRetry.setOnClickListener { vm.retry(vm.standings) }
        vm.selectedSeason.observe(viewLifecycleOwner) { season -> if (season != null) vm.standings.load(season) }
        vm.standings.state.observe(viewLifecycleOwner) { state ->
            binding.rvStandings.adapter = null
            renderState(state, binding.pbLoading, binding.errorView) { data ->
                binding.rvStandings.layoutManager = LinearLayoutManager(requireContext())
                binding.rvStandings.adapter = StandingsAdapter(data, 0, 0) { standing ->
                    startActivity(
                        Intent(requireContext(), TeamDetailActivity::class.java).apply {
                            putExtra(TeamDetailActivity.EXTRA_TEAM_ID, standing.teamId)
                            putExtra(TeamDetailActivity.EXTRA_TEAM_NAME, standing.teamName)
                            putExtra(TeamDetailActivity.EXTRA_LEAGUE_ID, leagueId)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}