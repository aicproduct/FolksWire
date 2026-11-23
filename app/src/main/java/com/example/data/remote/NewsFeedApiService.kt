package com.example.data.remote

import com.example.data.remote.dto.AdFeedResponseDto
import com.example.data.remote.dto.AdSlotDto
import com.example.data.remote.dto.JsonFeedDto
import com.example.data.remote.dto.WordPressPostDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface NewsFeedApiService {

    @GET("feed/rss")
    suspend fun getWiredTopStories(): Response<ResponseBody>

    @GET("feed/category/{category}/latest/rss")
    suspend fun getWiredCategoryFeed(@Path("category") category: String): Response<ResponseBody>

    @GET
    suspend fun fetchRawFeed(@Url url: String): Response<ResponseBody>

    @GET
    suspend fun fetchWordPressPosts(@Url url: String): Response<List<WordPressPostDto>>

    @GET
    suspend fun fetchJsonFeed(@Url url: String): Response<JsonFeedDto>

    @GET
    suspend fun fetchAdFeedObject(@Url url: String): Response<AdFeedResponseDto>

    @GET
    suspend fun fetchAdFeedArray(@Url url: String): Response<List<AdSlotDto>>

    companion object {
        const val WIRED_BASE_URL = "https://www.wired.com/"
        const val HASHTAGFOLKS_BASE_URL = "https://hashtagfolks.com/"

        fun createMoshi(): Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        fun createOkHttpClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            return OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "FolksWire/1.0 (Android RSS News Reader)")
                        .header(
                            "Accept",
                            "application/rss+xml, application/atom+xml, application/xml, text/xml, application/json, application/feed+json, text/html"
                        )
                        .build()
                    chain.proceed(request)
                }
                .addInterceptor(logging)
                .build()
        }

        fun create(
            baseUrl: String = WIRED_BASE_URL,
            okHttpClient: OkHttpClient = createOkHttpClient(),
            moshi: Moshi = createMoshi()
        ): NewsFeedApiService {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(NewsFeedApiService::class.java)
        }
    }
}
