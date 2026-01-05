package com.intagri.mtgleader.persistence.friends

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface FriendsApi {
    @GET("v1/friends/connections")
    suspend fun getConnections(): List<FriendConnectionDto>

    @GET("v1/friends/connections")
    suspend fun getConnectionsWithEtag(
        @Header("If-None-Match") etag: String? = null,
    ): Response<List<FriendConnectionDto>>

    @GET("v1/friends")
    suspend fun getFriends(): FriendsOverviewDto

    @POST("v1/friends/requests")
    suspend fun sendFriendRequest(@Body request: FriendRequestCreate)

    @POST("v1/friends/requests/{id}/accept")
    suspend fun acceptRequest(
        @Path("id") id: String,
    )

    @POST("v1/friends/requests/{id}/decline")
    suspend fun declineRequest(
        @Path("id") id: String,
    )

    @POST("v1/friends/requests/{id}/cancel")
    suspend fun cancelRequest(
        @Path("id") id: String,
    )

    @POST("v1/friends/{id}/remove")
    suspend fun removeFriend(
        @Path("id") id: String,
    )

    @DELETE("v1/friends/{id}")
    suspend fun deleteFriend(
        @Path("id") id: String,
    )
}
