package com.gnoemes.shikimori.utils.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

class RetryInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var retryCount = 0

        while (true) {
            val response = chain.proceed(request)
            if (response.code != 429 || retryCount >= MAX_RETRIES) {
                return response
            }

            val delayMs = BASE_DELAY_MS * (1L shl retryCount)
            response.close()
            TimeUnit.MILLISECONDS.sleep(delayMs)
            retryCount++
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val BASE_DELAY_MS = 1000L
    }
}
