package com.example.livescore.match

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.livescore.StandingsAdapter
import com.example.livescore.common.StateHost
import com.example.livescore.databinding.FragmentStandingsBinding
import com.example.livescore.team.TeamDetailActivity

// 경기 전 탭의 리그 순위 (두 팀 강조)
class StandingsFragment : Fragment() {
    private var _binding: FragmentStandingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var host: StateHost

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStandingsBinding.inflate(inflater, container, false)
        host = StateHost(requireContext(), binding.root)
        return host.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = matchDetailViewModel()
        host.onRetry { vm.standings.retry() }
        vm.standings.state.observe(viewLifecycleOwner) { state ->
            host.render(state) { standings ->
                binding.rvStandings.layoutManager = LinearLayoutManager(context)
                binding.rvStandings.adapter = StandingsAdapter(standings, vm.homeTeamId, vm.awayTeamId) { standing ->
                    startActivity(
                        Intent(requireContext(), TeamDetailActivity::class.java).apply {
                            putExtra(TeamDetailActivity.EXTRA_TEAM_ID, standing.teamId)
                            putExtra(TeamDetailActivity.EXTRA_TEAM_NAME, standing.teamName)
                            putExtra(TeamDetailActivity.EXTRA_LEAGUE_ID, vm.leagueId)
                        }
                    )
                }
            }
        }
        vm.standings.ensureLoaded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = StandingsFragment()
    }
}
