package com.example.livescore.team

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.livescore.StandingsAdapter
import com.example.livescore.common.renderState
import com.example.livescore.databinding.FragmentTeamStandingsBinding

// 팀이 속한 리그의 순위표. 이 팀을 강조하고 그 위치로 스크롤한다.
class TeamStandingsFragment : Fragment() {

    private var _binding: FragmentTeamStandingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: TeamDetailViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamStandingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = teamViewModel()
        binding.rvTeamStandings.layoutManager = LinearLayoutManager(requireContext())
        SeasonDropdown(
            this, vm, binding.layoutSeasonSelector, binding.scrollSeasonList, binding.layoutSeasonList,
            binding.tvDropdownArrow, binding.tvLeagueName, binding.tvCurrentSeason, binding.ivLeagueLogo
        ).bind()
        binding.errorView.btnRetry.setOnClickListener { vm.retry(vm.standings) }
        vm.selectedSeason.observe(viewLifecycleOwner) { season -> if (season != null) vm.standings.load(season) }
        vm.standings.state.observe(viewLifecycleOwner) { state ->
            binding.rvTeamStandings.visibility = View.GONE
            renderState(state, binding.pbStandingsLoading, binding.errorView) { standings ->
                binding.rvTeamStandings.visibility = View.VISIBLE
                // 이 팀을 강조한다 (홈/어웨이 자리에 같은 값을 넣어 같은 색으로 표시)
                binding.rvTeamStandings.adapter = StandingsAdapter(standings, vm.teamId, vm.teamId) { standing ->
                    openTeamDetail(standing.teamId, standing.teamName)
                }
                val focusPos = standings.indexOfFirst { it.teamId == vm.teamId }
                if (focusPos >= 0) {
                    (binding.rvTeamStandings.layoutManager as LinearLayoutManager)
                        .scrollToPositionWithOffset((focusPos - 2).coerceAtLeast(0), dp(8))
                }
            }
        }
    }

    private fun openTeamDetail(teamId: Int, teamName: String) {
        startActivity(Intent(requireContext(), TeamDetailActivity::class.java).apply {
            putExtra(TeamDetailActivity.EXTRA_TEAM_ID, teamId)
            putExtra(TeamDetailActivity.EXTRA_TEAM_NAME, teamName)
            putExtra(TeamDetailActivity.EXTRA_LEAGUE_ID, vm.leagueId.value ?: 0)
            putExtra("leagueName", vm.info.value?.leagueName.orEmpty())
            vm.selectedSeason.value?.let { putExtra("season", it) }
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
