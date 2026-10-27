package com.example.livescore

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory // 추가됨

object RetrofitClient {
    private const val BASE_URL = "http://192.168.0.2:8080"

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            // String 응답을 처리하기 위해 ScalarsConverter를 Gson보다 먼저 추가해야 함
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}