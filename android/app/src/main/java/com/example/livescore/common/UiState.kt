package com.example.livescore.common

import android.view.View
import com.example.livescore.databinding.ViewErrorBinding

// 화면 데이터의 상태. 로딩·성공·오류를 모든 화면이 같은 방식으로 다룬다.
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

// 상태에 맞게 로딩 표시/오류 안내(+다시 시도 버튼)를 보이고, 성공이면 onSuccess로 내용을 그린다.
fun <T> renderState(state: UiState<T>, progress: View, errorView: ViewErrorBinding, onSuccess: (T) -> Unit) {
    progress.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
    errorView.root.visibility = if (state is UiState.Error) View.VISIBLE else View.GONE
    when (state) {
        is UiState.Error -> errorView.tvErrorMessage.text = state.message
        is UiState.Success -> onSuccess(state.data)
        UiState.Loading -> Unit
    }
}
