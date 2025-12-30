package com.intagri.mtgleader.persistence.friends

class FriendsRepository(
    private val friendsApi: FriendsApi
) {
    suspend fun getFriends(): FriendsOverviewDto {
        return friendsApi.getFriends()
    }

    suspend fun sendFriendRequest(username: String) {
        friendsApi.sendFriendRequest(FriendRequestCreate(username = username))
    }

    suspend fun acceptRequest(id: String) {
        friendsApi.acceptRequest(id)
    }

    suspend fun declineRequest(id: String) {
        friendsApi.declineRequest(id)
    }
}
