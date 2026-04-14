package com.u3coding.shaver

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiProvider {

    private const val BASE_URL = "https://api.deepseek.com/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .addInterceptor { chain ->
            val apiKey = BuildConfig.DEEPSEEK_API_KEY.trim()
            if (apiKey.isBlank()) {
                throw IllegalStateException("DEEPSEEK_API_KEY is empty. Please set it in local.properties.")
            }
            if (apiKey.any { it.code < 0x21 || it.code > 0x7E }) {
                throw IllegalStateException("DEEPSEEK_API_KEY contains invalid non-ASCII characters.")
            }

            val newRequest = chain.request()
                .newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .build()
            chain.proceed(newRequest)
        }
        .build()

    val api: API by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(API::class.java)
    }
}
