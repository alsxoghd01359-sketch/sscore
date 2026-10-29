package com.example.livescore.match

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.livescore.R
import com.example.livescore.common.StateHost
import com.example.livescore.databinding.FragmentInfoBinding

class InfoFragment : Fragment() {

    private var _binding: FragmentInfoBinding? = null
    private val binding get() = _binding!!
    private lateinit var host: StateHost

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInfoBinding.inflate(inflater, container, false)
        host = StateHost(requireContext(), binding.root)
        return host.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = matchDetailViewModel()
        binding.tvInfoStadium.text = "경기장: ${arguments?.getString("stadium") ?: "정보 없음"}"
        binding.tvInfoRound.text = "라운드: ${arguments?.getString("round") ?: "정보 없음"}"
        binding.rvTimeline.layoutManager = LinearLayoutManager(context)

        host.onRetry { vm.info.retry() }
        vm.info.state.observe(viewLifecycleOwner) { state ->
            host.render(state) { info ->
                binding.tvInfoReferee.text = "주심: ${info.referee ?: "배정 전"}"
                binding.tvInfoDate.text = "일시: ${info.matchFullDate ?: "일시 정보 없음"}"

                val mom = info.momPlayer
                if (mom != null) {
                    binding.layoutMOM.visibility = View.VISIBLE
                    binding.tvMomName.text = mom.name
                    binding.tvMomTeam.text = mom.teamName
                    binding.tvMomRating.text = String.format("%.1f", mom.rating)
                    Glide.with(this)
                        .load(mom.photoUrl)
                        .placeholder(R.drawable.bg_player_circle)
                        .error(R.drawable.bg_player_circle)
                        .circleCrop()
                        .into(binding.ivMomPlayer)
                } else {
                    binding.layoutMOM.visibility = View.GONE
                }

                // 타임라인: 득점·카드·교체를 시간순으로
                binding.layoutTimelineContainer.visibility = if (info.events.isNotEmpty()) View.VISIBLE else View.GONE
                binding.rvTimeline.adapter = TimelineAdapter(info.events, vm.homeTeamId)
            }
        }
        vm.info.ensureLoaded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(stadium: String?, round: String?) = InfoFragment().apply {
            arguments = Bundle().apply {
                putString("stadium", stadium)
                putString("round", round)
            }
        }
    }
}
