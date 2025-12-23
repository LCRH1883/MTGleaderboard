package com.intagri.mtgleader.ui.setup.theme

import android.content.Intent
import android.os.Bundle
import com.intagri.mtgleader.R
import com.intagri.mtgleader.databinding.ActivityThemeBinding
import com.intagri.mtgleader.ui.BaseActivity
import com.intagri.mtgleader.ui.MainActivity

class ThemeActivity : BaseActivity() {

    private lateinit var binding: ActivityThemeBinding

    private var themeChanged: Boolean = false

    companion object {
        private const val STATE_THEME_CHANGED = "state_theme_changed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        val initialTheme = datastore.theme
        themeChanged = savedInstanceState?.getBoolean(STATE_THEME_CHANGED, false) ?: false

        val resolvedTheme = ScThemeUtils.resolveTheme(this, datastore.theme)
        binding.themeToggle.setOnCheckedChangeListener(null)
        binding.themeToggle.isChecked = resolvedTheme == SpellCounterTheme.DARK
        binding.themeToggle.text = if (binding.themeToggle.isChecked) {
            getString(R.string.theme_dark)
        } else {
            getString(R.string.theme_karn)
        }
        binding.themeToggle.setOnCheckedChangeListener { _, isChecked ->
            val theme = if (isChecked) SpellCounterTheme.DARK else SpellCounterTheme.KARN
            binding.themeToggle.text = if (isChecked) {
                getString(R.string.theme_dark)
            } else {
                getString(R.string.theme_karn)
            }
            if (theme != resolvedTheme) {
                datastore.theme = theme
                themeChanged = theme != initialTheme
                recreate()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_THEME_CHANGED, themeChanged)
    }

    override fun onBackPressed() {
        if (themeChanged) {
            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        } else {
            super.onBackPressed()
        }
    }
}
