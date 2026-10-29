package com.example.livescore.common

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 한 시즌 단위로 불러오는 탭 데이터(순위·통계 등). 같은 시즌은 한 번 받으면 다시 받지 않는다.
class SeasonLoader<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (season: Int) -> ApiResult<T>
) {
    private val _state = MutableLiveData<UiState<T>>(UiState.Loading)
    val state: LiveData<UiState<T>> = _state

    private val cache = mutableMapOf<Int, T>()
    private var requested: Int? = null
    private var job: Job? = null

    fun load(season: Int, force: Boolean = false) {
        requested = season
        val cached = cache[season]
        if (!force && cached != null) {
            _state.value = UiState.Success(cached)
            return
        }
        _state.value = UiState.Loading
        job?.cancel()
        job = scope.launch {
            val result = fetch(season)
            if (requested != season) return@launch   // 그 사이 다른 시즌을 골랐다면 버린다
            when (result) {
                is ApiResult.Success -> {
                    cache[season] = result.data
                    _state.value = UiState.Success(result.data)
                }
                is ApiResult.Failure -> _state.value = UiState.Error(result.message)
            }
        }
    }

    fun retry() {
        requested?.let { load(it, force = true) }
    }

    fun setLoading() { _state.value = UiState.Loading }
    fun setError(message: String) { _state.value = UiState.Error(message) }
}

// 시즌이 없는 데이터(스쿼드 등). 성공하면 다시 받지 않고, 실패하면 retry()로 다시 받는다.
class SimpleLoader<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend () -> ApiResult<T>
) {
    private val _state = MutableLiveData<UiState<T>>(UiState.Loading)
    val state: LiveData<UiState<T>> = _state

    private var job: Job? = null
    private var started = false

    // 화면이 보일 때 부르면 처음 한 번만 받는다
    fun ensureLoaded() {
        if (!started) load()
    }

    fun retry() = load()

    private fun load() {
        started = true
        _state.value = UiState.Loading
        job?.cancel()
        job = scope.launch {
            _state.value = when (val result = fetch()) {
                is ApiResult.Success -> UiState.Success(result.data)
                is ApiResult.Failure -> UiState.Error(result.message)
            }
        }
    }
}
