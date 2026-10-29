package com.example.livescore.common

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.example.livescore.databinding.ViewErrorBinding

// 화면 내용(content) 위에 로딩 표시와 오류 안내(+다시 시도)를 겹쳐 주는 틀.
// 레이아웃 파일을 건드리지 않고 같은 로딩·오류 모양을 쓰고 싶을 때 쓴다.
class StateHost(context: Context, val content: View) {
    val root = FrameLayout(context)
    private val progress = ProgressBar(context)
    val errorView: ViewErrorBinding = ViewErrorBinding.inflate(LayoutInflater.from(context), root, false)

    init {
        root.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(progress, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
        root.addView(errorView.root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
    }

    fun onRetry(action: () -> Unit) = errorView.btnRetry.setOnClickListener { action() }

    // 성공일 때만 내용을 보이고, 로딩/오류일 때는 내용을 감춘다
    fun <T> render(state: UiState<T>, onSuccess: (T) -> Unit) {
        content.visibility = if (state is UiState.Success) View.VISIBLE else View.GONE
        renderState(state, progress, errorView, onSuccess)
    }
}
