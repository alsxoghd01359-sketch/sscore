package com.example.livescore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.livescore.databinding.FragmentTeamMatchesBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamMatchesFragment : Fragment() {

    private var _binding: FragmentTeamMatchesBinding? = null
    private val binding get() = _binding!!

    private lateinit var matchAdapter: MatchAdapter
    private val accumulatedMatches = mutableListOf<MatchData>()

    private var teamId    = 0
    private var leagueId  = 0
    private var season    = 0
    private var currentPage = 0
    private var isLoading   = false
    private var hasMore     = true

    companion object {
        fun newInstance(teamId: Int, leagueId: Int, season: Int) =
            TeamMatchesFragment().apply {
                arguments = Bundle().apply {
                    putInt("teamId", teamId)
                    putInt("leagueId", leagueId)
                    putInt("season", season)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamMatchesBinding.inflate(inflater, container, false)

        teamId   = arguments?.getInt("teamId")   ?: return binding.root
        leagueId = arguments?.getInt("leagueId") ?: return binding.root
        season   = arguments?.getInt("season")   ?: return binding.root

        setupRecyclerView()
        loadNextPage()   // 첫 15경기 로드
        return binding.root
    }

    private fun setupRecyclerView() {
        matchAdapter = MatchAdapter(mutableListOf())  // 클릭은 MatchAdapter 내부에서 처리

        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvTeamMatches.layoutManager = layoutManager
        binding.rvTeamMatches.adapter = matchAdapter

        // 무한 스크롤: 하단 3개 항목 이전에 다음 페이지 미리 요청
        binding.rvTeamMatches.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoading || !hasMore) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val totalItems  = layoutManager.itemCount
                if (lastVisible >= totalItems - 3) {
                    loadNextPage()
                }
            }
        })
    }

    private fun loadNextPage() {
        if (isLoading || !hasMore) return
        isLoading = true
        binding.pbLoading.visibility = View.VISIBLE

        RetrofitClient.apiService.getTeamMatches(teamId, currentPage, 15)
            .enqueue(object : Callback<TeamMatchesPageData> {
                override fun onResponse(
                    call: Call<TeamMatchesPageData>,
                    response: Response<TeamMatchesPageData>
                ) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                    isLoading = false

                    val data = response.body() ?: return
                    accumulatedMatches.addAll(data.matches)
                    matchAdapter.updateData(accumulatedMatches.toList())
                    hasMore     = data.hasMore
                    currentPage = data.currentPage + 1
                }

                override fun onFailure(call: Call<TeamMatchesPageData>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    binding.pbLoading.visibility = View.GONE
                    isLoading = false
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}