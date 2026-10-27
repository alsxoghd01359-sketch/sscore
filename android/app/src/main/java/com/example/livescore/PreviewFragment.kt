package com.example.livescore

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.example.livescore.databinding.FragmentPreviewBinding

class PreviewFragment : Fragment() {
    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(stadium: String?, round: String?) = PreviewFragment().apply {
            arguments = Bundle().apply {
                putString("stadium", stadium)
                putString("round", round)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)

        // 🌟 데이터 받아서 화면에 바로 세팅
        val stadium = arguments?.getString("stadium") ?: "정보 없음"
        val round = arguments?.getString("round") ?: "정보 없음"

        binding.tvStadiumInfo.text = "경기장: $stadium"
        binding.tvRoundInfo.text = "라운드: $round"
        binding.tvStandingsPreview.text = "현재 순위 정보는 '순위' 탭에서 확인 가능합니다."

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}