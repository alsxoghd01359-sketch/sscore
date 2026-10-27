package com.example.livescore

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.livescore.databinding.FragmentH2hBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class H2HFragment : Fragment() {
    private var _binding: FragmentH2hBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(homeId: Int, awayId: Int) = H2HFragment().apply {
            arguments = Bundle().apply {
                putInt("homeId", homeId)
                putInt("awayId", awayId)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentH2hBinding.inflate(inflater, container, false)

        val homeId = arguments?.getInt("homeId") ?: 0
        val awayId = arguments?.getInt("awayId") ?: 0

        if (homeId != 0 && awayId != 0) {
            loadH2H(homeId, awayId)
        }

        return binding.root
    }

    private fun loadH2H(homeId: Int, awayId: Int) {
        RetrofitClient.apiService.getH2H(homeId, awayId).enqueue(object : Callback<H2HSummaryData> {
            override fun onResponse(call: Call<H2HSummaryData>, response: Response<H2HSummaryData>) {
                if (response.isSuccessful) {
                    val data = response.body() ?: return

                    // 1. 승 무 패 텍스트 세팅
                    binding.tvHomeWins.text = data.homeWins.toString()
                    binding.tvDraws.text = data.draws.toString()
                    binding.tvAwayWins.text = data.awayWins.toString()

                    // 🌟 2. 비율 바 그래프 동적 길이 세팅
                    val total = data.homeWins + data.draws + data.awayWins
                    if (total > 0) {
                        val paramsHome = binding.viewH2HHome.layoutParams as LinearLayout.LayoutParams
                        paramsHome.weight = data.homeWins.toFloat()
                        binding.viewH2HHome.layoutParams = paramsHome

                        val paramsDraw = binding.viewH2HDraw.layoutParams as LinearLayout.LayoutParams
                        paramsDraw.weight = data.draws.toFloat()
                        binding.viewH2HDraw.layoutParams = paramsDraw

                        val paramsAway = binding.viewH2HAway.layoutParams as LinearLayout.LayoutParams
                        paramsAway.weight = data.awayWins.toFloat()
                        binding.viewH2HAway.layoutParams = paramsAway
                    }

                    // 3. 리사이클러뷰에 10경기 목록 세팅 (기존 MatchAdapter 재활용)
                    binding.rvH2HMatches.layoutManager = LinearLayoutManager(context)
                    binding.rvH2HMatches.adapter = MatchAdapter(data.lastMatches)
                }
            }
            override fun onFailure(call: Call<H2HSummaryData>, t: Throwable) {
                Log.e("DEBUG", "H2H 로드 실패: ${t.message}")
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}