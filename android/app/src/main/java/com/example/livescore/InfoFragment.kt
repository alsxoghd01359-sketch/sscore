package com.example.livescore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.livescore.databinding.FragmentInfoBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class InfoFragment : Fragment() {

    private var _binding: FragmentInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fixtureId = arguments?.getLong("fixtureId") ?: -1L
        val stadium = arguments?.getString("stadium") ?: "정보 없음"
        val round = arguments?.getString("round") ?: "정보 없음"
        val homeTeamId = arguments?.getInt("homeTeamId") ?: -1 // 🌟 타임라인 좌우 구분을 위해 추가

        binding.tvInfoStadium.text = "경기장: $stadium"
        binding.tvInfoRound.text = "라운드: $round"

        // 🌟 타임라인 리사이클러뷰 레이아웃 매니저 초기화
        binding.rvTimeline.layoutManager = LinearLayoutManager(context)

        if (fixtureId != -1L) {
            loadMatchInfoFromServer(fixtureId, homeTeamId)
        }
    }

    private fun loadMatchInfoFromServer(fixtureId: Long, homeTeamId: Int) {
        RetrofitClient.apiService.getMatchInfo(fixtureId).enqueue(object : Callback<MatchInfoData> {
            override fun onResponse(call: Call<MatchInfoData>, response: Response<MatchInfoData>) {
                if (response.isSuccessful) {
                    response.body()?.let { info ->
                        binding.tvInfoReferee.text = "주심: ${info.referee ?: "배정 전"}"
                        binding.tvInfoDate.text = "일시: ${info.matchFullDate ?: "일시 정보 없음"}"

                        val mom = info.momPlayer
                        if (mom != null) {
                            binding.layoutMOM.visibility = View.VISIBLE
                            binding.tvMomName.text = mom.name
                            binding.tvMomTeam.text = mom.teamName
                            binding.tvMomRating.text = String.format("%.1f", mom.rating)

                            Glide.with(this@InfoFragment)
                                .load(mom.photoUrl)
                                .placeholder(R.drawable.ic_launcher_background)
                                .error(R.drawable.ic_launcher_background)
                                .circleCrop()
                                .into(binding.ivMomPlayer)
                        } else {
                            binding.layoutMOM.visibility = View.GONE
                        }

                        // 🌟 누락되었던 타임라인 렌더링 핵심 로직!
                        if (info.events.isNotEmpty()) {
                            binding.layoutTimelineContainer.visibility = View.VISIBLE
                            val adapter = TimelineAdapter(info.events, homeTeamId)
                            binding.rvTimeline.adapter = adapter
                        } else {
                            binding.layoutTimelineContainer.visibility = View.GONE
                        }
                    }
                }
            }

            override fun onFailure(call: Call<MatchInfoData>, t: Throwable) {
                Toast.makeText(context, "상세 정보 로딩 실패: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(fixtureId: Long, homeTeamId: Int, awayTeamId: Int, stadium: String?, round: String?): InfoFragment {
            return InfoFragment().apply {
                arguments = Bundle().apply {
                    putLong("fixtureId", fixtureId)
                    putInt("homeTeamId", homeTeamId)
                    putInt("awayTeamId", awayTeamId)
                    putString("stadium", stadium)
                    putString("round", round)
                }
            }
        }
    }
}