package com.kenkeremath.mtgcounter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MTGLeaderboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
