package com.intagri.mtgleader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import com.intagri.mtgleader.persistence.Datastore
import com.intagri.mtgleader.persistence.notifications.NotificationsRepository
import com.intagri.mtgleader.persistence.sync.SyncScheduler
import com.intagri.mtgleader.ui.setup.theme.ScThemeUtils
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.content.ContextCompat

@AndroidEntryPoint
open class BaseActivity : AppCompatActivity() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DynamicThemeEntryPoint {
        fun provideDatastore(): Datastore
    }

    lateinit var datastore: Datastore
    @Inject lateinit var notificationsRepository: NotificationsRepository
    private val requestNotificationsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        datastore.hasRequestedNotificationPermission = true
        if (granted) {
            lifecycleScope.launch(Dispatchers.IO) {
                notificationsRepository.syncTokenIfNeeded()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val datastoreInterface = EntryPoints.get(applicationContext, DynamicThemeEntryPoint::class.java)
        datastore = datastoreInterface.provideDatastore()
        val savedTheme = datastore.theme
        val theme = ScThemeUtils.resolveTheme(this, savedTheme)
        setTheme(theme.resId)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        SyncScheduler.enqueueNow(this)
        lifecycleScope.launch(Dispatchers.IO) {
            notificationsRepository.syncTokenIfNeeded()
        }
        maybeRequestNotificationPermission()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (!datastore.friendRequestNotificationsEnabled) {
            return
        }
        if (datastore.hasRequestedNotificationPermission) {
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            datastore.hasRequestedNotificationPermission = true
            return
        }
        requestNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
