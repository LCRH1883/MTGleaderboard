package com.intagri.mtgleader.persistence.matches

import retrofit2.http.Body
import retrofit2.http.POST

interface MatchApi {
    @POST("v1/matches")
    suspend fun createMatch(@Body request: MatchCreateRequest): MatchCreateResponse
}
