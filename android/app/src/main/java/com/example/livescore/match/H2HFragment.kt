package com.example.livescore.match

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.livescore.MatchAdapter
import com.example.livescore.common.StateHost
import com.example.livescore.databinding.FragmentH2hBinding

class H2HFragment : Fragment() {
    private var _binding: FragmentH2hBinding? = null
    private val binding get() = _binding!!
    private lateinit var host: StateHost

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentH2hBinding.inflate(inflater, container, false)
        host = StateHost(requireContext(), binding.root)
        return host.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = matchDetailViewModel()
        host.onRetry { vm.h2h.retry() }
        vm.h2h.state.observe(viewLifecycleOwner) { state ->
            host.render(state) { data ->
                binding.tvHomeWins.text = data.homeWins.toString()
                binding.tvDraws.text = data.draws.toString()
                binding.tvAwayWins.text = data.awayWins.toString()

                // 비율 바 길이
                if (data.homeWins + data.draws + data.awayWins > 0) {
                    setWeight(binding.viewH2HHome, data.homeWins)
                    setWeight(binding.viewH2HDraw, data.draws)
                    setWeight(binding.viewH2HAway, data.awayWins)
                }

                binding.rvH2HMatches.layoutManager = LinearLayoutManager(context)
                binding.rvH2HMatches.adapter = MatchAdapter(data.lastMatches)
            }
        }
        vm.h2h.ensureLoaded()
    }

    private fun setWeight(bar: View, value: Int) {
        val params = bar.layoutParams as LinearLayout.LayoutParams
        params.weight = value.toFloat()
        bar.layoutParams = params
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = H2HFragment()
    }
}
