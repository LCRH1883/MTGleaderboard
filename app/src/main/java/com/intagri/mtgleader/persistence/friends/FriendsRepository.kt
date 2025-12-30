package com.intagri.mtgleader.persistence.friends

class FriendsRepository(
    private val friendsApi: FriendsApi
) {
    suspend fun getConnections(): List<FriendConnectionDto> {
        return try {
            friendsApi.getConnections()
        } catch (e: retrofit2.HttpException) {
            if (e.code() != 404) {
                throw e
            }
            val overview = friendsApi.getFriends()
            overviewToConnections(overview)
        }
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

    suspend fun cancelRequest(id: String) {
        friendsApi.cancelRequest(id)
    }

    private fun overviewToConnections(overview: FriendsOverviewDto): List<FriendConnectionDto> {
        val accepted = overview.friends.map {
            FriendConnectionDto(user = it, status = "accepted")
        }
        val incoming = overview.incomingRequests.map {
            FriendConnectionDto(
                user = it.user,
                status = "incoming",
                requestId = it.id,
                createdAt = it.createdAt
            )
        }
        val outgoing = overview.outgoingRequests.map {
            FriendConnectionDto(
                user = it.user,
                status = "outgoing",
                requestId = it.id,
                createdAt = it.createdAt
            )
        }
        return incoming + accepted + outgoing
    }
}
