package com.intagri.mtgleader.persistence.notifications

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Query

interface NotificationsApi {
    @POST("v1/notifications/token")
    suspend fun registerToken(
        @Body request: NotificationTokenRequest,
    ): NotificationTokenResponse

    @DELETE("v1/notifications/token")
    suspend fun deleteToken(
        @Query("token") token: String,
    )
}
