package com.intagri.mtgleader.persistence.friends

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface FriendsApi {
    @GET("v1/friends")
    suspend fun getFriends(): FriendsOverviewDto

    @POST("v1/friends/requests")
    suspend fun sendFriendRequest(@Body request: FriendRequestCreate)

    @POST("v1/friends/requests/{id}/accept")
    suspend fun acceptRequest(@Path("id") id: String)

    @POST("v1/friends/requests/{id}/decline")
    suspend fun declineRequest(@Path("id") id: String)
}
