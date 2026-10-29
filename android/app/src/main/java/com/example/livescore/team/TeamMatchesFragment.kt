package com.example.livescore.team

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.livescore.MatchAdapter
import com.example.livescore.databinding.FragmentTeamMatchesBinding

// 팀 경기 목록 (최신순, 아래로 스크롤하면 다음 페이지를 이어서 불러온다)
class TeamMatchesFragment : Fragment() {

    private var _binding: FragmentTeamMatchesBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: TeamDetailViewModel
    private lateinit var matchAdapter: MatchAdapter
    private var shownError: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamMatchesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = teamViewModel()

        matchAdapter = MatchAdapter(mutableListOf())  // 클릭은 MatchAdapter 내부에서 처리
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvTeamMatches.layoutManager = layoutManager
        binding.rvTeamMatches.adapter = matchAdapter

        // 무한 스크롤: 하단 3개 항목 이전에 다음 페이지 미리 요청
        binding.rvTeamMatches.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                if (layoutManager.findLastVisibleItemPosition() >= layoutManager.itemCount - 3) vm.loadMoreMatches()
            }
        })
        binding.errorView.btnRetry.setOnClickListener { vm.loadMoreMatches() }

        vm.matches.observe(viewLifecycleOwner) { state ->
            matchAdapter.updateData(state.items)
            binding.pbLoading.visibility = if (state.loading) View.VISIBLE else View.GONE
            // 첫 페이지를 못 받았을 때만 오류 화면, 그 뒤 페이지 실패는 알림만 띄우고 스크롤하면 다시 시도한다
            val firstPageFailed = state.items.isEmpty() && state.error != null
            binding.errorView.root.visibility = if (firstPageFailed) View.VISIBLE else View.GONE
            if (firstPageFailed) binding.errorView.tvErrorMessage.text = state.error
            if (!firstPageFailed && state.error != null && state.error != shownError) {
                Toast.makeText(requireContext(), state.error, Toast.LENGTH_SHORT).show()
            }
            shownError = state.error
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
