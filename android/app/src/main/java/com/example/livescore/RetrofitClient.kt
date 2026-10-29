package com.example.livescore

import com.example.livescore.auth.AuthApi
import com.example.livescore.auth.AuthInterceptor
import com.example.livescore.auth.AuthRepository
import com.example.livescore.auth.TokenAuthenticator
import com.example.livescore.league.LeagueApi
import com.example.livescore.member.MemberApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

object RetrofitClient {
    private const val BASE_URL = BuildConfig.BASE_URL

    private fun retrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            // String 응답을 처리하기 위해 ScalarsConverter를 Gson보다 먼저 추가해야 함
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    // 인증 주소(가입·로그인·토큰 갱신·로그아웃): 토큰 자동 첨부/갱신이 없는 클라이언트
    val authApi: AuthApi by lazy { retrofit(OkHttpClient()).create(AuthApi::class.java) }

    // 그 밖의 모든 주소: 로그인 상태면 토큰을 붙이고, 만료되면 자동으로 갱신한다
    private val authedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .authenticator(TokenAuthenticator(AuthRepository(authApi)))
            .build()
    }

    val apiService: ApiService by lazy { retrofit(authedClient).create(ApiService::class.java) }

    val memberApi: MemberApi by lazy { retrofit(authedClient).create(MemberApi::class.java) }

    val leagueApi: LeagueApi by lazy { retrofit(authedClient).create(LeagueApi::class.java) }
}
