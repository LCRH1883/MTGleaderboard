package com.intagri.mtgleader.persistence.auth

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileCache @Inject constructor() {
    private var cachedUser: AuthUser? = null

    fun getUser(): AuthUser? = cachedUser

    fun setUser(user: AuthUser?) {
        cachedUser = user
    }
}
