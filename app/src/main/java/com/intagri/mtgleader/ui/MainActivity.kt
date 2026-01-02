package com.intagri.mtgleader.ui

import android.content.Intent
import android.os.Bundle
import com.intagri.mtgleader.R
import com.intagri.mtgleader.ui.settings.friends.FriendsFragment
import com.intagri.mtgleader.ui.setup.SetupFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            showInitialFragment(intent?.getBooleanExtra(EXTRA_OPEN_FRIENDS, false) == true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_FRIENDS, false)) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, FriendsFragment.newInstance())
                .commit()
        }
    }

    private fun showInitialFragment(openFriends: Boolean) {
        val fragment = if (openFriends) {
            FriendsFragment.newInstance()
        } else {
            SetupFragment.newInstance()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    companion object {
        const val EXTRA_OPEN_FRIENDS = "extra_open_friends"
    }
}
