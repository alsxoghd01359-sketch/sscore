package com.example.livescore.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livescore.RetrofitClient
import com.example.livescore.SearchResultData
import com.example.livescore.common.ApiResult
import com.example.livescore.common.apiCall
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SearchApi {
    @GET("/api/soccer/search")
    suspend fun search(@Query("q") q: String): Response<List<SearchResultData>>
}

class SearchRepository(private val api: SearchApi) {
    suspend fun search(q: String): ApiResult<List<SearchResultData>> = apiCall { api.search(q) }
}

// 검색 화면의 상태: 입력 대기 / 검색 중 / 결과 / 결과 없음 / 오류
sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Results(val items: List<SearchResultData>) : SearchState()
    object Empty : SearchState()
    data class Error(val message: String) : SearchState()
}

class SearchViewModel(
    private val repository: SearchRepository = SearchRepository(RetrofitClient.searchApi)
) : ViewModel() {

    private val _state = MutableLiveData<SearchState>(SearchState.Idle)
    val state: LiveData<SearchState> = _state

    private var query = ""
    private var job: Job? = null

    // 입력이 멈춘 뒤 300ms가 지나면 검색한다. 입력이 바뀌면 이전 검색은 버린다.
    fun onQueryChanged(raw: String) {
        query = raw.trim()
        job?.cancel()
        if (query.length < MIN_LENGTH) {
            _state.value = SearchState.Idle
            return
        }
        launchSearch(query, debounce = true)
    }

    fun retry() {
        if (query.length >= MIN_LENGTH) launchSearch(query, debounce = false)
    }

    private fun launchSearch(q: String, debounce: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(DEBOUNCE_MS)
            _state.value = SearchState.Loading
            _state.value = when (val r = repository.search(q)) {
                is ApiResult.Success -> if (r.data.isEmpty()) SearchState.Empty else SearchState.Results(r.data)
                is ApiResult.Failure -> SearchState.Error(r.message)
            }
        }
    }

    private companion object {
        const val MIN_LENGTH = 2
        const val DEBOUNCE_MS = 300L
    }
}
