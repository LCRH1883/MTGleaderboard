package com.intagri.mtgleader.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.intagri.mtgleader.BuildConfig
import com.intagri.mtgleader.R
import com.intagri.mtgleader.ui.settings.friends.FriendsFragment
import com.intagri.mtgleader.ui.settings.notifications.NotificationsFragment
import com.intagri.mtgleader.ui.settings.counters.manage.ManageCountersFragment
import com.intagri.mtgleader.ui.settings.profiles.manage.ManageProfilesFragment
import com.intagri.mtgleader.ui.settings.user.UserSettingsFragment
import com.intagri.mtgleader.ui.history.MatchHistoryFragment

class SettingsFragment: Fragment() {

    companion object {
        fun newInstance(): SettingsFragment {
            val args = Bundle()
            val fragment = SettingsFragment()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "fragment_settings"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
        val userSettings: View = view.findViewById(R.id.user_settings)
        val friends: View = view.findViewById(R.id.friends)
        val notifications: View = view.findViewById(R.id.notifications)
        val matchHistory: View = view.findViewById(R.id.match_history)
        val manageProfiles: View = view.findViewById(R.id.manage_profiles)
        val manageCounters: View = view.findViewById(R.id.manage_counters)
        val about: View = view.findViewById(R.id.about)
        val versionText: TextView = view.findViewById(R.id.version_text)
        versionText.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        userSettings.setOnClickListener {
            val f = UserSettingsFragment.newInstance()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(UserSettingsFragment.TAG)
                .commit()
        }
        friends.setOnClickListener {
            val f = FriendsFragment.newInstance()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(FriendsFragment.TAG)
                .commit()
        }
        notifications.setOnClickListener {
            val f = NotificationsFragment.newInstance()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(NotificationsFragment.TAG)
                .commit()
        }
        matchHistory.setOnClickListener {
            val f = MatchHistoryFragment.newInstance()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(MatchHistoryFragment.TAG)
                .commit()
        }
        manageProfiles.setOnClickListener {
            val f = ManageProfilesFragment.newInstance()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(ManageProfilesFragment.TAG)
                .commit()
        }
        manageCounters.setOnClickListener {
            val f = ManageCountersFragment.newInstance()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(ManageCountersFragment.TAG)
                .commit()
        }
        about.setOnClickListener {
            val f = AboutFragment.newInstance()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(AboutFragment.TAG)
                .commit()
        }
        return view
    }


}
