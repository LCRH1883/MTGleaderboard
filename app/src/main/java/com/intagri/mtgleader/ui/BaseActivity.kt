package com.intagri.mtgleader.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.intagri.mtgleader.persistence.Datastore
import com.intagri.mtgleader.ui.setup.theme.ScThemeUtils
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent

@AndroidEntryPoint
open class BaseActivity : AppCompatActivity() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DynamicThemeEntryPoint {
        fun provideDatastore(): Datastore
    }

    lateinit var datastore: Datastore

    override fun onCreate(savedInstanceState: Bundle?) {
        val datastoreInterface = EntryPoints.get(applicationContext, DynamicThemeEntryPoint::class.java)
        datastore = datastoreInterface.provideDatastore()
        val savedTheme = datastore.theme
        val theme = ScThemeUtils.resolveTheme(this, savedTheme)
        setTheme(theme.resId)
        super.onCreate(savedInstanceState)
    }
}