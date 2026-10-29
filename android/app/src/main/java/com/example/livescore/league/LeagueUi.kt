package com.example.livescore.league

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider

const val ARG_LEAGUE_ID = "leagueId"

// 리그 상세 화면(액티비티)이 만든 ViewModel을 탭 프래그먼트들이 함께 쓴다
fun Fragment.leagueViewModel(): LeagueDetailViewModel =
    ViewModelProvider(requireActivity(), LeagueDetailViewModel.Factory(requireArguments().getInt(ARG_LEAGUE_ID, 39)))[LeagueDetailViewModel::class.java]

fun seasonLabel(season: Int) = "$season/${(season + 1).toString().takeLast(2)}"

// 시즌 드롭다운을 ViewModel의 시즌 목록/선택과 연결한다. 한 탭에서 고른 시즌은 다른 탭에도 반영된다.
fun Spinner.bindSeasons(owner: LifecycleOwner, vm: LeagueDetailViewModel) {
    vm.seasons.observe(owner) { list ->
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, list.map { seasonLabel(it) })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val index = list.indexOf(vm.selectedSeason.value)
        if (index >= 0) setSelection(index)
    }
    vm.selectedSeason.observe(owner) { season ->
        val index = vm.seasons.value?.indexOf(season) ?: -1
        if (index >= 0 && selectedItemPosition != index) setSelection(index)
    }
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            vm.seasons.value?.getOrNull(position)?.let { vm.selectSeason(it) }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
}
